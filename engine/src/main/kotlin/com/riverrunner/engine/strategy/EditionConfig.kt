package com.riverrunner.engine.strategy

import com.riverrunner.engine.*

enum class AiMode { LEGACY, ADAPTIVE }
enum class OpponentPreset(val legacyStyle: BotStyle) {
    BASELINE(BotStyle.GTO), TAG(BotStyle.TAG), LAG(BotStyle.LAG),
    STATION(BotStyle.STATION), NIT(BotStyle.NIT), MANIAC(BotStyle.MANIAC), LOOSE_PASSIVE(BotStyle.STATION)
}
data class EditionConfig(
    val aiMode: AiMode = AiMode.LEGACY,
    val opponents: Map<Int, OpponentPreset> = emptyMap(),
    val recordAdvice: Boolean = true,
) {
    init { require(opponents.keys.all { it in 1..8 }) { "Only bot seats 1..8 may be overridden" } }
}

/** Self-style adjustment, never used as knowledge about another player's range. */
object PresetPolicy {
    fun apply(view: DecisionView, base: Policy, preset: OpponentPreset?): Policy {
        if (preset != OpponentPreset.LOOSE_PASSIVE || view.mode != GameMode.CASH) return base
        val la = view.legal
        if (view.street != Street.PREFLOP) return PolicyTools.transfer(base, MoveType.RAISE,
            if (la.canCheck) MoveType.CHECK else MoveType.CALL, base.probability(MoveType.RAISE) * .35, la)
        val percentile = HandClasses.percentile(view.ownHole[0], view.ownHole[1])
        val raises = view.actions.count { it.street == Street.PREFLOP && it.type.isAggressive }
        if (raises > 0 || view.actor.stack < 15 * view.bigBlind) return base
        val raise = if (la.canRaise && percentile < .08) .7 else if (la.canRaise && percentile < .16) .08 else 0.0
        val call = if (la.canCall) when { percentile < .08 -> 1 - raise; percentile < .55 -> .8 * (1 - raise); percentile < .72 -> .35; else -> 0.0 } else 0.0
        val options = mutableListOf<Pair<Move, Double>>()
        if (raise > 0) options += BetSizing.target(la, 1.0) to raise
        if (call > 0) options += Move.CALL to call
        options += (if (la.canCheck) Move.CHECK else Move.FOLD) to (1 - raise - call)
        return PolicyTools.replace(base, options, la)
    }
}
