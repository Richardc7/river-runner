package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.random.Random

/** One table lifetime. The model is not reused by display name, seat number, or saved profile. */
class StrategySession(private val seed: Long, private val mode: GameMode, val config: EditionConfig) {
    val opponents = OpponentModel()
    var handNumber: Int = -1; private set
    private val pending = linkedMapOf<Int, DecisionTrace>()

    fun beginHand(engine: HandEngine, number: Int) {
        handNumber = number
        pending.clear()
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
    /** Capture before the hero acts; later observations never rewrite this trace. */
    fun capture(engine: HandEngine, tournament: TournamentContext? = null) {
        if (!config.recordAdvice) return
        val view = ObservationFactory.forActor(engine, mode)
        if (view.actionIndex in pending) return
        val advice = advise(engine, BotStyle.GTO, tournament)
        pending[view.actionIndex] = DecisionTrace(view.actionIndex, view.actorSeat, view.street,
            DecisionFingerprint.of(view), PolicySnapshot.from(advice.baseline), PolicySnapshot.from(advice.policy),
            advice.status, advice.evidence.toList(), opponents.snapshots().values.sortedBy { it.seat },
            notes = listOf(advice.note).filter { it.isNotBlank() })
    }
    fun finishAnalysis(handId: Long): HandAnalysis? = if (pending.isEmpty()) null else HandAnalysis(
        handId, ExploitPolicy.VERSION, StrategyRandom.VERSION, seed, config.aiMode, pending.values.toList())

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
