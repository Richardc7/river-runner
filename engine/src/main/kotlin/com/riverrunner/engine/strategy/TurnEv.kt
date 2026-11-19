package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.random.Random

data class TurnAnalysis(
    val supported: Boolean,
    val values: List<ActionValue>,
    val rangeEquity: Double?,
    val rangeClasses: Map<String, Double>,
    val assumptions: List<String>,
)

/**
 * Conditional turn & flop C-bet EV approximations for heads-up unraked cash spots.
 * Coaching-only: does not override live play or claim equilibrium GTO.
 */
object TurnEv {
    const val VERSION = "hu-turn-model-v1"
    const val FLOP_CBET_VERSION = "hu-flop-cbet-v1"

    fun analyze(
        view: DecisionView,
        range: WeightedRange,
        opponent: OpponentSnapshot? = null,
        extraMoves: List<Move> = emptyList(),
        declaredResponse: ResponseRates? = null,
    ): TurnAnalysis {
        fun unsupported(reason: String) = TurnAnalysis(false, emptyList(), null, emptyMap(), listOf(reason))
        if (view.mode != GameMode.CASH || view.street != Street.TURN || view.opponents.size != 1)
            return unsupported("Only heads-up cash turn nodes are modelled")
        if (view.cashRules.hasRake)
            return unsupported("Raked action values require fee-aware continuation modelling; no EV is shown")
        val hero = view.actor
        val villain = view.opponents.single()
        val la = view.legal
        if (la.canCheck && !view.inPosition && !villain.allIn)
            return unsupported("Out-of-position check has a future decision; no terminal check EV")
        if (view.pot != view.seats.sumOf { it.committed } || hero.committed - hero.streetBet != villain.committed - villain.streetBet)
            return unsupported("Prior side-pot contributions are unsupported")
        val folded = view.seats.filter { it.folded }
        if (folded.any { it.committed > minOf(hero.committed, villain.committed) })
            return unsupported("Ineligible dead-money side pot is unsupported")

        val deadMoney = folded.sumOf { it.committed }.toDouble()
        val dead = Cards.mask((view.ownHole + view.board).toIntArray())
        val filtered = runCatching { range.without(dead) }.getOrElse { return unsupported("Range has no unblocked combination") }

        // Evaluate against top-weighted combos across all remaining river cards (100% deterministic).
        val topCombos = filtered.combos.sortedByDescending { it.weight }.take(140)
        val totalTopWeight = topCombos.sumOf { it.weight }.coerceAtLeast(1e-12)
        val normWeights = topCombos.map { it.weight / totalTopWeight }
        val baseBoardMask = Cards.mask(view.board.toIntArray())
        val heroHoleMask = Cards.mask(view.ownHole.toIntArray())

        val remainingRiver = IntArray(52 - view.board.size - view.ownHole.size).let { arr ->
            var idx = 0
            for (c in 0 until 52) {
                val bit = 1L shl c
                if (dead and bit == 0L) arr[idx++] = c
            }
            arr.copyOf(idx)
        }

        val wins = DoubleArray(topCombos.size)
        for (i in topCombos.indices) {
            val combo = topCombos[i]
            var sum = 0.0
            var count = 0
            for (rCard in remainingRiver) {
                val rBit = 1L shl rCard
                if (combo.mask and rBit != 0L) continue
                val boardWithRiver = baseBoardMask or rBit
                val heroScore = HandEvaluator.evaluate(boardWithRiver or heroHoleMask)
                val villainScore = HandEvaluator.evaluate(boardWithRiver or combo.mask)
                sum += if (heroScore > villainScore) 1.0 else if (heroScore == villainScore) 0.5 else 0.0
                count++
            }
            wins[i] = if (count > 0) sum / count else 0.5
        }

        val equity = normWeights.indices.sumOf { normWeights[it] * wins[it] }
        val classes = topCombos.map { Bot.handClass(it.hole, view.board) }

        val candidates = linkedSetOf<Move>()
        if (la.canCheck) candidates += Move.CHECK else { candidates += Move.FOLD; if (la.canCall) candidates += Move.CALL }
        if (la.canRaise) SizeClass.entries.forEach { candidates += BetSizing.target(la, it.center) }
        extraMoves.filter { PolicyTools.legal(it, la) }.forEach { candidates += it }
        require(candidates.size <= 64)

        fun rates(move: Move): ResponseRates {
            if (declaredResponse != null) return declaredResponse
            val fraction = BetSizing.fraction(la, move)
            val bucket = BetSizing.bucket(fraction)
            val folds = opponent?.facing(StatKind.FOLD_TO_BET, Street.TURN, bucket) ?: BayesianStat()
            val raises = opponent?.facing(StatKind.RAISE_BET, Street.TURN, bucket) ?: BayesianStat()
            val f = folds.estimate((.38 + .15 * ln(1 + fraction)).coerceIn(.2, .8))
            val r = raises.estimate((.10 / (1 + fraction)).coerceIn(.02, .16))
            return ResponseRates(
                f.mean,
                minOf(r.mean, 1 - f.mean),
                (2 * f.standardDeviation + .06).coerceAtMost(.3),
                (2 * r.standardDeviation + .04).coerceAtMost(.2),
            )
        }

        val values = candidates.map { move ->
            when (move.type) {
                MoveType.FOLD -> ActionValue(move, 0.0, 0.0, 0.0)
                MoveType.CHECK -> {
                    val ev = equity * view.pot
                    ActionValue(move, ev, ev * 0.9, (ev * 1.1).coerceAtMost(view.pot.toDouble()))
                }
                MoveType.CALL -> {
                    val newTotal = hero.committed.toLong() + la.toCall
                    val contestable = 2.0 * minOf(newTotal, villain.committed.toLong()) + deadMoney
                    val realization = if (view.inPosition) 1.0 else 0.92
                    val ev = equity * realization * contestable - la.toCall
                    val rawEv = equity * contestable - la.toCall
                    ActionValue(move, ev, minOf(ev, rawEv) - 0.05 * la.toCall, maxOf(ev, rawEv) + 0.05 * la.toCall)
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
                        val folds = calibrated(normWeights, classes.map { doubleArrayOf(2.0, 0.8, .5, -.6, -2.4)[it] }, foldTarget)
                        val remaining = normWeights.indices.map { normWeights[it] * (1 - folds[it]) }
                        val mass = remaining.sum()
                        val conditionalRaises = if (!canReraise || mass < 1e-12) List(normWeights.size) { 0.0 }
                        else calibrated(
                            remaining.map { it / mass },
                            classes.map { doubleArrayOf(-2.8, -1.8, -1.8, -.7, 2.0)[it] },
                            (raiseTarget / mass).coerceIn(0.0, 1.0),
                        )
                        return normWeights.indices.sumOf { i ->
                            val f = folds[i]
                            val r = (1 - f) * conditionalRaises[i]
                            val c = (1 - f - r).coerceAtLeast(0.0)
                            normWeights[i] * (f * view.pot + c * (wins[i] * potIfCalled - cost) - r * amount)
                        }
                    }

                    val point = evaluate(response.fold, response.raise)
                    val scenarios = mutableListOf(point)
                    for (f in listOf(response.fold - response.foldRadius, response.fold + response.foldRadius)) {
                        for (r in listOf(response.raise - response.raiseRadius, response.raise + response.raiseRadius)) {
                            val fold = f.coerceIn(0.0, 1.0)
                            scenarios += evaluate(fold, r.coerceIn(0.0, 1 - fold))
                        }
                    }
                    ActionValue(move, point, scenarios.min(), scenarios.max())
                }
            }
        }

        return TurnAnalysis(
            supported = true,
            values = values,
            rangeEquity = equity,
            rangeClasses = filtered.classes(),
            assumptions = listOf(
                "$VERSION: turn runout enumeration conditional on ${filtered.source}; ${topCombos.size} weighted combos.",
                "Turn EV is coaching-only in this version; it approximates single-street continuation without full river trees.",
            ),
        )
    }

    /**
     * Evaluates heads-up unraked cash flop C-bet spots when hero was the preflop aggressor.
     * Distinguishes dry vs. wet flop textures for targeted C-bet drills.
     */
    fun analyzeFlopCbet(
        view: DecisionView,
        range: WeightedRange,
        opponent: OpponentSnapshot? = null,
        extraMoves: List<Move> = emptyList(),
    ): TurnAnalysis {
        fun unsupported(reason: String) = TurnAnalysis(false, emptyList(), null, emptyMap(), listOf(reason))
        if (view.mode != GameMode.CASH || view.street != Street.FLOP || view.opponents.size != 1)
            return unsupported("Only heads-up cash flop C-bet nodes are modelled")
        if (view.cashRules.hasRake)
            return unsupported("Raked action values require fee-aware continuation modelling; no EV is shown")
        val la = view.legal
        if (!la.canCheck || !la.canRaise)
            return unsupported("Flop C-bet model applies when hero can check or bet")
        val preAggressor = view.actions.lastOrNull { it.street == Street.PREFLOP && it.type.isAggressive }?.seat
        if (preAggressor != view.actorSeat)
            return unsupported("Flop C-bet model applies to the preflop aggressor")

        val dead = Cards.mask((view.ownHole + view.board).toIntArray())
        val filtered = runCatching { range.without(dead) }.getOrElse { return unsupported("Range has no unblocked combination") }
        val equity = Equity.vsRanges(view.ownHole.toIntArray(), view.board, listOf(filtered), 260, Random(DecisionFingerprint.of(view).hashCode()))
        val wet = Bot.wetness(view.board)
        val textureLabel = if (wet < 0.35) "dry" else "wet"

        val candidates = linkedSetOf(Move.CHECK)
        listOf(SizeClass.SMALL, SizeClass.MEDIUM, SizeClass.LARGE).forEach { candidates += BetSizing.target(la, it.center) }
        extraMoves.filter { PolicyTools.legal(it, la) }.forEach { candidates += it }

        val baseFoldBias = if (wet < 0.35) 0.48 else 0.36
        val obsFold = opponent?.facing(StatKind.FOLD_TO_CBET, Street.FLOP, null) ?: BayesianStat()

        val values = candidates.map { move ->
            if (move.type == MoveType.CHECK) {
                val realization = if (view.inPosition) 1.0 else 0.9
                val ev = equity * realization * view.pot
                ActionValue(move, ev, ev * 0.9, (ev * 1.1).coerceAtMost(view.pot.toDouble()))
            } else {
                val fraction = BetSizing.fraction(la, move)
                val priorFold = (baseFoldBias + 0.14 * ln(1 + fraction)).coerceIn(0.2, 0.72)
                val est = obsFold.estimate(priorFold)
                val foldRate = est.mean
                val radius = (2 * est.standardDeviation + 0.06).coerceAtMost(0.25)
                val cost = (move.raiseTo - view.actor.streetBet).toDouble()
                fun evFor(f: Double): Double = f * view.pot + (1 - f) * (equity * (view.pot + 2 * cost) - cost)
                val point = evFor(foldRate)
                val lo = evFor((foldRate + radius).coerceIn(0.0, 1.0))
                val hi = evFor((foldRate - radius).coerceIn(0.0, 1.0))
                ActionValue(move, point, minOf(point, lo, hi), maxOf(point, lo, hi))
            }
        }

        return TurnAnalysis(
            supported = true,
            values = values,
            rangeEquity = equity,
            rangeClasses = filtered.classes(),
            assumptions = listOf(
                "$FLOP_CBET_VERSION: Board texture: $textureLabel (wetness=${"%.2f".format(java.util.Locale.US, wet)}).",
                "Flop C-bet EV is coaching-only in this version; it estimates fold equity and showdown equity.",
            ),
        )
    }

    private fun calibrated(weights: List<Double>, scores: List<Double>, target: Double): List<Double> {
        if (target <= 0) return List(weights.size) { 0.0 }
        if (target >= 1) return List(weights.size) { 1.0 }
        fun logistic(x: Double) = 1.0 / (1.0 + exp(-x.coerceIn(-40.0, 40.0)))
        var low = -40.0
        var high = 40.0
        repeat(60) {
            val mid = (low + high) / 2
            val mean = weights.indices.sumOf { weights[it] * logistic(scores[it] + mid) }
            if (mean < target) low = mid else high = mid
        }
        val offset = (low + high) / 2
        return scores.map { logistic(it + offset) }
    }
}
