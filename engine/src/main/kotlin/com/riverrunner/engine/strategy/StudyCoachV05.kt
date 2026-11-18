package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Only decision-time information, public actions and saved contemporaneous policies are used.
 * A model deviation is NOT a proven poker error or verified GTO regret.
 */
data class DecisionAssessment(
    val id: String, val topic: String, val position: String, val street: Street,
    val stackGroup: String, val sizeGroup: String, val oddsGroup: String,
    val actual: Move?, val preferred: MoveType, val actualFrequency: Double?,
    val preferredFrequency: Double, val modelGapBB: Double?,
    val needsStudy: Boolean,
    val ownHole: List<Int>, val board: List<Int>, val potBB: Double, val toCallBB: Double,
    val legalTypes: List<MoveType>
)
data class StudyCategory(val key: String, val hands: Int, val marked: Int, val measuredEv: Int, val meanModelGapBB: Double) {
    val reviewRate: Double get() = if (hands == 0) 0.0 else marked.toDouble() / hands
}

object StudyCoach {
    fun moveOf(action: Action?): Move? = when (action?.type) {
        ActionType.FOLD -> Move.FOLD
        ActionType.CHECK -> Move.CHECK
        ActionType.CALL -> Move.CALL
        ActionType.BET, ActionType.RAISE -> Move.raiseTo(action.streetTotal)
        else -> null
    }
    fun policyTypeProbability(snapshot: PolicySnapshot, type: MoveType): Double =
        snapshot.choices.filter { it.move.type == type }.sumOf { it.probability }

    fun assess(handId: Long, view: DecisionView, trace: DecisionTrace, actual: Action?): DecisionAssessment {
        require(handId > 0 && trace.actionIndex == view.actionIndex && trace.contextHash == DecisionFingerprint.of(view))
        val answer = moveOf(actual)
        val p = if (trace.status == AdviceStatus.ADJUSTED) trace.exploit else trace.baseline
        val byType = MoveType.entries.associateWith { policyTypeProbability(p, it) }
        val preferred = byType.maxBy { it.value }.key
        val actualFreq = answer?.let { byType.getValue(it.type) }
        val oppStack = view.opponents.minOfOrNull { it.stack + it.committed } ?: (view.actor.stack + view.actor.committed)
        val effective = min(view.actor.stack + view.actor.committed, oppStack).toDouble() / view.bigBlind
        val depth = when { effective < 20 -> "under20bb"; effective < 50 -> "20-49bb"; effective < 100 -> "50-99bb"; else -> "100bb+" }
        val size = view.actions.lastOrNull { it.street == view.street && it.type.isAggressive }?.let {
            val fraction = it.amount.toDouble() / max(1, view.pot - it.amount)
            when { fraction < .5 -> "small"; fraction < 1.0 -> "medium"; else -> "large" }
        } ?: "no-bet"
        val odds = if (!view.legal.canCall) "no-call" else {
            val pOdds = view.legal.toCall.toDouble() / max(1, view.pot + view.legal.toCall)
            when { pOdds < .25 -> "under25%"; pOdds < .4 -> "25-40%"; else -> "40%+" }
        }
        val pos = HandEngine.positionName(view.order.indexOf(view.actorSeat), view.order.size)
        val topic = when (view.street) {
            Street.PREFLOP -> if (view.actions.any { it.street == Street.PREFLOP && it.type.isAggressive })
                "Preflop facing raise" else "Preflop opening"
            else -> if (view.legal.canCall) view.street.name + " facing bet" else view.street.name + " initiative"
        }
        val exact = answer?.let { move -> trace.actionValues.firstOrNull { it.move == move } }
        val modelGap = if (exact != null && trace.actionValues.size >= 2)
            max(0.0, trace.actionValues.maxOf { it.chips } - exact.chips) / view.bigBlind else null
        val mismatch = actualFreq != null && actualFreq < .2 && byType.getValue(preferred) >= .45
        val marked = mismatch || modelGap != null && modelGap >= 1.0
        val legalTypes = buildList {
            if (view.legal.canCheck) add(MoveType.CHECK) else add(MoveType.FOLD)
            if (view.legal.canCall) add(MoveType.CALL)
            if (view.legal.canRaise) add(MoveType.RAISE)
        }
        return DecisionAssessment(
            "h" + handId + "a" + trace.actionIndex, topic, pos, view.street,
            depth, size, odds, answer, preferred, actualFreq, byType.getValue(preferred), modelGap, marked,
            view.ownHole.toList(), view.board.toList(),
            view.pot.toDouble() / view.bigBlind, view.legal.toCall.toDouble() / view.bigBlind, legalTypes
        )
    }
    fun categories(assessments: List<DecisionAssessment>): List<StudyCategory> {
        val groups = assessments.flatMap { a ->
            listOf("topic:" + a.topic, "position:" + a.position, "depth:" + a.stackGroup,
                "size:" + a.sizeGroup, "odds:" + a.oddsGroup).map { it to a }
        }.groupBy { it.first }
        return groups.map { (key, rows) ->
            val items = rows.map { it.second }
            val measured = items.mapNotNull { it.modelGapBB }
            StudyCategory(key, items.size, items.count { it.needsStudy }, measured.size,
                if (measured.isEmpty()) 0.0 else measured.average())
        }.sortedWith(compareByDescending<StudyCategory> { it.marked }.thenByDescending { it.hands }.thenBy { it.key })
    }
}

data class ReviewMemory(val attempts: Int = 0, val correct: Int = 0, val dueDay: Long = 0, val lastScore: Double = 0.0) {
    init { require(attempts >= 0 && correct in 0..attempts && dueDay >= 0 && lastScore.isFinite() && lastScore in 0.0..1.0) }
}
data class ReviewProgress(val entries: Map<String, ReviewMemory> = emptyMap(), val lastDrill: String? = null) {
    fun record(id: String, score: Double, day: Long): ReviewProgress {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,90}")) && score.isFinite() && score in 0.0..1.0 && day >= 0)
        val old = entries[id] ?: ReviewMemory()
        val good = score >= .75
        val consecutive = if (good) old.correct + 1 else 0
        val interval = if (!good) 1L else when { consecutive == 1 -> 1L; consecutive == 2 -> 3L; consecutive == 3 -> 7L; else -> 14L }
        return copy(entries = entries + (id to ReviewMemory(old.attempts + 1, consecutive, day + interval, score)), lastDrill = id)
    }
    fun next(candidates: List<DecisionAssessment>, day: Long, onlyWeak: Boolean): DecisionAssessment? {
        val all = candidates.filter { !onlyWeak || it.needsStudy }
        if (all.isEmpty()) return null
        val due = all.filter { entries[it.id]?.dueDay?.let { next -> next <= day } ?: true }
        val pool = due.ifEmpty { all }.filter { it.id != lastDrill }.ifEmpty { due.ifEmpty { all } }
        return pool.minWithOrNull(compareBy<DecisionAssessment> {
            entries[it.id]?.dueDay ?: 0L
        }.thenByDescending { it.needsStudy }.thenBy { it.id })
    }
}

data class PreflopCell(val name: String, val raiseFrequency: Double)
data class PreflopRangeCoach(
    val position: String, val effectiveBB: Int, val facingRaises: Int, val targetWidth: Double,
    val cells: List<List<PreflopCell>>, val ownClass: String, val explanation: String
)
object PreflopStudy {
    private const val RANKS = "AKQJT98765432"
    fun forDecision(view: DecisionView): PreflopRangeCoach? {
        if (view.street != Street.PREFLOP) return null
        val order = if (view.order.size == 2) listOf(view.buttonSeat, view.order.first())
            else view.order.drop(2) + view.order.take(2)
        val seatIndex = order.indexOf(view.actorSeat)
        if (seatIndex < 0) return null
        val behind = order.size - seatIndex - 1
        val raises = view.actions.count { it.street == Street.PREFLOP && it.type.isAggressive }
        val opponentStacks = view.opponents.map { it.stack + it.committed }
        val effectiveBB = (min(view.actor.stack + view.actor.committed,
            opponentStacks.minOrNull() ?: view.actor.stack + view.actor.committed) / view.bigBlind).coerceAtLeast(1)
        val baseline = Bot.rfiWidth(behind, view.order.size == 2)
        val width = when {
            raises == 0 && effectiveBB <= 15 -> Bot.pushWidth(effectiveBB.toDouble(), behind).coerceIn(.04, .7)
            raises == 0 -> baseline
            raises == 1 -> (baseline * .30).coerceIn(.035, .18)
            else -> (baseline * .12).coerceIn(.02, .08)
        }
        val grid = RANKS.mapIndexed { row, a -> RANKS.mapIndexed { col, b ->
            val label = when {
                row == col -> "" + a + b
                row < col -> "" + a + b + "s"
                else -> "" + b + a + "o"
            }
            val percentile = HandClasses.percentileOfClass(label)
            val probability = ((width + .04 - percentile) / .08).coerceIn(0.0, 1.0)
            PreflopCell(label, probability)
        } }
        val explanation = if (raises == 0 && effectiveBB <= 15)
            "Short-stack push-width heuristic, NOT a validated shove chart; check opponent ranges and ICM."
        else if (raises == 0) "RFI heuristic adjusted for players left to act; not solver GTO."
        else "Facing a raise: tighter response-width illustration, not a solved 3-bet/call strategy."
        return PreflopRangeCoach(
            HandEngine.positionName(view.order.indexOf(view.actorSeat), view.order.size),
            effectiveBB, raises, width, grid,
            HandClasses.className(view.ownHole[0], view.ownHole[1]), explanation
        )
    }
}

data class RiverWhatIfInput(val betFraction: Double, val foldRate: Double, val strengthShift: Double, val effectiveStackBB: Int) {
    init {
        require(betFraction in .25..2.0 && foldRate in .0.. .8 && strengthShift in -1.5..1.5 &&
            effectiveStackBB in 5..500 && betFraction.isFinite() && foldRate.isFinite() && strengthShift.isFinite())
    }
}
data class RiverWhatIfResult(
    val supported: Boolean, val chosenBet: Move?, val chosenEv: ActionValue?,
    val comparison: List<ActionValue>, val note: String
)
/** Re-evaluates *only* already-supported terminal HU cash river decisions.
 * The stack control changes remaining wager capacity at the same frozen river node,
 * NOT the pot formation or any prior street strategy.
 */
object RiverWhatIf {
    fun explore(view: DecisionView, range: WeightedRange, input: RiverWhatIfInput): RiverWhatIfResult {
        fun unavailable(reason: String) = RiverWhatIfResult(false, null, null, emptyList(), reason)
        if (view.street != Street.RIVER || view.mode != GameMode.CASH || view.opponents.size != 1)
            return unavailable("Conditional what-if is available only for heads-up cash river nodes")
        if (view.cashRules.hasRake) return unavailable("Raked river EV is unsupported")
        val villain = view.opponents.single()
        val actor = view.actor
        val target = input.effectiveStackBB.toLong() * view.bigBlind
        if (target <= max(actor.committed.toLong(), villain.committed.toLong()) + view.legal.toCall)
            return unavailable("Stack cap cannot cover already-committed and callable chips")
        val heroRemain = (target - actor.committed).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val villainRemain = (target - villain.committed).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
        val maxRaise = actor.streetBet + heroRemain
        val legal = view.legal.copy(maxRaiseTo = maxRaise,
            minRaiseTo = min(view.legal.minRaiseTo, maxRaise),
            canRaise = view.legal.canRaise && heroRemain > view.legal.toCall)
        val seats = view.seats.map { seat -> when(seat.seat) {
            actor.seat -> seat.copy(stack = heroRemain)
            villain.seat -> seat.copy(stack = villainRemain)
            else -> seat
        } }
        val shifted = WeightedRange.of(range.combos.map {
            val cls = Bot.handClass(it.hole, view.board)
            it.copy(weight = it.weight * exp(input.strengthShift * (cls - 2)))
        }, "what-if-strength-shift")
        val scenario = view.copy(legal = legal, seats = seats)
        val chosen = if (legal.canRaise) BetSizing.target(legal, input.betFraction) else null
        val result = RiverEv.analyze(scenario, shifted, extraMoves = listOfNotNull(chosen),
            declaredResponse = ResponseRates(input.foldRate, .08))
        if (!result.supported) return unavailable(result.assumptions.joinToString("; "))
        return RiverWhatIfResult(true, chosen, result.values.firstOrNull { it.move == chosen }, result.values,
            "Conditional range-strength and response sensitivity, NOT solver GTO, verified EV, or historical profit. " +
            "Stack cap changes the remaining wager budget at this fixed decision only.")
    }
}
