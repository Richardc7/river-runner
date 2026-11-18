package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import java.util.Locale
import kotlin.math.sqrt

/** Independent seed blocks; identical decks within each baseline/adaptive pair. */
data class BenchmarkReport(
    val blocks: Int, val handsPerBlock: Int, val baselineBB100: Double,
    val adaptiveBB100: Double, val differenceBB100: Double, val blockStandardError: Double,
    val adjustedDecisions: Int,
) {
    fun json(): String = String.format(Locale.US,
        "{\"blocks\":%d,\"handsPerBlock\":%d,\"baselineBB100\":%.4f,\"adaptiveBB100\":%.4f,\"differenceBB100\":%.4f,\"blockStandardError\":%.4f,\"adjustedDecisions\":%d,\"interpretation\":\"Exploratory paired simulation; not evidence of real-world profit\"}",
        blocks, handsPerBlock, baselineBB100, adaptiveBB100, differenceBB100, blockStandardError, adjustedDecisions)
}
object SimulationBenchmark {
    fun run(blocks: Int = 8, handsPerBlock: Int = 100, seed: Long = 91L): BenchmarkReport {
        require(blocks in 2..1000 && handsPerBlock in 1..10000)
        val base = mutableListOf<Double>(); val adaptive = mutableListOf<Double>(); var adjusted = 0
        repeat(blocks) { block ->
            val blockSeed = seed + block * 1000003L
            val a = block(blockSeed, handsPerBlock, AiMode.LEGACY)
            val b = block(blockSeed, handsPerBlock, AiMode.ADAPTIVE)
            base += a.first; adaptive += b.first; adjusted += b.second
        }
        val differences = base.indices.map { adaptive[it] - base[it] }
        val mean = differences.average()
        val variance = differences.sumOf { (it - mean) * (it - mean) } / (blocks - 1)
        return BenchmarkReport(blocks, handsPerBlock, base.average(), adaptive.average(), mean, sqrt(variance / blocks), adjusted)
    }
    private fun block(seed: Long, hands: Int, mode: AiMode): Pair<Double, Int> {
        val strategy = StrategySession(seed, GameMode.CASH, EditionConfig(mode, recordAdvice = false))
        var net = 0.0; var adjusted = 0
        repeat(hands) { hand ->
            val e = HandEngine(HandSetup(50, 100, 0, hand % 3,
                (0..2).map { SeatSetup(it, "P$it", 10000) }),
                RandomCardSource(StrategyRandom.forDecision(seed, hand, -1, -1, 9)))
            strategy.beginHand(e, hand); var steps = 0
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                check(++steps < 500)
                val seat = e.toAct; val index = e.actions.count { !it.type.isPost }
                val rng = StrategyRandom.forDecision(seed, hand, index, seat, 11)
                val before = strategy.opportunity(e)
                val move = if (seat == 0) {
                    val advice = strategy.advise(e)
                    if (mode == AiMode.ADAPTIVE && advice.status == AdviceStatus.ADJUSTED) adjusted++
                    val policy = if (mode == AiMode.ADAPTIVE) advice.policy else advice.baseline
                    // Common random numbers isolate the overlay instead of comparing two RNG conventions.
                    policy.sample(StrategyRandom.forDecision(seed, hand, index, seat, 2))
                } else {
                    // Opponents remain fixed across policy versions; preset identity is not model evidence.
                    val style = if (seat == 1) BotStyle.STATION else BotStyle.MANIAC
                    Bot.decide(e, seat, style, rng).sample(rng)
                }
                check(PolicyTools.legal(move, e.legalActions()))
                val action = e.apply(move); strategy.observe(before, action)
            }
            check(e.players.sumOf { it.stack } == 30000)
            net += e.result!!.net.getValue(0) / 100.0
        }
        return net / hands * 100 to adjusted
    }
    @JvmStatic fun main(args: Array<String>) {
        val blocks = args.getOrNull(0)?.toIntOrNull() ?: 8
        val hands = args.getOrNull(1)?.toIntOrNull() ?: 100
        println(run(blocks, hands).json())
    }
}
