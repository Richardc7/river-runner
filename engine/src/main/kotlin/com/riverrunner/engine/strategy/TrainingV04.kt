package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import java.util.Base64
import kotlin.math.abs

data class TrainingDrill(
    val id: String, val category: String, val options: List<ActionValue>,
    val usesSamplingBands: Boolean = false,
) {
    init {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        require(category.length in 1..80)
        require(options.size in 2..64 && options.map { it.move }.distinct().size == options.size)
        require(!usesSamplingBands || separatedBest(options) != null)
    }
    val best: ActionValue get() = if (usesSamplingBands) requireNotNull(separatedBest(options)) else options.maxBy { it.chips }
    fun grade(move: Move): DrillGrade {
        val chosen = options.singleOrNull { it.move == move } ?: error("Illegal drill answer")
        val winner = best
        return DrillGrade(id, category, winner.move == move, actionValueGap(winner, chosen, usesSamplingBands))
    }
}
data class DrillGrade(val id: String, val category: String, val exact: Boolean, val modelLossChips: Double)
data class CategoryProgress(val attempts: Int = 0, val exact: Int = 0, val lossChips: Double = 0.0) {
    init { require(attempts >= 0 && exact in 0..attempts && lossChips.isFinite() && lossChips >= 0) }
    fun add(g: DrillGrade) = CategoryProgress(attempts + 1, exact + if (g.exact) 1 else 0, lossChips + g.modelLossChips)
}
data class TrainingProgress(val categories: Map<String, CategoryProgress> = emptyMap(), val last: String? = null) {
    fun record(g: DrillGrade) = TrainingProgress(categories + (g.category to (categories[g.category] ?: CategoryProgress()).add(g)), g.id)
}
object TrainingCurriculum {
    const val VERSION = "v0.4"
    fun fromHistory(hands: Iterable<HandAnalysis>): List<TrainingDrill> =
        hands.flatMap { hand ->
            hand.decisions.mapNotNull { decision ->
                if (decision.gradableBestEv() == null) null else {
                    val category = when {
                        decision.evidence.isNotEmpty() -> "exploit-" + decision.evidence.first().code.name
                        decision.street == Street.FLOP ->
                            if (decision.notes.any { it.contains("Board texture: wet") }) "flop-cbet-wet" else "flop-cbet-dry"
                        decision.street == Street.TURN -> "turn-model"
                        else -> "river-model"
                    }
                    TrainingDrill(
                        "h" + hand.handId + "a" + decision.actionIndex,
                        category,
                        decision.actionValues.toList(),
                        usesSamplingBands = decision.isMultiwayEv,
                    )
                }
            }
        }.distinctBy { it.id }.sortedBy { it.id }
    fun next(drills: List<TrainingDrill>, progress: TrainingProgress): TrainingDrill? {
        val pool = drills.filter { it.id != progress.last }.ifEmpty { drills }
        return pool.minWithOrNull(compareBy<TrainingDrill> {
            val p = progress.categories[it.category] ?: CategoryProgress()
            if (p.attempts == 0) -1.0 else (p.exact + 1.0) / (p.attempts + 2.0)
        }.thenBy { it.id })
    }
}
object RangeMatrix {
    private const val RANKS = "AKQJT98765432"
    data class Cell(val handClass: String, val mass: Double)
    fun of(range: WeightedRange): List<List<Cell>> {
        val mass = range.classes()
        return RANKS.mapIndexed { r, a -> RANKS.mapIndexed { c, b ->
            val label = if (r == c) "" + a + b else if (r < c) "" + a + b + "s" else "" + b + a + "o"
            Cell(label, mass[label] ?: 0.0)
        } }
    }
}
data class ReferenceProvenance(val source: String, val author: String, val licence: String, val context: String) {
    init { require(listOf(source,author,licence,context).all { it.length in 1..200 && '\n' !in it && '\t' !in it }) }
}
data class ReferenceAction(val move: Move, val frequency: Double) {
    init { require(frequency.isFinite() && frequency in 0.0..1.0) }
}
data class ReferenceNode(val contextHash: String, val actions: List<ReferenceAction>) {
    init {
        require(contextHash.matches(Regex("[a-f0-9]{64}")) && actions.size in 1..64)
        require(actions.map { it.move }.distinct().size == actions.size)
        require(abs(actions.sumOf { it.frequency } - 1.0) < 1e-6)
    }
}
data class ReferenceBook(val provenance: ReferenceProvenance, val nodes: List<ReferenceNode>) {
    init { require(nodes.size in 1..3000 && nodes.map { it.contextHash }.distinct().size == nodes.size) }
    fun lookup(view: DecisionView): ReferenceNode? {
        val hash = DecisionFingerprint.of(view)
        return nodes.firstOrNull { it.contextHash == hash && it.actions.all { a -> PolicyTools.legal(a.move, view.legal) } }
    }
}
/** Bounded offline-only import. No solver claims and no approximate cross-context matching. */
object ReferenceCodec {
    const val HEADER = "RR-REFERENCE-1"
    const val MAX_TEXT_LENGTH = 1_000_000
    private fun quote(v: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(v.toByteArray(Charsets.UTF_8))
    private fun unquote(v: String) = String(Base64.getUrlDecoder().decode(v), Charsets.UTF_8)
    fun encode(book: ReferenceBook): String = buildString {
        append(HEADER).append('\n')
        val p = book.provenance
        append(listOf(p.source,p.author,p.licence,p.context).joinToString("\t") { quote(it) }).append('\n')
        for (node in book.nodes.sortedBy { it.contextHash }) {
            append("N\t").append(node.contextHash).append('\n')
            for (a in node.actions) append("A\t").append(a.move.type.name).append('\t')
                .append(a.move.raiseTo).append('\t').append(a.frequency).append('\n')
            append("E\n")
        }
    }
    fun decode(text: String): ReferenceBook {
        require(text.length <= MAX_TEXT_LENGTH)
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.size in 5..200_000 && lines[0] == HEADER)
        val p = lines[1].split('\t').map { unquote(it) }
        require(p.size == 4)
        val nodes = ArrayList<ReferenceNode>()
        val actions = ArrayList<ReferenceAction>()
        var hash: String? = null
        for (line in lines.drop(2)) {
            val parts = line.split('\t')
            when (parts[0]) {
                "N" -> { require(parts.size == 2 && hash == null); hash = parts[1] }
                "A" -> {
                    require(parts.size == 4 && hash != null)
                    val t = MoveType.valueOf(parts[1]); val value = parts[2].toInt()
                    val m = when(t) {
                        MoveType.RAISE -> { require(value > 0); Move.raiseTo(value) }
                        MoveType.FOLD -> { require(value == 0); Move.FOLD }
                        MoveType.CHECK -> { require(value == 0); Move.CHECK }
                        MoveType.CALL -> { require(value == 0); Move.CALL }
                    }
                    actions += ReferenceAction(m, parts[3].toDouble())
                }
                "E" -> {
                    require(parts.size == 1 && hash != null)
                    nodes += ReferenceNode(hash, actions.toList())
                    require(nodes.size <= 3000)
                    hash = null; actions.clear()
                }
                else -> error("Invalid reference line")
            }
        }
        require(hash == null && actions.isEmpty())
        return ReferenceBook(ReferenceProvenance(p[0],p[1],p[2],p[3]), nodes)
    }
}
