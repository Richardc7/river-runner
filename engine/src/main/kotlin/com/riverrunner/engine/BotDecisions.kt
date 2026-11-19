package com.riverrunner.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Preflop and postflop policy synthesis used by [Bot.decide].
 * Separated from [BotHeuristics] so street-specific sizing and frequencies
 * can evolve without cluttering range or board evaluation helpers.
 */
internal object BotDecisions {

    /**
     * Size mix for bets, indexed by [SizeClass] (SMALL..OVER). Value hands lean
     * larger on wet boards; bluffs lean small. The overlap keeps bet size from
     * telling the player what the hand is.
     */
    private val VALUE_SIZES_WET = doubleArrayOf(0.1, 0.2, 0.3, 0.25, 0.15)
    private val VALUE_SIZES_MID = doubleArrayOf(0.15, 0.3, 0.3, 0.2, 0.05)
    private val VALUE_SIZES_DRY = doubleArrayOf(0.25, 0.35, 0.25, 0.12, 0.03)
    private val BLUFF_SIZES = doubleArrayOf(0.4, 0.35, 0.15, 0.1, 0.0)

    private fun betSizeWeights(value: Boolean, wet: Double, street: Street, nOpp: Int): DoubleArray {
        val w = when {
            !value -> BLUFF_SIZES.copyOf()
            wet >= 0.5 -> VALUE_SIZES_WET.copyOf()
            wet >= 0.25 -> VALUE_SIZES_MID.copyOf()
            else -> VALUE_SIZES_DRY.copyOf()
        }
        // no overbets on the flop/turn, and none into several players
        if (street != Street.RIVER || nOpp >= 2) {
            w[3] += w[4]; w[4] = 0.0
        }
        if (nOpp >= 2) {
            w[2] += w[3]; w[3] = 0.0
        }
        return w
    }

    /** Raise multipliers (of the current bet) and their mix, facing a bet. */
    private fun raiseSizes(value: Boolean, street: Street): Pair<DoubleArray, DoubleArray> {
        val mults = if (street == Street.RIVER) doubleArrayOf(2.2, 2.5, 3.0) else doubleArrayOf(2.5, 3.0, 3.5)
        val w = if (value) doubleArrayOf(0.3, 0.4, 0.3) else doubleArrayOf(0.5, 0.35, 0.15)
        return mults to w
    }

    fun preflop(
        engine: HandEngine,
        seat: Int,
        style: BotStyle,
        la: LegalActions,
        random: Random,
        t: TournamentContext?,
    ): Policy {
        val me = engine.player(seat)
        val bb = engine.bigBlind
        val pct = HandClasses.percentile(me.hole[0], me.hole[1])
        val hu = engine.order.size == 2
        val behind = engine.playersBehindPreflop(seat)
        val acts = engine.actions.filter { it.street == Street.PREFLOP && !it.type.isPost }
        val raises = acts.count { it.type.isAggressive }
        val callers = acts.count { it.type == ActionType.CALL }
        val othersMax = engine.players.filter { it.seat != seat && !it.folded }.maxOfOrNull { it.stack + it.streetBet } ?: 0
        val effBB = min(me.stack + me.streetBet, othersMax).toDouble() / bb
        val opts = ArrayList<Pair<Move, Double>>()
        val ranges = BotHeuristics.preflopRanges(engine)
        val iWasRaiser = acts.any { it.seat == seat && it.type.isAggressive }

        // ---- big calls (facing a shove or a raise that commits a large part of the stack)
        if (la.toCall > 0 && (la.toCall >= me.stack * 0.4 || engine.players.any { it.allIn && !it.folded && it.seat != seat && it.streetBet >= la.currentBet })) {
            val opps = engine.players.filter { !it.folded && it.seat != seat }
            val samplers = opps.map { BotHeuristics.samplerFor(engine, it.seat, ranges.getValue(it.seat)) }
            val eq = Equity.vsRanges(me.hole, emptyList(), samplers, 500, random)
            val (req, icm) = BotHeuristics.requiredEquity(engine, seat, la, t)
            val margin = eq + style.callBias * 0.5 - req
            val pCall = BotHeuristics.above(margin, 0.0, 0.02)
            val canJam = la.canRaise && raises <= 3 && pct < 0.03 * style.aggression
            if (canJam) opts += Move.raiseTo(la.maxRaiseTo) to pCall
            else opts += Move.CALL to pCall
            opts += Move.FOLD to (1 - pCall)
            return Policy(BotHeuristics.normalized(opts), eq, la.toCall.toDouble() / (la.pot + la.toCall), icm)
        }

        if (raises == 0) {
            // ---- unopened (possibly limped) pot
            val limpers = callers
            if (la.canCheck) {
                // big blind option after limps / folds
                val thr = 0.12 * style.looseness * style.aggression
                val pR = BotHeuristics.smooth(pct, thr, 0.02)
                val size = bb * (3 + limpers)
                if (la.canRaise) opts += BotHeuristics.raiseMove(la, size) to pR
                opts += Move.CHECK to (1 - pR)
                return Policy(BotHeuristics.normalized(opts), null, 0.0, null)
            }
            if (effBB <= 13 && t != null || effBB <= 10) {
                val w = BotHeuristics.pushWidth(effBB, behind) * (if (hu) 1.07 else 1.0) * style.looseness
                val pJam = BotHeuristics.smooth(pct, w, 0.012)
                opts += Move.raiseTo(la.maxRaiseTo) to pJam
                opts += Move.FOLD to (1 - pJam)
                return Policy(BotHeuristics.normalized(opts), null, 0.0, null)
            }
            var thr = BotHeuristics.rfiWidth(behind, hu) * style.looseness
            if (limpers > 0) thr *= 0.65
            val pRaise = BotHeuristics.smooth(pct, thr, 0.02)
            val openBB = when {
                hu -> 2.5
                seat == engine.smallBlindSeat -> 3.0
                effBB < 30 -> 2.2
                else -> 2.5
            } + limpers
            // open sizes vary a little so the opening raise is not a fixed tell
            for ((m, w) in listOf(0.9 to 0.3, 1.0 to 0.4, 1.2 to 0.3)) {
                opts += BotHeuristics.raiseMove(la, (openBB * m * bb).toInt()) to pRaise * w
            }
            // passive styles sometimes limp
            if (style == BotStyle.STATION || style == BotStyle.MANIAC) {
                val pLimp = BotHeuristics.smooth(pct, thr * 1.5, 0.03) * (1 - pRaise) * 0.6
                opts += Move.CALL to pLimp
                opts += Move.FOLD to (1 - pRaise - pLimp)
            } else {
                opts += Move.FOLD to (1 - pRaise)
            }
            return Policy(BotHeuristics.normalized(opts), null, 0.0, null)
        }

        val potOdds = la.toCall.toDouble() / (la.pot + la.toCall)
        val lastRaise = acts.last { it.type.isAggressive }
        val raiserWidth = ranges.getValue(lastRaise.seat).hi
        val isBB = seat == engine.bigBlindSeat
        val isSB = seat == engine.smallBlindSeat && !hu
        // in position = acts after the raiser postflop
        val ip = engine.order.indexOf(seat) > engine.order.indexOf(lastRaise.seat)
        val callersAfterRaise = acts.dropWhile { it !== lastRaise }.count { it.type == ActionType.CALL }

        when (raises) {
            1 -> {
                var threeBet = max(0.022, raiserWidth * 0.30) * style.aggression
                var callTop = when {
                    isBB -> min(0.75, raiserWidth * 1.9 + 0.08 - (potOdds - 0.25))
                    isSB -> raiserWidth * 0.45
                    hu -> min(0.75, raiserWidth * 0.8)
                    else -> raiserWidth * 0.85
                } * style.preflopCall
                if (isSB) threeBet *= 1.35
                if (callersAfterRaise > 0) { callTop *= 0.8; threeBet *= 1.1 }
                if (!ip && !isBB) callTop *= 0.85
                callTop = callTop.coerceIn(threeBet, 0.85)
                val pValue = BotHeuristics.smooth(pct, threeBet, 0.008)
                var pBluff = 0.0
                if (pct > callTop && pct < callTop + 0.09 && (BotHeuristics.isSuited(me.hole))) pBluff = 0.35 * style.bluff
                if (BotHeuristics.isSuitedWheelAce(me.hole) && pct > threeBet) pBluff = max(pBluff, 0.4 * style.bluff)
                val p3 = (pValue + (1 - pValue) * pBluff).coerceAtMost(1.0)
                val pCall = (BotHeuristics.smooth(pct, callTop, 0.02) * (1 - p3)).coerceAtLeast(0.0)
                val mult = (if (ip) 3.0 else 3.8) + callersAfterRaise
                if (la.canRaise) opts += BotHeuristics.raiseMove(la, (lastRaise.streetTotal * mult).toInt()) to p3
                else opts += Move.CALL to p3
                opts += Move.CALL to pCall
                opts += Move.FOLD to (1 - p3 - pCall).coerceAtLeast(0.0)
            }
            2 -> {
                val fourBet: Double
                val callTop: Double
                if (iWasRaiser) {
                    fourBet = max(0.02, raiserWidth * 0.33) * style.aggression
                    callTop = max(0.06, raiserWidth * (if (ip) 1.05 else 0.8)) * style.preflopCall
                } else {
                    fourBet = 0.02 * style.aggression
                    callTop = 0.04 * style.preflopCall
                }
                val pValue = BotHeuristics.smooth(pct, fourBet, 0.006)
                val pBluff = if (iWasRaiser && BotHeuristics.isSuitedWheelAce(me.hole)) 0.3 * style.bluff else 0.0
                val p4 = (pValue + (1 - pValue) * pBluff).coerceAtMost(1.0)
                val pCall = (BotHeuristics.smooth(pct, callTop, 0.012) * (1 - p4))
                val target = (lastRaise.streetTotal * (if (ip) 2.3 else 2.6)).toInt()
                if (la.canRaise) opts += BotHeuristics.raiseMove(la, target) to p4 else opts += Move.CALL to p4
                opts += Move.CALL to pCall
                opts += Move.FOLD to (1 - p4 - pCall).coerceAtLeast(0.0)
            }
            else -> {
                val jam = 0.022 * style.aggression
                val pJam = BotHeuristics.smooth(pct, jam, 0.005)
                val pCall = BotHeuristics.smooth(pct, 0.045 * style.preflopCall, 0.008) * (1 - pJam)
                if (la.canRaise) opts += Move.raiseTo(la.maxRaiseTo) to pJam else opts += Move.CALL to pJam
                opts += Move.CALL to pCall
                opts += Move.FOLD to (1 - pJam - pCall).coerceAtLeast(0.0)
            }
        }
        return Policy(BotHeuristics.normalized(opts), null, potOdds, null)
    }

    fun postflop(
        engine: HandEngine,
        seat: Int,
        style: BotStyle,
        la: LegalActions,
        random: Random,
        t: TournamentContext?,
    ): Policy {
        val me = engine.player(seat)
        val board = engine.board
        val street = engine.street
        val opps = engine.players.filter { !it.folded && it.seat != seat }
        val nOpp = opps.size
        val eq = BotHeuristics.equityVsField(engine, seat, random, if (nOpp >= 3) 220 else 320)
        // 1.0 = a fair share of the pot: 1/(players) equity, scaled to heads-up (0.5 -> 1.0)
        val rel = eq * (nOpp + 1)
        val cls = BotHeuristics.handClass(me.hole, board)
        val wet = BotHeuristics.wetness(board)
        val pot = la.pot
        val spr = me.stack.toDouble() / max(1, pot)
        val order = engine.order
        val myIdx = order.indexOf(seat)
        val ip = opps.filter { !it.allIn }.all { order.indexOf(it.seat) < myIdx }
        // initiative: last aggressor on the previous street
        val prevStreet = Street.entries[street.ordinal - 1]
        val prevAggressor = engine.actions.lastOrNull { it.street == prevStreet && it.type.isAggressive }?.seat
        val initiative = prevAggressor == seat
        val opts = ArrayList<Pair<Move, Double>>()
        val multi = if (nOpp >= 2) 0.35 else 1.0

        if (la.toCall == 0) {
            // ---------------- we can check or bet
            if (!la.canRaise) return Policy(listOf(Move.CHECK to 1.0), eq, 0.0, null)
            val valueThr = when (street) { Street.FLOP -> 1.22; Street.TURN -> 1.3; else -> 1.4 }
            var pBet = BotHeuristics.above(rel, valueThr, 0.06) * min(1.0, 0.85 * style.aggression + 0.1)
            if (cls == 4 && street == Street.FLOP && wet < 0.3 && nOpp == 1) pBet *= 0.7 // some slow-playing
            val pValue = pBet
            if (rel < valueThr) {
                val bluff: Double = when {
                    cls == 1 && street != Street.RIVER -> 0.5
                    initiative && street == Street.FLOP && nOpp == 1 -> if (wet < 0.3) 0.65 else if (wet < 0.6) 0.45 else 0.3
                    initiative && street == Street.TURN && nOpp == 1 -> if (cls == 0) 0.3 else 0.15
                    initiative && street == Street.RIVER && nOpp == 1 -> if (cls == 0) 0.22 else 0.05
                    !initiative && ip && nOpp == 1 && prevAggressor == null -> 0.35  // stab when checked to
                    else -> 0.06
                }
                val medium = if (cls == 2 || cls == 3) 0.6 else 1.0  // medium hands prefer pot control
                pBet = max(pBet, bluff * style.bluff * multi * medium)
            }
            pBet = pBet.coerceIn(0.0, 0.97)
            if (spr < 1.3 && rel > valueThr) {
                opts += BotHeuristics.raiseMove(la, la.maxRaiseTo) to pBet
            } else if (pBet > 0) {
                val vShare = (pValue / max(1e-9, pBet)).coerceIn(0.0, 1.0)
                val wV = betSizeWeights(true, wet, street, nOpp)
                val wB = betSizeWeights(false, wet, street, nOpp)
                for (size in SizeClass.entries) {
                    val i = size.ordinal
                    val w = vShare * wV[i] + (1 - vShare) * wB[i]
                    if (w <= 0) continue
                    val chips = (pot * size.center).toInt().coerceAtLeast(engine.bigBlind)
                    opts += BotHeuristics.raiseMove(la, chips) to pBet * w
                }
            }
            opts += Move.CHECK to (1 - pBet)
            return Policy(BotHeuristics.normalized(opts), eq, 0.0, null)
        }

        // ---------------- facing a bet
        val (req, icm) = BotHeuristics.requiredEquity(engine, seat, la, t)
        val potOdds = la.toCall.toDouble() / (pot + la.toCall)
        val realization = if (ip) 1.0 else 0.9
        val implied = if (cls == 1 && street != Street.RIVER && spr > 1.5) 0.05 else 0.0
        val eqEff = eq * realization + implied + style.callBias
        val raiseThr = when (street) { Street.FLOP -> 1.45; Street.TURN -> 1.52; else -> 1.6 }
        var pRaise = 0.0
        var pValueRaise = 0.0
        if (la.canRaise) {
            pRaise = BotHeuristics.above(rel, raiseThr, 0.05) * min(1.0, 0.65 * style.aggression)
            if (cls == 4 && street == Street.FLOP && wet < 0.3) pRaise *= 0.6
            pValueRaise = pRaise
            val bluffRaise = when {
                cls == 1 && street == Street.FLOP -> 0.12
                cls == 1 && street == Street.TURN -> 0.06
                cls == 0 && street == Street.RIVER -> 0.03
                else -> 0.0
            } * style.bluff * multi
            if (rel < raiseThr) pRaise = max(pRaise, bluffRaise)
        }
        var pCall = BotHeuristics.above(eqEff - req, 0.0, 0.03)
        // minimum-defence guard vs small bets with real hands (GTO-ish)
        if (potOdds < 0.22 && cls >= 2) pCall = max(pCall, 0.85)
        pCall *= (1 - pRaise)
        if (pRaise > 0) {
            if (spr < 2.0) {
                opts += BotHeuristics.raiseMove(la, la.maxRaiseTo) to pRaise
            } else {
                val vShare = (pValueRaise / max(1e-9, pRaise)).coerceIn(0.0, 1.0)
                val (mults, wValue) = raiseSizes(true, street)
                val (_, wBluff) = raiseSizes(false, street)
                for (i in mults.indices) {
                    val w = vShare * wValue[i] + (1 - vShare) * wBluff[i]
                    val target = (la.currentBet * mults[i]).toInt().coerceAtLeast(la.minRaiseTo)
                    opts += BotHeuristics.raiseMove(la, target) to pRaise * w
                }
            }
        }
        opts += Move.CALL to pCall
        opts += Move.FOLD to (1 - pRaise - pCall).coerceAtLeast(0.0)
        return Policy(BotHeuristics.normalized(opts), eq, potOdds, icm)
    }
}
