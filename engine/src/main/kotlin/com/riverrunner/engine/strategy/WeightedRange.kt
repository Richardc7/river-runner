package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

data class WeightedCombo(val first: Int, val second: Int, val weight: Double) {
    init { require(first in 0..51 && second in 0..51 && first < second && weight.isFinite() && weight > 0) }
    val mask: Long get() = (1L shl first) or (1L shl second)
    val hole: IntArray get() = intArrayOf(first, second)
}
class WeightedRange private constructor(val combos: List<WeightedCombo>, val source: String) : Equity.HandSampler {
    fun without(dead: Long): WeightedRange = of(combos.filter { it.mask and dead == 0L }, source)
    fun classes(): Map<String, Double> = combos.groupBy { HandClasses.className(it.first, it.second) }
        .mapValues { e -> e.value.sumOf { it.weight } }.toList().sortedByDescending { it.second }.toMap()
    override fun sample(dead: Long, random: Random): Long {
        val available = combos.filter { it.mask and dead == 0L }
        val total = available.sumOf { it.weight }
        if (total <= 0) return 0L
        var value = random.nextDouble() * total
        for (combo in available) { value -= combo.weight; if (value <= 0) return combo.mask }
        return available.last().mask
    }
    companion object {
        fun of(combos: List<WeightedCombo>, source: String = "declared-range"): WeightedRange {
            require(combos.isNotEmpty() && combos.size <= 1326 && source.length in 1..120)
            require(combos.map { it.mask }.distinct().size == combos.size)
            val sum = combos.sumOf { it.weight }; require(sum.isFinite() && sum > 0)
            return WeightedRange(combos.map { it.copy(weight = it.weight / sum) }, source)
        }
        fun uniform(dead: Long = 0L): WeightedRange = of(buildList {
            for (a in 0..50) for (b in a + 1..51) {
                val c = WeightedCombo(a, b, 1.0); if (c.mask and dead == 0L) add(c)
            }
        }, "uniform-unobserved")
        fun hand(cards: String, weight: Double = 1.0): WeightedCombo {
            val c = Cards.parseMany(cards); require(c.size == 2 && c[0] != c[1])
            return WeightedCombo(minOf(c[0], c[1]), maxOf(c[0], c[1]), weight)
        }
    }
}

/** Public-action likelihood approximation, NOT solver output or an unbiased learned bluff rate. */
object RangeModel {
    const val VERSION = "public-action-likelihood-v1"
    private data class Evidence(val action: Action, val priorRaises: Int, val fraction: Double)

    fun infer(view: DecisionView, seat: Int, snapshot: OpponentSnapshot?): WeightedRange {
        require(view.opponents.any { it.seat == seat })
        val model = snapshot ?: OpponentSnapshot(seat, 0, emptyMap())
        val vpip = model.stat(StatKind.VPIP).estimate(.30, 30.0).mean
        val pfr = model.stat(StatKind.PFR).estimate(.18, 30.0).mean
        val threeBet = model.stat(StatKind.THREE_BET).estimate(.08, 30.0).mean
        val straddle = view.actions.firstOrNull { it.type == ActionType.STRADDLE }?.seat
        val preOrder = if (straddle != null) {
            val first = (view.order.indexOf(straddle) + 1) % view.order.size
            List(view.order.size) { view.order[(first + it) % view.order.size] }
        } else if (view.order.size == 2) listOf(view.buttonSeat, view.order.first()) else view.order.drop(2) + view.order.take(2)
        val behind = (preOrder.size - preOrder.indexOf(seat) - 1).coerceAtLeast(0)
        val opening = (Bot.rfiWidth(behind, view.order.size == 2) * pfr / .18).coerceIn(.03, .9)
        val observations = mutableListOf<Evidence>()
        var pot = 0; var raises = 0; var street = Street.PREFLOP
        val bets = hashMapOf<Int, Int>()
        for (a in view.actions) {
            if (a.street != street) { street = a.street; bets.clear() }
            val price = bets.values.maxOrNull() ?: 0
            val call = (price - (bets[a.seat] ?: 0)).coerceAtLeast(0)
            val fraction = (a.streetTotal - price).coerceAtLeast(0).toDouble() / (pot.toLong() + call).coerceAtLeast(1)
            if (a.seat == seat && !a.type.isPost) observations += Evidence(a, raises, fraction)
            if (a.street == Street.PREFLOP && a.type.isAggressive) raises++
            if (a.type != ActionType.ANTE) bets[a.seat] = a.streetTotal
            pot += a.amount
        }
        val dead = Cards.mask((view.ownHole + view.board).toIntArray())
        val candidates = WeightedRange.uniform(dead).combos
        val logs = candidates.map { combo ->
            val pct = HandClasses.percentile(combo.first, combo.second)
            var logWeight = 0.0
            for (event in observations) {
                val a = event.action
                val likelihood = if (a.street == Street.PREFLOP) {
                    when {
                        a.type.isAggressive -> {
                            val width = when (event.priorRaises) { 0 -> opening; 1 -> threeBet.coerceIn(.025, .3); else -> (threeBet * .35).coerceIn(.015, .12) }
                            .02 + .96 / (1 + exp((pct - width) / .035))
                        }
                        a.type == ActionType.CALL -> {
                            val width = (if (event.priorRaises == 0) vpip else vpip * if (event.priorRaises == 1) .7 else .35).coerceIn(.04, .95)
                            (.02 + .93 / (1 + exp((pct - width) / .05))) * if (pct < .02) .4 else 1.0
                        }
                        a.type == ActionType.CHECK -> if (pct < .04) .5 else .95
                        else -> .5
                    }
                } else {
                    val board = view.board.take(a.street.boardCount)
                    val cls = Bot.handClass(combo.hole, board)
                    when (a.type) {
                        ActionType.BET, ActionType.RAISE -> {
                            val base = doubleArrayOf(.10, .35, .28, .55, .8)[cls]
                            val sizeAdjustment = if (event.fraction >= 1.0 && cls <= 2) .5 else 1.0
                            val raiseAdjustment = if (a.type == ActionType.RAISE && cls < 3) .5 else 1.0
                            base * sizeAdjustment * raiseAdjustment
                        }
                        ActionType.CALL -> doubleArrayOf(.04, .72, .65, .8, .6)[cls]
                        ActionType.CHECK -> doubleArrayOf(.9, .65, .75, .5, .3)[cls]
                        else -> .5
                    }
                }
                logWeight += ln(likelihood.coerceIn(.0001, .9999))
            }
            logWeight
        }
        val maxLog = logs.maxOrNull() ?: 0.0
        return WeightedRange.of(candidates.mapIndexed { i, c -> c.copy(weight = exp((logs[i] - maxLog).coerceAtLeast(-600.0))) }, VERSION)
    }
}
