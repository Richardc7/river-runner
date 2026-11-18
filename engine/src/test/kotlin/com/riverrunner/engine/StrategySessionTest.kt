package com.riverrunner.engine

import com.riverrunner.engine.strategy.*
import kotlin.test.*

class StrategySessionTest {
    @Test fun legacyModeKeepsOriginalRandomPath() {
        val config = SessionConfig(GameMode.CASH, 6, 100, TableType.MIXED, seed = 991)
        val old = GameSession(config); val integrated = GameSession(config)
        repeat(20) {
            if (old.isOver || integrated.isOver) return@repeat
            val a = old.startHand(); val b = integrated.startHand()
            while (a.phase != Phase.HAND_DONE) {
                assertEquals(a.phase, b.phase)
                if (a.phase == Phase.STREET_DONE) { a.dealNextStreet(); b.dealNextStreet(); continue }
                val seat = a.toAct
                val moveA = Bot.decide(a, seat, old.players[seat].style, old.random, old.tournamentContext()).sample(old.random)
                val moveB = integrated.botMove(seat)
                assertEquals(moveA, moveB)
                a.apply(moveA); integrated.applyMove(moveB)
            }
            assertEquals(a.actions, b.actions)
            assertEquals(a.board, b.board)
            assertEquals(a.players.map { it.stack }, b.players.map { it.stack })
            old.completeHand(a); integrated.completeHand(b)
        }
    }
    @Test fun adaptiveSessionConservesChipsAndCollectsEvidence() {
        val s = GameSession(SessionConfig(GameMode.CASH, 3, 100, TableType.MIXED, seed = 18,
            edition = EditionConfig(AiMode.ADAPTIVE, mapOf(1 to OpponentPreset.LOOSE_PASSIVE))))
        repeat(20) {
            if (s.isOver) return@repeat
            val e = s.startHand(); var steps = 0
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) e.dealNextStreet()
                else { s.applyMove(s.botMove(e.toAct)); assertTrue(++steps < 500) }
            }
            s.completeHand(e)
            assertEquals(30000, s.players.sumOf { it.stack })
        }
        assertTrue(s.strategy.opponents.snapshot(1).handsObserved > 0)
        assertEquals(BotStyle.STATION, s.players[1].style)
    }
    @Test fun adviceDoesNotChangeAdaptiveCardsOrActions() {
        val config = SessionConfig(GameMode.CASH, 2, 100, TableType.MIXED, seed = 27, edition = EditionConfig(AiMode.ADAPTIVE))
        val a = GameSession(config); val b = GameSession(config)
        repeat(10) {
            if (a.isOver || b.isOver) return@repeat
            val ea = a.startHand(); val eb = b.startHand()
            while (ea.phase != Phase.HAND_DONE) {
                if (ea.phase == Phase.STREET_DONE) { ea.dealNextStreet(); eb.dealNextStreet(); continue }
                b.strategy.advise(eb)
                val ma = a.botMove(ea.toAct); val mb = b.botMove(eb.toAct)
                assertEquals(ma, mb); a.applyMove(ma); b.applyMove(mb)
            }
            assertEquals(ea.board, eb.board)
            a.completeHand(ea); b.completeHand(eb)
        }
    }
    @Test fun heroCannotBeOverridden() {
        assertFailsWith<IllegalArgumentException> { EditionConfig(opponents = mapOf(0 to OpponentPreset.MANIAC)) }
    }
}
