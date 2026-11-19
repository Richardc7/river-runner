package com.riverrunner.engine

import kotlin.random.Random

/**
 * Player styles. GTO approximates solver-like play; the others deviate in
 * recognisable, exploitable ways so a "mixed" table resembles a real game.
 */
enum class BotStyle(
    val code: String,
    /** multiplies preflop range widths */
    val looseness: Double,
    /** multiplies value raising / betting frequencies */
    val aggression: Double,
    /** multiplies bluffing frequencies */
    val bluff: Double,
    /** added to effective equity when deciding to call (positive = calls more) */
    val callBias: Double,
    /** extra width multiplier for flat calls preflop */
    val preflopCall: Double,
) {
    GTO("G", 1.0, 1.0, 1.0, 0.0, 1.0),
    TAG("T", 0.85, 1.1, 0.75, -0.02, 0.85),
    LAG("L", 1.35, 1.25, 1.5, 0.01, 1.1),
    STATION("S", 1.5, 0.45, 0.25, 0.12, 2.2),
    NIT("N", 0.6, 0.8, 0.35, -0.06, 0.7),
    MANIAC("M", 1.9, 1.8, 2.3, 0.04, 1.4);

    companion object {
        fun fromCode(c: String): BotStyle = entries.firstOrNull { it.code == c } ?: GTO
    }
}

/** Tournament info a bot may use for ICM decisions. */
class TournamentContext(
    /** stacks (start of this hand) of every player still in the tournament, by seat */
    val stacks: Map<Int, Int>,
    val payouts: DoubleArray,
)

/** A mixed strategy over legal moves. */
class Policy(
    val options: List<Pair<Move, Double>>,
    /** estimated equity vs. opponents' ranges, when computed */
    val equity: Double?,
    /** pot odds faced (0 when nothing to call) */
    val potOdds: Double,
    /** required equity under ICM when it applied */
    val icmRequired: Double?,
    /** pot when the decision was made, used to classify bet sizes */
    val pot: Int = 0,
) {
    fun sample(random: Random): Move {
        var r = random.nextDouble()
        for ((m, p) in options) {
            r -= p
            if (r <= 0) return m
        }
        return options.last().first
    }

    fun probability(type: MoveType): Double = options.filter { it.first.type == type }.sumOf { it.second }

    /** The single most likely move. */
    val best: Move get() = options.maxBy { it.second }.first
}

/** A percentile interval [lo, hi) of the starting-hand order describing a range. */
data class RangeEstimate(val lo: Double, val hi: Double)
