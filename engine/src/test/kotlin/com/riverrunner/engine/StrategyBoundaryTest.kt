package com.riverrunner.engine

import com.riverrunner.engine.strategy.*
import kotlin.test.*

class StrategyBoundaryTest {
    private fun engine(villain: String = "QsQc", board: String = "2c3d4h5s6c"): HandEngine = HandEngine(
        HandSetup(50, 100, 0, 0, listOf(SeatSetup(0, "Hero", 10000), SeatSetup(1, "V", 10000))),
        PresetCardSource(mapOf(0 to Cards.parseMany("AhKd"), 1 to Cards.parseMany(villain)), Cards.parseMany(board)),
    )
    @Test fun hiddenCardsAndFutureBoardDoNotReachView() {
        val a = ObservationFactory.forActor(engine(), GameMode.CASH)
        val b = ObservationFactory.forActor(engine("JsJc", "8c9dTh2s3c"), GameMode.CASH)
        assertEquals(a, b)
        assertEquals(2, a.ownHole.size)
        assertTrue(a.board.isEmpty())
        assertEquals(150, a.pot)
    }
    @Test fun viewDoesNotAliasHoleArray() {
        val e = engine()
        val view = ObservationFactory.forActor(e, GameMode.CASH)
        e.player(0).hole[0] = Cards.parse("2h")
        assertEquals(Cards.parse("Ah"), view.ownHole[0])
    }
    @Test fun raisesUseIncrementOverPotAfterCall() {
        val la = LegalActions(0, 50, false, true, true, false, 100, 1000, 50, 0, 150)
        assertEquals(.5, BetSizing.fraction(la, Move.raiseTo(150)), 1e-12)
        assertEquals(Move.raiseTo(150), BetSizing.target(la, .5))
    }
    @Test fun invalidProbabilitiesAndActionsAreRejected() {
        val la = engine().legalActions()
        assertFailsWith<IllegalArgumentException> { PolicyTools.normalize(listOf(Move.CALL to Double.NaN), la) }
        assertFailsWith<IllegalArgumentException> { PolicyTools.normalize(listOf(Move.CHECK to 1.0), la) }
        assertFailsWith<IllegalArgumentException> { PolicyTools.normalize(listOf(Move.raiseTo(1) to 1.0), la) }
        assertFailsWith<IllegalArgumentException> { PolicyTools.normalize(emptyList(), la) }
    }
    @Test fun boundedTransferConservesProbability() {
        val la = engine().legalActions()
        val p = Policy(listOf(Move.FOLD to .4, Move.CALL to .5, Move.raiseTo(300) to .1), .4, .25, null, la.pot)
        val adjusted = PolicyTools.transfer(p, MoveType.FOLD, MoveType.CALL, .1, la)
        assertEquals(.3, adjusted.probability(MoveType.FOLD), 1e-12)
        assertEquals(.6, adjusted.probability(MoveType.CALL), 1e-12)
        assertEquals(1.0, adjusted.options.sumOf { it.second }, 1e-12)
        assertEquals(.1, PolicyTools.distance(p, adjusted), 1e-12)
    }
    @Test fun decisionRandomStreamsAreIndependent() {
        val a = StrategyRandom.forDecision(17, 2, 5, 0, 1)
        repeat(1000) { a.nextLong() }
        val action = StrategyRandom.forDecision(17, 2, 5, 0, 2)
        val again = StrategyRandom.forDecision(17, 2, 5, 0, 2)
        repeat(100) { assertEquals(action.nextInt(), again.nextInt()) }
        assertNotEquals(StrategyRandom.forDecision(17, 2, 5, 0, 1).nextLong(), StrategyRandom.forDecision(17, 2, 5, 0, 2).nextLong())
    }
}
