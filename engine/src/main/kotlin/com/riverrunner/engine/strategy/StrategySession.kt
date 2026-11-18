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
        val snapshot = opponents.snapshots()
        val river = if (view.street == Street.RIVER && view.mode == GameMode.CASH && view.opponents.size == 1) {
            val seat = view.opponents.single().seat
            RiverEv.analyze(view, RangeModel.infer(view, seat, snapshot[seat]), snapshot[seat],
                (advice.baseline.options + advice.policy.options).map { it.first })
        } else null
        val notes = buildList {
            if (advice.note.isNotBlank()) add(advice.note)
            if (river != null) {
                addAll(river.assumptions)
                if (river.supported) {
                    add("Top model range classes: " + river.rangeClasses.entries.take(12).joinToString(", ") { "${it.key}: ${"%.1f".format(java.util.Locale.US, it.value * 100)}%" })
                    add("River EV is coaching-only in this version; it does not override the bounded live policy.")
                }
            }
        }
        pending[view.actionIndex] = DecisionTrace(view.actionIndex, view.actorSeat, view.street,
            DecisionFingerprint.of(view), PolicySnapshot.from(advice.baseline), PolicySnapshot.from(advice.policy),
            advice.status, advice.evidence.toList(), snapshot.values.sortedBy { it.seat },
            actionValues = river?.values ?: emptyList(), notes = notes)
    }
    fun capturedBaseline(engine: HandEngine): PolicySnapshot? {
        if (engine.phase != Phase.BETTING || engine.toAct != GameSession.HERO_SEAT) return null
        val view = ObservationFactory.forActor(engine, mode)
        return pending[view.actionIndex]?.takeIf { it.contextHash == DecisionFingerprint.of(view) }?.baseline
    }
    fun finishAnalysis(handId: Long): HandAnalysis? = if (pending.isEmpty()) null else HandAnalysis(
        handId, "exploit-0.3.0", StrategyRandom.VERSION, seed, config.aiMode, pending.values.toList())

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
