package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.math.exp
import kotlin.math.ln

data class ResponseRates(val fold: Double, val raise: Double, val foldRadius: Double = 0.0, val raiseRadius: Double = 0.0) {
    init { require(listOf(fold, raise, foldRadius, raiseRadius).all { it.isFinite() && it in 0.0..1.0 } && fold + raise <= 1 + 1e-9) }
}
data class RiverAnalysis(val supported: Boolean, val values: List<ActionValue>, val rangeEquity: Double?, val rangeClasses: Map<String, Double>, val assumptions: List<String>)

/** Exact enumeration CONDITIONAL on a model range. No equilibrium or realized-profit claim. */
object RiverEv {
    const val VERSION = "hu-river-model-v1"
    fun analyze(view: DecisionView, range: WeightedRange, opponent: OpponentSnapshot? = null,
        extraMoves: List<Move> = emptyList(), declaredResponse: ResponseRates? = null): RiverAnalysis {
        fun unsupported(reason: String) = RiverAnalysis(false, emptyList(), null, emptyMap(), listOf(reason))
        if (view.mode != GameMode.CASH || view.street != Street.RIVER || view.opponents.size != 1)
            return unsupported("Only heads-up cash river nodes are modelled")
        if (view.cashRules.hasRake) return unsupported("Raked action values require fee-aware continuation modelling; no EV is shown")
        val hero = view.actor; val villain = view.opponents.single(); val la = view.legal
        if (la.canCheck && !view.inPosition && !villain.allIn) return unsupported("Out-of-position check has a future decision; no terminal check EV")
        if (view.pot != view.seats.sumOf { it.committed } || hero.committed - hero.streetBet != villain.committed - villain.streetBet)
            return unsupported("Prior side-pot contributions are unsupported")
        val folded = view.seats.filter { it.folded }
        if (folded.any { it.committed > minOf(hero.committed, villain.committed) })
            return unsupported("Ineligible dead-money side pot is unsupported")
        val deadMoney = folded.sumOf { it.committed }.toDouble()
        val dead = Cards.mask((view.ownHole + view.board).toIntArray())
        val filtered = runCatching { range.without(dead) }.getOrElse { return unsupported("Range has no unblocked combination") }
        val combos = filtered.combos
        val boardMask = Cards.mask(view.board.toIntArray())
        val heroScore = HandEvaluator.evaluate(boardMask or Cards.mask(view.ownHole.toIntArray()))
        val wins = combos.map { c ->
            val score = HandEvaluator.evaluate(boardMask or c.mask)
            if (heroScore > score) 1.0 else if (heroScore == score) .5 else 0.0
        }
        val weights = combos.map { it.weight }
        val equity = weights.indices.sumOf { weights[it] * wins[it] }
        val classes = combos.map { Bot.handClass(it.hole, view.board) }
        val candidates = linkedSetOf<Move>()
        if (la.canCheck) candidates += Move.CHECK else { candidates += Move.FOLD; if (la.canCall) candidates += Move.CALL }
        if (la.canRaise) SizeClass.entries.forEach { candidates += BetSizing.target(la, it.center) }
        extraMoves.filter { PolicyTools.legal(it, la) }.forEach { candidates += it }
        require(candidates.size <= 64)
        fun rates(move: Move): ResponseRates {
            if (declaredResponse != null) return declaredResponse
            val fraction = BetSizing.fraction(la, move)
            val bucket = BetSizing.bucket(fraction)
            val folds = opponent?.facing(StatKind.FOLD_TO_BET, Street.RIVER, bucket) ?: BayesianStat()
            val raises = opponent?.facing(StatKind.RAISE_BET, Street.RIVER, bucket) ?: BayesianStat()
            val f = folds.estimate((.35 + .16 * ln(1 + fraction)).coerceIn(.2, .8))
            val r = raises.estimate((.09 / (1 + fraction)).coerceIn(.02, .15))
            return ResponseRates(f.mean, minOf(r.mean, 1 - f.mean),
                (2 * f.standardDeviation + .05).coerceAtMost(.3), (2 * r.standardDeviation + .03).coerceAtMost(.2))
        }
        val values = candidates.map { move ->
            when (move.type) {
                MoveType.FOLD -> ActionValue(move, 0.0, 0.0, 0.0)
                MoveType.CHECK -> (equity * view.pot).let { ActionValue(move, it, it, it) }
                MoveType.CALL -> {
                    val newTotal = hero.committed.toLong() + la.toCall
                    val contestable = 2.0 * minOf(newTotal, villain.committed.toLong()) + deadMoney
                    val ev = equity * contestable - la.toCall
                    ActionValue(move, ev, ev, ev)
                }
                MoveType.RAISE -> {
                    val amount = move.raiseTo - hero.streetBet
                    val opponentCall = minOf(villain.stack, (move.raiseTo - villain.streetBet).coerceAtLeast(0))
                    val matched = minOf(hero.committed.toLong() + amount, villain.committed.toLong() + opponentCall)
                    val cost = (matched - hero.committed).toDouble()
                    if (cost < 0) return unsupported("Unmatched prior contribution is unsupported")
                    val potIfCalled = 2.0 * matched + deadMoney
                    val canReraise = villain.stack > opponentCall && hero.stack > amount && !villain.allIn
                    val response = rates(move)
                    fun evaluate(foldTarget: Double, raiseTarget: Double): Double {
                        // Response depends on villain's own hand and public context, NEVER hero's real cards.
                        val folds = calibrated(weights, classes.map { doubleArrayOf(2.0, 1.0, .6, -.5, -2.4)[it] }, foldTarget)
                        val remaining = weights.indices.map { weights[it] * (1 - folds[it]) }
                        val mass = remaining.sum()
                        val conditionalRaises = if (!canReraise || mass < 1e-12) List(weights.size) { 0.0 }
                        else calibrated(remaining.map { it / mass }, classes.map { doubleArrayOf(-3.0, -2.5, -2.0, -.8, 2.0)[it] }, (raiseTarget / mass).coerceIn(0.0, 1.0))
                        return weights.indices.sumOf { i ->
                            val f = folds[i]; val r = (1 - f) * conditionalRaises[i]; val c = (1 - f - r).coerceAtLeast(0.0)
                            weights[i] * (f * view.pot + c * (wins[i] * potIfCalled - cost) - r * amount)
                        }
                    }
                    val point = evaluate(response.fold, response.raise)
                    val scenarios = mutableListOf(point)
                    for (f in listOf(response.fold - response.foldRadius, response.fold + response.foldRadius))
                        for (r in listOf(response.raise - response.raiseRadius, response.raise + response.raiseRadius)) {
                            val fold = f.coerceIn(0.0, 1.0)
                            scenarios += evaluate(fold, r.coerceIn(0.0, 1 - fold))
                        }
                    ActionValue(move, point, scenarios.min(), scenarios.max())
                }
            }
        }
        return RiverAnalysis(true, values, equity, filtered.classes(), listOf(
            "$VERSION: exact enumeration conditional on ${filtered.source}; ${combos.size} unblocked combinations.",
            "The range is a modelling assumption, not knowledge of the opponent's cards or verified GTO.",
            "Bet/raise EV assumes folding to a re-raise; only terminal checks and calls are evaluated.",
            "Bounds vary response parameters only; they exclude range-model error and are not statistical confidence intervals."
        ))
    }
    private fun calibrated(weights: List<Double>, scores: List<Double>, target: Double): List<Double> {
        if (target <= 0) return List(weights.size) { 0.0 }
        if (target >= 1) return List(weights.size) { 1.0 }
        fun logistic(x: Double) = 1.0 / (1.0 + exp(-x.coerceIn(-40.0, 40.0)))
        var low = -40.0; var high = 40.0
        repeat(60) {
            val mid = (low + high) / 2
            val mean = weights.indices.sumOf { weights[it] * logistic(scores[it] + mid) }
            if (mean < target) low = mid else high = mid
        }
        val offset = (low + high) / 2
        return scores.map { logistic(it + offset) }
    }
}
