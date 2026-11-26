package com.riverrunner.engine.strategy

import com.riverrunner.engine.Move

private const val OLD_MULTIWAY_MODEL = "multiway-river-closure-v1"

private val oldUnsupportedNotes = listOf(
    "Multiway river continuation is not yet evaluated:",
    "Only cash multiway river nodes with 2-8 live opponents are supported",
    "Later river actions remain possible; a terminal check/call EV would be incorrect",
    "Missing or extra opponent range",
    "Pot does not match public commitments",
    "Inconsistent contribution or side-pot eligibility",
    "No legal joint hands were sampled",
    "Joint range sampling produced insufficient effective samples",
)

internal val DecisionTrace.isOldMultiwayEv: Boolean
    get() = notes.any { it.startsWith(OLD_MULTIWAY_MODEL + ":") }

val DecisionTrace.isMultiwayEv: Boolean
    get() = isOldMultiwayEv || notes.any { note ->
        note.startsWith(MultiwayRiverEv.VERSION + ":") || oldUnsupportedNotes.any { note.startsWith(it) }
    }

internal fun separatedBest(options: List<ActionValue>): ActionValue? {
    if (options.size < 2) return null
    return options.singleOrNull { candidate ->
        options.filter { it.move != candidate.move }.all { candidate.low > it.high }
    }
}

/** Legacy multiway bands remain viewable but are not used to grade saved decisions. */
fun DecisionTrace.gradableBestEv(): ActionValue? = when {
    actionValues.size < 2 || isOldMultiwayEv -> null
    isMultiwayEv -> separatedBest(actionValues)
    else -> actionValues.maxBy { it.chips }
}

internal fun actionValueGap(best: ActionValue, chosen: ActionValue, useSamplingBands: Boolean): Double =
    if (best.move == chosen.move) 0.0
    else if (useSamplingBands) (best.low - chosen.high).coerceAtLeast(0.0)
    else (best.chips - chosen.chips).coerceAtLeast(0.0)

fun DecisionTrace.gradableEvGapChips(move: Move): Double? {
    val chosen = actionValues.firstOrNull { it.move == move } ?: return null
    val best = gradableBestEv() ?: return null
    return actionValueGap(best, chosen, isMultiwayEv)
}
