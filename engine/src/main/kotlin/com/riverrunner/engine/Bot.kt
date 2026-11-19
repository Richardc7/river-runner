package com.riverrunner.engine

import kotlin.random.Random

/**
 * Public facade for baseline bot decisions and poker heuristics.
 * Delegates range/board calculations to [BotHeuristics] and street policy
 * synthesis to [BotDecisions] to keep responsibilities isolated.
 */
object Bot {

    fun rfiWidth(behind: Int, headsUp: Boolean): Double =
        BotHeuristics.rfiWidth(behind, headsUp)

    fun pushWidth(effBB: Double, behind: Int): Double =
        BotHeuristics.pushWidth(effBB, behind)

    fun preflopRanges(engine: HandEngine): Map<Int, RangeEstimate> =
        BotHeuristics.preflopRanges(engine)

    fun handClass(hole: IntArray, board: List<Int>): Int =
        BotHeuristics.handClass(hole, board)

    fun drawOuts(hole: IntArray, board: List<Int>): Int =
        BotHeuristics.drawOuts(hole, board)

    fun wetness(board: List<Int>): Double =
        BotHeuristics.wetness(board)

    fun equityVsField(engine: HandEngine, seat: Int, random: Random, iters: Int = 300): Double =
        BotHeuristics.equityVsField(engine, seat, random, iters)

    /**
     * Decides for [seat] using only information that seat legitimately has:
     * its own hole cards, the board, stacks and the public action history.
     */
    fun decide(
        engine: HandEngine,
        seat: Int,
        style: BotStyle,
        random: Random,
        tournament: TournamentContext? = null,
    ): Policy {
        val la = engine.legalActions()
        require(la.seat == seat)
        val p = if (engine.street == Street.PREFLOP) {
            BotDecisions.preflop(engine, seat, style, la, random, tournament)
        } else {
            BotDecisions.postflop(engine, seat, style, la, random, tournament)
        }
        return Policy(p.options, p.equity, p.potOdds, p.icmRequired, la.pot)
    }
}
