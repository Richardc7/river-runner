package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.math.sqrt

enum class StatKind { VPIP, PFR, THREE_BET, FOLD_TO_THREE_BET, FOLD_TO_BET, CALL_BET, RAISE_BET, BET_WHEN_FREE, FOLD_TO_CBET }
data class StatKey(val kind: StatKind, val street: Street? = null, val size: SizeClass? = null, val multiway: Boolean = false)
data class Estimate(val mean: Double, val evidenceWeight: Double, val standardDeviation: Double, val opportunities: Int)

data class BayesianStat(val successes: Int = 0, val opportunities: Int = 0) {
    init { require(opportunities >= 0 && successes in 0..opportunities) }
    fun observe(success: Boolean) = BayesianStat(successes + if (success) 1 else 0, opportunities + 1)
    fun estimate(priorMean: Double = .5, priorStrength: Double = 20.0): Estimate {
        require(priorMean > 0 && priorMean < 1 && priorStrength.isFinite() && priorStrength > 0)
        val a = priorMean * priorStrength + successes
        val b = (1 - priorMean) * priorStrength + opportunities - successes
        return Estimate(a / (a + b), opportunities / (opportunities + priorStrength),
            sqrt(a * b / ((a + b) * (a + b) * (a + b + 1))), opportunities)
    }
}

data class OpponentSnapshot(val seat: Int, val handsObserved: Int, val stats: Map<StatKey, BayesianStat>) {
    fun stat(kind: StatKind, street: Street? = null, size: SizeClass? = null, multiway: Boolean = false): BayesianStat =
        stats[StatKey(kind, street, size, multiway)] ?: BayesianStat()
    /** Sparse size buckets fall back to the SAME street and player-count class, not unrelated data. */
    fun facing(kind: StatKind, street: Street, size: SizeClass?, multiway: Boolean = false): BayesianStat {
        val detailed = stat(kind, street, size, multiway)
        return if (size != null && detailed.opportunities < 12) stat(kind, street, null, multiway) else detailed
    }
}

/** A before-action event deliberately has no cards at all. */
data class ActionOpportunity(
    val actorSeat: Int, val street: Street, val actionIndex: Int,
    val legal: LegalActions, val seats: List<PublicSeat>, val actions: List<Action>,
) {
    val multiway: Boolean get() = seats.count { !it.folded } > 2
    companion object {
        fun from(view: DecisionView) = ActionOpportunity(view.actorSeat, view.street, view.actionIndex,
            view.legal.copy(), view.seats.toList(), view.actions.toList())
    }
    fun facingSize(): SizeClass? {
        if (legal.toCall <= 0) return null
        val i = actions.indexOfLast { it.street == street && it.type.isAggressive }
        if (i < 0) return null // blinds are not a voluntary bet
        val bet = actions[i]
        val before = actions.take(i).filter { it.street == street && it.type != ActionType.ANTE }
        val price = before.maxOfOrNull { it.streetTotal } ?: 0
        val paid = before.lastOrNull { it.seat == bet.seat }?.streetTotal ?: 0
        val potBefore = legal.pot - actions.drop(i).sumOf { it.amount }
        val denominator = (potBefore.toLong() + (price - paid).coerceAtLeast(0)).coerceAtLeast(1)
        return BetSizing.bucket((bet.streetTotal - price).coerceAtLeast(0).toDouble() / denominator)
    }
}

/** Session-local, public-only observations shared identically by all observers. Never keyed by a display name. */
class OpponentModel {
    private data class MutableProfile(var hands: Int = 0, val stats: MutableMap<StatKey, BayesianStat> = linkedMapOf())
    private val profiles = linkedMapOf<Int, MutableProfile>()
    private var hand = -1
    private var lastAction = -1
    private var lastEvent: Pair<ActionOpportunity, Action>? = null
    private val counted = hashSetOf<Pair<Int, StatKind>>()
    private val succeeded = hashSetOf<Pair<Int, StatKind>>()

    fun beginHand(handNumber: Int, seats: List<Int>) {
        require(handNumber >= 0 && seats.distinct().size == seats.size)
        if (handNumber == hand) return
        require(handNumber > hand) { "Out-of-order hand" }
        hand = handNumber; lastAction = -1; lastEvent = null; counted.clear(); succeeded.clear()
        for (seat in seats) profiles.getOrPut(seat) { MutableProfile() }.hands++
    }
    fun snapshot(seat: Int): OpponentSnapshot {
        val p = profiles[seat]
        return OpponentSnapshot(seat, p?.hands ?: 0, p?.stats?.toMap() ?: emptyMap())
    }
    fun snapshots(): Map<Int, OpponentSnapshot> = profiles.keys.associateWith { snapshot(it) }

    fun observe(before: ActionOpportunity, actual: Action) {
        check(hand >= 0) { "beginHand must precede observations" }
        require(actual.seat == before.actorSeat && actual.street == before.street && !actual.type.isPost)
        if (before.actionIndex == lastAction) {
            require(lastEvent == (before to actual)) { "Conflicting duplicate action" }
            return
        }
        require(before.actionIndex == lastAction + 1) { "Skipped or stale action" }
        lastAction = before.actionIndex
        lastEvent = before to actual
        val seat = actual.seat
        val profile = profiles.getValue(seat)
        fun add(kind: StatKind, success: Boolean, street: Street? = null, size: SizeClass? = null) {
            val key = StatKey(kind, street, size, street != null && before.multiway)
            profile.stats[key] = (profile.stats[key] ?: BayesianStat()).observe(success)
        }
        fun once(kind: StatKind, success: Boolean) {
            val id = seat to kind; val key = StatKey(kind)
            if (counted.add(id)) add(kind, success)
            else if (success && id !in succeeded) {
                val old = profile.stats.getValue(key)
                profile.stats[key] = BayesianStat(old.successes + 1, old.opportunities)
            }
            if (success) succeeded += id
        }
        if (before.street == Street.PREFLOP) {
            once(StatKind.VPIP, actual.type == ActionType.CALL || actual.type.isAggressive)
            once(StatKind.PFR, actual.type.isAggressive)
            val prior = before.actions.filter { it.street == Street.PREFLOP && !it.type.isPost }
            val raises = prior.filter { it.type.isAggressive }
            if (raises.size == 1 && raises[0].seat != seat) once(StatKind.THREE_BET, actual.type.isAggressive)
            if (raises.size == 2 && raises.last().seat != seat && prior.any { it.seat == seat && (it.type == ActionType.CALL || it.type.isAggressive) })
                once(StatKind.FOLD_TO_THREE_BET, actual.type == ActionType.FOLD)
            return
        }
        if (before.legal.canCheck) {
            add(StatKind.BET_WHEN_FREE, actual.type.isAggressive, before.street)
        } else {
            val size = before.facingSize()
            val outcomes = listOf(StatKind.FOLD_TO_BET to (actual.type == ActionType.FOLD),
                StatKind.CALL_BET to (actual.type == ActionType.CALL), StatKind.RAISE_BET to actual.type.isAggressive)
            for ((kind, success) in outcomes) {
                add(kind, success, before.street)
                if (size != null) add(kind, success, before.street, size)
            }
            val streetBets = before.actions.filter { it.street == before.street && it.type.isAggressive }
            val previous = Street.entries[before.street.ordinal - 1]
            val priorAggressor = before.actions.lastOrNull { it.street == previous && it.type.isAggressive }?.seat
            if (streetBets.size == 1 && streetBets.first().seat == priorAggressor) {
                add(StatKind.FOLD_TO_CBET, actual.type == ActionType.FOLD, before.street)
                if (size != null) add(StatKind.FOLD_TO_CBET, actual.type == ActionType.FOLD, before.street, size)
            }
        }
    }
}
