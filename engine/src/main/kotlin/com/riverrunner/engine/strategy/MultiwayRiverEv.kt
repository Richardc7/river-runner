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
    const val VERSION = "multiway-river-closure-v1"

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
        val settlement = buildSettlement(view, cost)
            ?: return unsupported("Inconsistent contribution or side-pot eligibility")
        val dead = Cards.mask((view.ownHole + view.board).toIntArray())
        val sampler = JointRangeSampler(ranges)
        val boardMask = Cards.mask(view.board.toIntArray())
        val heroScore = HandEvaluator.evaluate(boardMask or Cards.mask(view.ownHole.toIntArray()))
        val rankInOrder = view.order.withIndex().associate { it.value to it.index }

        var accepted = 0
        var weightSum = 0.0
        var weightSquaredSum = 0.0
        var weightedNet = 0.0
        var weightedNetSquared = 0.0
        repeat(iterations) {
            val draw = sampler.sample(dead, random) ?: return@repeat
            val weight = draw.importanceWeight
            if (weight <= 0.0 || !weight.isFinite()) return@repeat
            val scores = HashMap<Int, Int>(ranges.size + 1)
            scores[view.actorSeat] = heroScore
            for ((seat, cards) in draw.holes) {
                scores[seat] = HandEvaluator.evaluate(boardMask or cards)
            }
            var heroAward = settlement.heroRefund.toDouble()
            for (pot in settlement.pots) {
                val best = pot.eligible.maxOf { scores.getValue(it) }
                val winners = pot.eligible.filter { scores.getValue(it) == best }
                    .sortedBy { rankInOrder.getValue(it) }
                val heroIndex = winners.indexOf(view.actorSeat)
                if (heroIndex >= 0) {
                    heroAward += pot.amount / winners.size
                    if (heroIndex < pot.amount % winners.size) heroAward += 1.0
                }
            }
            val net = heroAward - cost
            accepted++
            weightSum += weight
            weightSquaredSum += weight * weight
            weightedNet += weight * net
            weightedNetSquared += weight * net * net
        }
        if (weightSum <= 0 || weightSquaredSum <= 0)
            return unsupported("No legal joint hands were sampled")
        val effectiveSamples = weightSum * weightSum / weightSquaredSum
        if (effectiveSamples < max(25.0, iterations * 0.10))
            return unsupported("Joint range sampling produced insufficient effective samples")
        val mean = weightedNet / weightSum
        val variance = max(0.0, weightedNetSquared / weightSum - mean * mean)
        val samplingError = sqrt(variance / effectiveSamples)
        val delta = 2.0 * samplingError
        val outcome = ActionValue(
            if (view.legal.canCheck) Move.CHECK else Move.CALL,
            mean, mean - delta, mean + delta,
        )
        val values = if (view.legal.canCheck) listOf(outcome)
            else listOf(ActionValue(Move.FOLD, 0.0, 0.0, 0.0), outcome)
        return RiverAnalysis(true, values, null, emptyMap(), listOf(
            "$VERSION: terminal cash river; ${view.opponents.size} live opponents, $accepted/$iterations legal joint samples, effective sample size ${"%.0f".format(java.util.Locale.US, effectiveSamples)}.",
            "Opponent hands come from an importance-weighted product of inferred marginal ranges; this is not a calibrated joint strategy or solver GTO.",
            "Low/high show approximate +/-2 Monte Carlo standard errors ONLY. They exclude range-model and continuation-policy uncertainty and are not validated confidence intervals.",
            "Only closing check/call versus fold is evaluated. Bet/raise and nonterminal river decisions need a full multiway continuation model.",
            "Side-pot eligibility, unmatched refunds, tied winners and cash rake follow the game's settlement rules.",
        ))
    }

    private data class Settlement(val pots: List<Pot>, val heroRefund: Int)

    /** Reproduces the wager-refund and tiered-pot arithmetic from HandEngine.finishHand. */
    private fun buildSettlement(view: DecisionView, additional: Int): Settlement? {
        val committed = view.seats.associate { seat ->
            seat.seat to seat.committed.toLong() + if (seat.seat == view.actorSeat) additional else 0
        }.toMutableMap()
        val highest = committed.entries.sortedByDescending { it.value }
        var heroRefund = 0L
        if (highest.size >= 2 && highest[0].value > highest[1].value) {
            val excess = highest[0].value - highest[1].value
            committed[highest[0].key] = highest[1].value
            if (highest[0].key == view.actorSeat) heroRefund = excess
        }
        val tiers = committed.values.filter { it > 0L }.distinct().sorted()
        val pots = ArrayList<Pot>()
        var previous = 0L
        for (level in tiers) {
            val contributors = committed.count { it.value >= level }
            val chips = (level - previous) * contributors
            if (chips <= 0L || chips > Int.MAX_VALUE) return null
            val eligible = view.seats.filter { !it.folded && committed.getValue(it.seat) >= level }
                .map { it.seat }
            if (eligible.isEmpty()) return null
            pots += Pot(chips.toInt(), eligible, emptyList())
            previous = level
        }
        if (pots.isEmpty() || heroRefund > Int.MAX_VALUE) return null
        return Settlement(RakeSettlement.apply(pots, view.cashRules, view.bigBlind, true), heroRefund.toInt())
    }
}
