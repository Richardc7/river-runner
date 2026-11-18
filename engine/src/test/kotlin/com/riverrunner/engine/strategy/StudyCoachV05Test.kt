package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.test.*

class StudyCoachV05Test {
    private val hero = PublicSeat(0, 10000, 9950, 50, 50, false, false)
    private val opponents = (1..8).map {
        PublicSeat(it, 10000, if (it == 1) 9900 else 10000, if (it == 1) 100 else 0,
            if (it == 1) 100 else 0, false, false)
    }
    private fun preflop(): DecisionView = DecisionView(
        0, Cards.parseMany("AsKh").toList(), Street.PREFLOP, emptyList(), 150,
        100, 8, (0..8).toList(), listOf(hero) + opponents, emptyList(),
        LegalActions(0, 50, false, true, true, false, 200, 9950, 100, 50, 150), GameMode.CASH
    )
    private fun trace(v: DecisionView): DecisionTrace {
        val policy = PolicySnapshot(listOf(
            PolicyChoice(Move.CALL, .10), PolicyChoice(Move.raiseTo(300), .90)
        ), null, .25, 150)
        return DecisionTrace(0, 0, Street.PREFLOP, DecisionFingerprint.of(v), policy, policy,
            AdviceStatus.BASELINE, emptyList(), emptyList())
    }
    @Test fun nineHandedPreflopMatrixIs169AndConditional() {
        val v = preflop()
        val result = requireNotNull(PreflopStudy.forDecision(v))
        assertEquals(13, result.cells.size)
        assertEquals(169, result.cells.flatten().size)
        assertEquals("AA", result.cells[0][0].name)
        assertEquals("AKs", result.cells[0][1].name)
        assertEquals("AKo", result.cells[1][0].name)
        assertEquals("AKo", result.ownClass)
        assertTrue(result.cells.flatten().all { it.raiseFrequency in 0.0..1.0 })
        assertNull(PreflopStudy.forDecision(v.copy(street=Street.FLOP, board=Cards.parseMany("2c3d4h").toList())))
    }
    @Test fun assessmentReportsHeuristicNotInventedEv() {
        val v = preflop()
        val a = StudyCoach.assess(55, v, trace(v), Action(Street.PREFLOP, 0, ActionType.CALL, 50, 100, false))
        assertEquals("h55a0", a.id)
        assertEquals(Street.PREFLOP, a.street)
        assertEquals(2, a.ownHole.size)
        assertTrue(a.board.isEmpty())
        assertEquals(MoveType.RAISE, a.preferred)
        assertTrue(a.needsStudy)
        assertNull(a.modelGapBB)
        assertEquals(9, v.order.size)
        assertEquals(5, StudyCoach.categories(listOf(a)).size)
        assertFailsWith<IllegalArgumentException> {
            StudyCoach.assess(55, v.copy(pot=200), trace(v), null)
        }
    }
    @Test fun reviewScheduleHonorsDueDaysAndUnknownCards() {
        val v = preflop()
        val a = StudyCoach.assess(55, v, trace(v), Action(Street.PREFLOP, 0, ActionType.CALL, 50, 100, false))
        val p = ReviewProgress()
        assertEquals(a.id, p.next(listOf(a), 100, true)?.id)
        val fail = p.record(a.id, 0.0, 100)
        assertNull(fail.next(listOf(a), 100, true))
        assertEquals(a.id, fail.next(listOf(a), 101, true)?.id)
        val pass = fail.record(a.id, 1.0, 101)
        assertNull(pass.next(listOf(a), 101, true))
        assertEquals(a.id, pass.next(listOf(a), 102, true)?.id)
        assertFailsWith<IllegalArgumentException> { p.record(a.id, Double.NaN, 100) }
    }
    @Test fun riverWhatIfDeclinesUnsupportedContext() {
        val v = preflop()
        val range = WeightedRange.of(listOf(WeightedRange.hand("QhJc")))
        val data = RiverWhatIf.explore(v, range, RiverWhatIfInput(.75, .4, 0.0, 100))
        assertFalse(data.supported)
        assertTrue(data.comparison.isEmpty())
        assertFailsWith<IllegalArgumentException> { RiverWhatIfInput(.75, 1.2, 0.0, 100) }
    }
    @Test fun privateOpponentOrFutureCardsCannotEnterPreflopReference() {
        val v = preflop()
        val coach = requireNotNull(PreflopStudy.forDecision(v))
        assertEquals(0, v.board.size)
        assertEquals("AKo", coach.ownClass)
        assertTrue(coach.explanation.contains("not solver", ignoreCase=true) ||
            coach.explanation.contains("not a", ignoreCase=true))
    }
}
