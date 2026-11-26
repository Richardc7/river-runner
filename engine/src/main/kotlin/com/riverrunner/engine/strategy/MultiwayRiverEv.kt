package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Cash-game 3-9-way RIVER closure, not a full multiway action tree.
 *
 * This estimates only check (when the round ends) or call/fold (when no other
 * opponent has an outstanding decision). Bet/raise and any future action are
 * deliberately excluded. Unlike the HU river special case, this model supports
 * multiple side pots, all-in opponents, and cash rake via the game's pot rules.
 */
object MultiwayRiverEv {
    const val VERSION = "multiway-river-closure-v2"

    fun isTerminal(view: DecisionView): Boolean {
        if (view.street != Street.RIVER || view.opponents.size !in 2..8) return false
        val opponentsWhoCanAct = view.opponents.filter { !it.allIn }
        return if (view.legal.canCheck) {
            // Checking is terminal only after every non-all-in opponent has checked.
            // If someone has yet to act, their future bet makes a terminal EV invalid.
            view.legal.currentBet == 0 && opponentsWhoCanAct.all { seat ->
                view.actions.any { it.street == Street.RIVER && it.seat == seat.seat && it.type == ActionType.CHECK }
            }
        } else {
            view.legal.canCall && opponentsWhoCanAct.all { it.streetBet == view.legal.currentBet }
        }
    }

    fun analyze(
        view: DecisionView,
        ranges: Map<Int, WeightedRange>,
        random: Random,
        iterations: Int = 1200,
    ): RiverAnalysis {
        fun unsupported(reason: String) = RiverAnalysis(false, emptyList(), null, emptyMap(), listOf(reason))
        require(iterations in 1..100000)
        if (view.mode != GameMode.CASH || view.street != Street.RIVER || view.opponents.size !in 2..8)
            return unsupported("Only cash multiway river nodes with 2-8 live opponents are supported")
        if (!isTerminal(view))
            return unsupported("Later river actions remain possible; a terminal check/call EV would be incorrect")
        if (ranges.keys != view.opponents.map { it.seat }.toSet())
            return unsupported("Missing or extra opponent range")
        if (view.pot.toLong() != view.seats.sumOf { it.committed.toLong() })
            return unsupported("Pot does not match public commitments")

        val cost = if (view.legal.canCheck) 0 else view.legal.toCall
        val committed = view.seats.associate {
            it.seat to it.committed.toLong() + if (it.seat == view.actorSeat) cost else 0
        }
        if (committed.size != view.seats.size || committed.values.any { it !in 0L..Int.MAX_VALUE.toLong() } ||
            committed.values.sum() > Int.MAX_VALUE
        ) return unsupported("Invalid public commitments")
        val live = view.seats.filterNot { it.folded }.map { it.seat }.toSet()
        val liveInOrder = view.order.filter { it in live }
        if (view.actorSeat !in live || liveInOrder.size != live.size || liveInOrder.toSet() != live)
            return unsupported("Inconsistent seat order or pot eligibility")
        val plan = PotSettlement.plan(committed.mapValues { it.value.toInt() }, liveInOrder)
        if (plan.pots.isEmpty()) return unsupported("No contested pot")
        val pots = RakeSettlement.apply(plan.pots, view.cashRules, view.bigBlind, true)
        val foldedTail = if (pots.none { it.rake > 0 }) plan.foldedTail else 0
        val heroRefund = plan.returned?.takeIf { it.first == view.actorSeat }?.second ?: 0
        val dead = Cards.mask((view.ownHole + view.board).toIntArray())
        val sampler = JointRangeSampler(ranges)
        val boardMask = Cards.mask(view.board.toIntArray())
        val heroScore = HandEvaluator.evaluate(boardMask or Cards.mask(view.ownHole.toIntArray()))
        val moments = ImportanceSampleMoments()
        repeat(iterations) {
            val draw = sampler.sample(dead, random) ?: return@repeat
            val weight = draw.importanceWeight
            if (weight <= 0.0 || !weight.isFinite()) return@repeat
            val scores = HashMap<Int, Int>(ranges.size + 1)
            scores[view.actorSeat] = heroScore
            for ((seat, cards) in draw.holes) {
                scores[seat] = HandEvaluator.evaluate(boardMask or cards)
            }
            var heroAward = heroRefund
            for ((index, pot) in pots.withIndex()) {
                val winners = PotSettlement.winners(pot, scores)
                val tail = if (index == pots.lastIndex) foldedTail else 0
                heroAward += PotSettlement.award(pot.amount, winners.size, winners.indexOf(view.actorSeat), tail)
            }
            moments.add(weight, (heroAward - cost).toDouble())
        }
        if (moments.count == 0)
            return unsupported("No legal joint hands were sampled")
        val effectiveSamples = moments.effectiveSamples
        if (effectiveSamples < max(25.0, iterations * 0.10))
            return unsupported("Joint range sampling produced insufficient effective samples")
        val mean = moments.mean
        val delta = 2.0 * moments.standardError
        val outcome = ActionValue(
            if (view.legal.canCheck) Move.CHECK else Move.CALL,
            mean, mean - delta, mean + delta,
        )
        val values = if (view.legal.canCheck) listOf(outcome)
            else listOf(ActionValue(Move.FOLD, 0.0, 0.0, 0.0), outcome)
        return RiverAnalysis(true, values, null, emptyMap(), listOf(
            "$VERSION: terminal cash river; ${view.opponents.size} live opponents, ${moments.count}/$iterations legal joint samples, effective sample size ${"%.0f".format(java.util.Locale.US, effectiveSamples)}.",
            "Opponent hands come from an importance-weighted product of inferred marginal ranges; this is not a calibrated joint strategy or solver GTO.",
            "Low/high show approximate +/-2 Monte Carlo standard errors ONLY. They exclude range-model and continuation-policy uncertainty and are not validated confidence intervals.",
            "Only closing check/call versus fold is evaluated. Bet/raise and nonterminal river decisions need a full multiway continuation model.",
            "Side-pot eligibility, unmatched refunds, tied winners and cash rake follow the game's settlement rules.",
        ))
    }

}

/** Self-normalized importance sampling; squared weights also weight the residuals. */
internal class ImportanceSampleMoments {
    var count = 0
        private set
    var mean = 0.0
        private set
    private var weightSum = 0.0
    private var squaredWeightSum = 0.0
    private var squaredWeightMean = 0.0
    private var squaredWeightM2 = 0.0

    fun add(weight: Double, outcome: Double) {
        count++
        weightSum += weight
        mean += (outcome - mean) * (weight / weightSum)
        val squared = weight * weight
        if (squared == 0.0) return
        squaredWeightSum += squared
        val delta = outcome - squaredWeightMean
        squaredWeightMean += delta * (squared / squaredWeightSum)
        squaredWeightM2 += squared * delta * (outcome - squaredWeightMean)
    }

    val effectiveSamples: Double
        get() = if (squaredWeightSum > 0) (weightSum / sqrt(squaredWeightSum)).let { it * it } else 0.0

    // Shift the squared-weight variance to the ordinary weighted mean (the ratio estimate).
    val standardError: Double
        get() {
            val shift = squaredWeightMean - mean
            return sqrt(max(0.0, squaredWeightM2 + squaredWeightSum * shift * shift)) / weightSum
        }
}
