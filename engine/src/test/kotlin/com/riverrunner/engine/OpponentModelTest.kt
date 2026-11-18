package com.riverrunner.engine
import com.riverrunner.engine.strategy.*
import kotlin.test.*

class OpponentModelTest {
    private fun engine(): HandEngine = HandEngine(HandSetup(50, 100, 0, 0,
        listOf(SeatSetup(0, "H", 10000), SeatSetup(1, "V", 10000))),
        PresetCardSource(mapOf(0 to Cards.parseMany("AhKd"), 1 to Cards.parseMany("QsQc")), Cards.parseMany("2c3d4h5s6c")))
    @Test fun priorShrinksSmallSample() {
        assertEquals(.58, BayesianStat(24, 30).estimate(.25, 20.0).mean, 1e-12)
        assertEquals(.6, BayesianStat(24, 30).estimate(.25, 20.0).evidenceWeight, 1e-12)
        assertEquals(.25, BayesianStat().estimate(.25).mean, 1e-12)
        assertFailsWith<IllegalArgumentException> { BayesianStat(3, 2) }
        assertFailsWith<IllegalArgumentException> { BayesianStat().estimate(.5, 0.0) }
    }
    @Test fun countsActualOpportunitiesAndIsIdempotent() {
        val e = engine(); val book = OpponentModel(); book.beginHand(0, e.order)
        fun act(move: Move): Pair<ActionOpportunity, Action> {
            val before = ActionOpportunity.from(ObservationFactory.forActor(e, GameMode.CASH))
            val action = e.apply(move); book.observe(before, action); return before to action
        }
        act(Move.CALL); act(Move.CHECK)
        assertEquals(BayesianStat(1, 1), book.snapshot(0).stat(StatKind.VPIP))
        assertEquals(BayesianStat(0, 1), book.snapshot(1).stat(StatKind.VPIP))
        e.dealNextStreet()
        act(Move.raiseTo(100))
        val (before, action) = act(Move.CALL)
        book.observe(before, action)
        assertFailsWith<IllegalArgumentException> { book.observe(before, action.copy(type = ActionType.FOLD)) }
        assertEquals(BayesianStat(0, 1), book.snapshot(0).stat(StatKind.FOLD_TO_BET, Street.FLOP))
        assertEquals(BayesianStat(1, 1), book.snapshot(0).stat(StatKind.CALL_BET, Street.FLOP))
        assertEquals(BayesianStat(), book.snapshot(1).stat(StatKind.FOLD_TO_BET, Street.FLOP))
    }
    @Test fun snapshotsCannotSeeFutureObservations() {
        val e = engine(); val book = OpponentModel(); book.beginHand(0, e.order)
        val snapshot = book.snapshot(0)
        val before = ActionOpportunity.from(ObservationFactory.forActor(e, GameMode.CASH))
        book.observe(before, e.apply(Move.CALL))
        assertEquals(BayesianStat(), snapshot.stat(StatKind.VPIP))
        assertEquals(BayesianStat(1, 1), book.snapshot(0).stat(StatKind.VPIP))
    }
    @Test fun sameSeatInNewSessionIsUnknown() {
        val e = engine(); val a = OpponentModel(); a.beginHand(0, e.order)
        val before = ActionOpportunity.from(ObservationFactory.forActor(e, GameMode.CASH)); a.observe(before, e.apply(Move.CALL))
        val b = OpponentModel(); b.beginHand(0, e.order)
        assertEquals(0, b.snapshot(0).stat(StatKind.VPIP).opportunities)
    }
}
