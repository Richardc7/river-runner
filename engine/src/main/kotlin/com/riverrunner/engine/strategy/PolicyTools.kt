package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.math.abs

object PolicyTools {
    fun legal(move: Move, la: LegalActions): Boolean = when (move.type) {
        MoveType.FOLD -> !la.canCheck
        MoveType.CHECK -> la.canCheck
        MoveType.CALL -> la.canCall
        MoveType.RAISE -> la.canRaise && move.raiseTo in la.minRaiseTo..la.maxRaiseTo
    }
    /** Never rely on HandEngine silently coercing an illegal AI action. */
    fun normalize(options: List<Pair<Move, Double>>, la: LegalActions): List<Pair<Move, Double>> {
        val merged = linkedMapOf<Move, Double>()
        for ((move, probability) in options) {
            require(probability.isFinite() && probability >= 0) { "Invalid probability" }
            require(legal(move, la)) { "Illegal strategy move: $move" }
            if (probability > 0) merged[move] = (merged[move] ?: 0.0) + probability
        }
        val sum = merged.values.sum()
        require(sum.isFinite() && sum > 0) { "Empty strategy" }
        return merged.map { it.key to it.value / sum }
    }
    fun validated(policy: Policy, la: LegalActions): Policy = Policy(
        normalize(policy.options, la), policy.equity, policy.potOdds, policy.icmRequired, la.pot,
    )
    fun replace(policy: Policy, options: List<Pair<Move, Double>>, la: LegalActions): Policy = Policy(
        normalize(options, la), policy.equity, policy.potOdds, policy.icmRequired, la.pot,
    )
    /** Moves at most [amount] probability mass between action types; sizes retain their mix. */
    fun transfer(policy: Policy, from: MoveType, to: MoveType, amount: Double, la: LegalActions): Policy {
        require(amount.isFinite() && amount in 0.0..1.0)
        val source = policy.options.filter { it.first.type == from }.sumOf { it.second }
        if (source <= 0 || amount <= 0 || from == to) return policy
        var targets = policy.options.filter { it.first.type == to }
        if (targets.isEmpty()) {
            val move = when (to) { MoveType.CHECK -> Move.CHECK; MoveType.FOLD -> Move.FOLD
                MoveType.CALL -> Move.CALL; MoveType.RAISE -> if (la.canRaise) BetSizing.target(la, .5) else return policy }
            if (!legal(move, la)) return policy
            targets = listOf(move to 1.0)
        }
        val delta = minOf(amount, source)
        val targetSum = targets.sumOf { it.second }
        val options = policy.options.filter { it.first.type != to }.map { (m, p) ->
            m to if (m.type == from) p * (1 - delta / source) else p
        } + targets.map { (m, p) -> m to ((policy.options.firstOrNull { it.first == m }?.second ?: 0.0) + delta * p / targetSum) }
        return replace(policy, options, la)
    }
    fun distance(a: Policy, b: Policy): Double {
        val ma = a.options.groupBy { it.first }.mapValues { e -> e.value.sumOf { it.second } }
        val mb = b.options.groupBy { it.first }.mapValues { e -> e.value.sumOf { it.second } }
        return (ma.keys + mb.keys).sumOf { abs((ma[it] ?: 0.0) - (mb[it] ?: 0.0)) } / 2
    }
}
