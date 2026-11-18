package com.riverrunner.engine

import com.riverrunner.engine.strategy.*
import kotlin.random.Random
import kotlin.test.*

class StrategyRegressionTest {
    @Test fun actualBaselinePolicyIgnoresOtherHoleCards() {
        fun engine(other: String): HandEngine = HandEngine(HandSetup(50, 100, 0, 0,
            listOf(SeatSetup(0, "H", 10000), SeatSetup(1, "V", 10000))),
            PresetCardSource(mapOf(0 to Cards.parseMany("AhKd"), 1 to Cards.parseMany(other)), Cards.parseMany("2c7d8h9sTc")))
        val a = engine("QsQc"); val b = engine("JsJc")
        while (a.phase != Phase.HAND_DONE) {
            if (a.phase == Phase.STREET_DONE) { a.dealNextStreet(); b.dealNextStreet(); continue }
            if (a.toAct == 0) {
                val pa = Bot.decide(a, 0, BotStyle.GTO, Random(19))
                val pb = Bot.decide(b, 0, BotStyle.GTO, Random(19))
                assertEquals(pa.options, pb.options); assertEquals(pa.equity, pb.equity)
            }
            val move = if (a.legalActions().canCheck) Move.CHECK else Move.CALL
            a.apply(move); b.apply(move)
        }
    }
    @Test fun adaptiveFuzzConservesChipsAndTerminates() {
        for (size in listOf(2, 3, 6, 8)) for (seed in 0L..2L) {
            val s = GameSession(SessionConfig(GameMode.CASH, size, 80, TableType.MIXED, seed = seed,
                edition = EditionConfig(AiMode.ADAPTIVE, recordAdvice = false)))
            repeat(3) {
                if (s.isOver) return@repeat
                val e = s.startHand(); var steps = 0
                while (e.phase != Phase.HAND_DONE) {
                    if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                    val move = s.botMove(e.toAct)
                    assertTrue(PolicyTools.legal(move, e.legalActions()))
                    s.applyMove(move); assertTrue(++steps < 500)
                }
                s.completeHand(e)
                assertEquals(size * 8000, s.players.sumOf { it.stack })
                assertTrue(s.players.all { it.stack >= 0 })
            }
        }
    }
    @Test fun h1StillReplaysToExactFinalStacks() {
        val s = GameSession(SessionConfig(GameMode.CASH, 6, 50, TableType.MIXED, seed = 117,
            edition = EditionConfig(AiMode.ADAPTIVE)))
        repeat(5) {
            if (s.isOver) return@repeat
            val e = s.startHand()
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) e.dealNextStreet() else s.applyMove(s.botMove(e.toAct))
            }
            val record = s.completeHand(e); val line = record.encode()
            assertTrue(line.startsWith("H1|"))
            val decoded = assertNotNull(HandRecord.decode(line))
            assertEquals(line, decoded.encode())
            assertEquals(e.players.associate { it.seat to it.stack }, Replayer(decoded).frames.last().players.associate { it.seat to it.stack })
        }
    }
    @Test fun pairedBenchmarkIsReproducibleWithoutClaimingProfit() {
        val a = SimulationBenchmark.run(2, 4, 13)
        val b = SimulationBenchmark.run(2, 4, 13)
        assertEquals(a, b)
        assertTrue(a.blockStandardError.isFinite() && a.blockStandardError >= 0)
        assertTrue(a.json().contains("Exploratory"))
        if (a.adjustedDecisions == 0) assertEquals(0.0, a.differenceBB100, 1e-9)
    }
}
