package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.random.Random

/** One table lifetime. The model is not reused by display name, seat number, or saved profile. */
class StrategySession(private val seed: Long, private val mode: GameMode, val config: EditionConfig) {
    val opponents = OpponentModel()
    var handNumber: Int = -1; private set

    fun beginHand(engine: HandEngine, number: Int) {
        handNumber = number
        opponents.beginHand(number, engine.order)
    }
    fun opportunity(engine: HandEngine): ActionOpportunity = ActionOpportunity.from(ObservationFactory.forActor(engine, mode))
    fun observe(before: ActionOpportunity, actual: Action) = opponents.observe(before, actual)

    fun advise(engine: HandEngine, style: BotStyle = BotStyle.GTO, tournament: TournamentContext? = null): DecisionAdvice {
        check(handNumber >= 0)
        val view = ObservationFactory.forActor(engine, mode)
        val rng = StrategyRandom.forDecision(seed, handNumber, view.actionIndex, view.actorSeat, 1)
        val raw = Bot.decide(engine, view.actorSeat, style, rng, tournament)
        val base = PolicyTools.validated(PresetPolicy.apply(view, raw, config.opponents[view.actorSeat]), view.legal)
        return ExploitPolicy.adjust(view, base, opponents.snapshots())
    }
    fun choose(engine: HandEngine, style: BotStyle, legacyRandom: Random, tournament: TournamentContext? = null): Move {
        if (config.aiMode == AiMode.LEGACY && config.opponents[engine.toAct] != OpponentPreset.LOOSE_PASSIVE) {
            // Keep the exact legacy RNG consumption and probability ordering.
            return Bot.decide(engine, engine.toAct, style, legacyRandom, tournament).sample(legacyRandom)
        }
        val advice = advise(engine, style, tournament)
        val policy = if (config.aiMode == AiMode.ADAPTIVE) advice.policy else advice.baseline
        return policy.sample(StrategyRandom.forDecision(seed, handNumber,
            engine.actions.count { !it.type.isPost }, engine.toAct, 2))
    }
}
