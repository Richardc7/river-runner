package com.riverrunner.engine

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Preflop opening/push tables, board texture and hand-class heuristics,
 * opponent range samplers, and ICM required-equity helpers used by [Bot].
 */
internal object BotHeuristics {

    // ------------------------------------------------------------------ tables

    /**
     * Raise-first-in width by number of players left to act behind.
     * Based on common solver opening charts (100bb, ~2.5x opens):
     * BTN ~44%, CO ~28%, HJ ~21%, LJ ~17.5%, UTG(7-8 max) ~12-14%. SB raises ~42%.
     */
    fun rfiWidth(behind: Int, headsUp: Boolean): Double {
        if (headsUp) return 0.82
        return when (behind) {
            0 -> 0.0
            1 -> 0.42  // SB vs BB
            2 -> 0.44  // BTN
            3 -> 0.28  // CO
            4 -> 0.21  // HJ
            5 -> 0.175
            6 -> 0.145
            else -> 0.12
        }
    }

    /**
     * Approximate Nash push width (fraction of hands) by effective stack in big
     * blinds and players left to act. Reference points: SB 10bb ~58%,
     * BTN 10bb ~40%, CO 10bb ~29%, UTG(6-max) 10bb ~17%; heads-up SB 10bb ~62%.
     */
    fun pushWidth(effBB: Double, behind: Int): Double {
        val base10 = when (behind) {
            0 -> 0.0
            1 -> 0.58
            2 -> 0.40
            3 -> 0.29
            4 -> 0.22
            5 -> 0.17
            6 -> 0.14
            else -> 0.12
        }
        // widen as stacks get shorter, tighten as they get deeper
        val factor = (10.0 / effBB.coerceAtLeast(1.0)).let { Math.pow(it, 0.85) }
        return (base10 * factor).coerceIn(0.0, 1.0)
    }

    // ------------------------------------------------------------------ math & cards

    fun smooth(x: Double, threshold: Double, band: Double): Double =
        1.0 / (1.0 + exp((x - threshold) / band))   // 1 when x << threshold

    fun above(x: Double, threshold: Double, band: Double): Double =
        1.0 / (1.0 + exp(-(x - threshold) / band))  // 1 when x >> threshold

    fun isSuited(h: IntArray) = Cards.suit(h[0]) == Cards.suit(h[1])

    fun isSuitedWheelAce(h: IntArray): Boolean {
        if (!isSuited(h)) return false
        val r = listOf(Cards.rank(h[0]), Cards.rank(h[1])).sorted()
        return r[1] == 12 && r[0] <= 3
    }

    // ------------------------------------------------------------------ ranges & board

    /** Estimates every player's preflop range from the public action sequence. */
    fun preflopRanges(engine: HandEngine): Map<Int, RangeEstimate> {
        val n = engine.order.size
        val hu = n == 2
        val ranges = HashMap<Int, RangeEstimate>()
        for (s in engine.order) ranges[s] = RangeEstimate(0.0, 1.0)
        var raises = 0
        var limpers = 0
        var lastRaiserWidth = 1.0
        val bb = engine.bigBlind.toDouble()
        for (a in engine.actions) {
            if (a.street != Street.PREFLOP || a.type.isPost) continue
            val behind = engine.playersBehindPreflop(a.seat)
            val isBB = a.seat == engine.bigBlindSeat
            when (a.type) {
                ActionType.FOLD -> {}
                ActionType.CHECK -> ranges[a.seat] = RangeEstimate(0.06, 1.0)
                ActionType.CALL -> {
                    ranges[a.seat] = when (raises) {
                        0 -> RangeEstimate(0.06, 0.6)
                        1 -> if (isBB) RangeEstimate(min(0.03, lastRaiserWidth * 0.25), min(0.8, lastRaiserWidth * 2.0 + 0.1))
                             else RangeEstimate(lastRaiserWidth * 0.2, min(0.7, lastRaiserWidth * 0.9))
                        2 -> RangeEstimate(0.012, max(0.05, lastRaiserWidth * 1.4))
                        else -> RangeEstimate(0.0, max(0.03, lastRaiserWidth * 1.5))
                    }
                }
                ActionType.BET, ActionType.RAISE -> {
                    val stackBB = (engine.player(a.seat).startStack) / bb
                    val w = when (raises) {
                        0 -> if (a.allIn && stackBB <= 15) pushWidth(stackBB, behind)
                             else if (limpers > 0) rfiWidth(behind, hu) * 0.65
                             else rfiWidth(behind, hu)
                        1 -> if (a.allIn && stackBB <= 20) max(0.06, lastRaiserWidth * 0.5)
                             else max(0.03, lastRaiserWidth * 0.33)
                        2 -> max(0.025, lastRaiserWidth * 0.4)
                        else -> max(0.02, lastRaiserWidth * 0.6)
                    }
                    ranges[a.seat] = RangeEstimate(0.0, w.coerceIn(0.015, 1.0))
                    lastRaiserWidth = w
                    raises++
                }
                else -> {}
            }
            if (a.type == ActionType.CALL && raises == 0) limpers++
        }
        return ranges
    }

    /** Coarse made-hand class: 0 air, 1 draw, 2 weak made, 3 strong one-pair, 4 two pair or better. */
    fun handClass(hole: IntArray, board: List<Int>): Int {
        if (board.size < 3) return 0
        val boardMask = Cards.mask(board.toIntArray())
        val all = boardMask or Cards.mask(hole)
        val heroCat = HandEvaluator.category(HandEvaluator.evaluate(all))
        val boardCat = HandEvaluator.category(HandEvaluator.evaluate(boardMask))
        val boardRanks = board.map { Cards.rank(it) }
        val topBoard = boardRanks.max()
        val h0 = Cards.rank(hole[0]); val h1 = Cards.rank(hole[1])
        if (heroCat > boardCat) {
            when {
                heroCat >= HandEvaluator.STRAIGHT -> return 4
                heroCat == HandEvaluator.TRIPS -> return 4
                heroCat == HandEvaluator.TWO_PAIR && boardCat == HandEvaluator.HIGH_CARD -> return 4
                heroCat == HandEvaluator.TWO_PAIR && boardCat == HandEvaluator.PAIR -> {
                    val pr = if (h0 == h1) h0 else if (h0 in boardRanks) h0 else h1
                    return if (pr >= topBoard) 3 else 2
                }
                heroCat == HandEvaluator.PAIR -> {
                    if (h0 == h1) return if (h0 > topBoard) 3 else 2
                    val pr = if (h0 in boardRanks) h0 else h1
                    val kicker = if (pr == h0) h1 else h0
                    return if (pr == topBoard && (kicker >= 8 || pr >= 11)) 3 else 2
                }
                else -> return 2
            }
        }
        return if (board.size < 5 && drawOuts(hole, board) >= 8) 1 else if (board.size < 5 && drawOuts(hole, board) >= 4 && h0 >= 9 && h1 >= 9) 1 else 0
    }

    /** Rough count of outs to a flush or straight (only draws using a hole card). */
    fun drawOuts(hole: IntArray, board: List<Int>): Int {
        var outs = 0
        val cards = board + hole.toList()
        for (s in 0 until 4) {
            val cnt = cards.count { Cards.suit(it) == s }
            if (cnt == 4 && hole.any { Cards.suit(it) == s }) outs += 9
        }
        val ranks = cards.map { Cards.rank(it) }.toSet()
        val boardRanks = board.map { Cards.rank(it) }.toSet()
        fun hasStraight(rs: Set<Int>): Boolean {
            var m = 0
            for (r in rs) m = m or (1 shl r)
            if (m and ((1 shl 12) or 0xF) == ((1 shl 12) or 0xF)) return true
            for (hi in 4..12) { val need = 0x1F shl (hi - 4); if (m and need == need) return true }
            return false
        }
        if (!hasStraight(ranks)) {
            var straightRanks = 0
            for (r in 0..12) {
                if (r in ranks) continue
                if (hasStraight(ranks + r) && !hasStraight(boardRanks + r)) straightRanks++
            }
            outs += min(straightRanks, 2) * 4
        }
        return outs
    }

    /** 0 = very dry, 1 = very wet. */
    fun wetness(board: List<Int>): Double {
        if (board.size < 3) return 0.0
        val suits = IntArray(4)
        board.forEach { suits[Cards.suit(it)]++ }
        val maxSuit = suits.max()
        val ranks = board.map { Cards.rank(it) }.distinct().sorted()
        var connected = 0
        for (i in 1 until ranks.size) if (ranks[i] - ranks[i - 1] <= 2) connected++
        val paired = ranks.size < board.size
        var w = 0.0
        if (maxSuit >= 3) w += 0.45 else if (maxSuit == 2) w += 0.2
        w += connected * 0.18
        if (ranks.size >= 3 && ranks.last() - ranks[ranks.size - 3] <= 4) w += 0.15
        if (paired) w -= 0.2
        if (ranks.last() >= 12 && connected == 0) w -= 0.1
        return w.coerceIn(0.0, 1.0)
    }

    private fun weightFor(cls: Int, action: ActionType?): Double = when (action) {
        ActionType.BET, ActionType.RAISE -> when (cls) { 0 -> 0.3; 1 -> 0.85; 2 -> 0.45; else -> 1.0 }
        ActionType.CALL -> when (cls) { 0 -> 0.12; 1 -> 1.0; 2 -> 0.9; 3 -> 0.9; else -> 0.6 }
        ActionType.CHECK -> when (cls) { 0 -> 1.0; 1 -> 0.9; 2 -> 1.0; 3 -> 0.7; else -> 0.45 }
        else -> 1.0
    }

    /** Builds a sampler for an opponent's hand using preflop range + postflop action weights. */
    fun samplerFor(engine: HandEngine, seat: Int, range: RangeEstimate): Equity.HandSampler {
        val board = engine.board
        // strongest action per postflop street for this opponent
        val streetActions = HashMap<Street, ActionType>()
        for (a in engine.actions) {
            if (a.seat != seat || a.street == Street.PREFLOP) continue
            val prev = streetActions[a.street]
            if (prev == null || a.type.isAggressive || (prev == ActionType.CHECK && a.type == ActionType.CALL)) streetActions[a.street] = a.type
        }
        return Equity.HandSampler { dead, random ->
            var lastMask = 0L
            for (attempt in 0 until 25) {
                val packed = HandClasses.sample(range.lo, range.hi, dead, random)
                    .let { if (it < 0) HandClasses.sample(0.0, 1.0, dead, random) else it }
                if (packed < 0) return@HandSampler 0L
                val c1 = packed / 64; val c2 = packed % 64
                val mask = (1L shl c1) or (1L shl c2)
                lastMask = mask
                if (streetActions.isEmpty()) return@HandSampler mask
                var w = 1.0
                val hole = intArrayOf(c1, c2)
                for ((st, act) in streetActions) {
                    val prefix = board.take(st.boardCount)
                    if (prefix.size < 3) continue
                    w *= weightFor(handClass(hole, prefix), act)
                }
                if (random.nextDouble() < w) return@HandSampler mask
            }
            lastMask
        }
    }

    fun equityVsField(engine: HandEngine, seat: Int, random: Random, iters: Int = 300): Double {
        val ranges = preflopRanges(engine)
        val me = engine.player(seat)
        val opps = engine.players.filter { it.seat != seat && !it.folded }
        val samplers = opps.map { samplerFor(engine, it.seat, ranges.getValue(it.seat)) }
        return Equity.vsRanges(me.hole, engine.board, samplers, iters, random)
    }

    // ------------------------------------------------------------------ shared decision helpers

    fun normalized(opts: List<Pair<Move, Double>>): List<Pair<Move, Double>> {
        val merged = LinkedHashMap<Move, Double>()
        for ((m, p) in opts) if (p > 0) merged[m] = (merged[m] ?: 0.0) + p
        val total = merged.values.sum()
        if (total <= 0) return listOf(Move.CHECK to 1.0)
        // drop tiny frequencies to keep play clean
        val cleaned = merged.filter { it.value / total >= 0.03 }
        val t2 = cleaned.values.sum()
        return cleaned.map { it.key to it.value / t2 }
    }

    fun raiseMove(la: LegalActions, target: Int): Move {
        var t = target.coerceIn(la.minRaiseTo, la.maxRaiseTo)
        // if the raise would leave a tiny stack behind, just shove
        if (la.maxRaiseTo - t < (la.maxRaiseTo - la.myStreetBet) * 0.25) t = la.maxRaiseTo
        return Move.raiseTo(t)
    }

    /** Equity needed to call: pot odds, or ICM when it is a big tournament decision. */
    fun requiredEquity(engine: HandEngine, seat: Int, la: LegalActions, tournament: TournamentContext?): Pair<Double, Double?> {
        val potOdds = la.toCall.toDouble() / (la.pot + la.toCall)
        if (tournament == null) return potOdds to null
        val me = engine.player(seat)
        val bigDecision = la.toCall >= me.stack * 0.5 || la.toCall == me.stack
        val live = engine.players.filter { !it.folded && it.seat != seat }
        if (!bigDecision || live.size != 1) return potOdds to null
        val v = live[0]
        val seats = tournament.stacks.keys.sorted()
        val idx = seats.indexOf(seat)
        if (idx < 0) return potOdds to null
        val call = min(la.toCall, me.stack)
        val myTotal = me.committed + call
        val vMatched = min(v.committed, myTotal)
        val dead = engine.players.filter { it.seat != seat && it.seat != v.seat }.sumOf { it.committed }
        fun stacksWith(f: (Int, Int) -> Int): IntArray = IntArray(seats.size) { i ->
            val s = seats[i]
            val ps = engine.players.firstOrNull { it.seat == s }
            val now = ps?.stack ?: tournament.stacks.getValue(s)
            f(s, now)
        }
        val ifFold = stacksWith { s, now -> if (s == v.seat) now + engine.pot else now }
        val ifWin = stacksWith { s, now ->
            when (s) {
                seat -> now - call + myTotal + vMatched + dead
                v.seat -> now + (v.committed - vMatched)
                else -> now
            }
        }
        val ifLose = stacksWith { s, now ->
            when (s) {
                seat -> now - call
                v.seat -> now + myTotal + v.committed + dead
                else -> now
            }
        }
        val req = Icm.requiredEquity(idx, ifFold, ifWin, ifLose, tournament.payouts)
        return max(req, potOdds) to req
    }
}
