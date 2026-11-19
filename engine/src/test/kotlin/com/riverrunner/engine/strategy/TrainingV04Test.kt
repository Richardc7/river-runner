package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import kotlin.test.*
import kotlin.math.abs

class TrainingV04Test {
    private val fold = ActionValue(Move.FOLD,0.0,0.0,0.0)
    private val call = ActionValue(Move.CALL,20.0,20.0,20.0)
    @Test fun modelEvGradingAndProgress() {
        val drill = TrainingDrill("d1","river",listOf(fold,call))
        val grade = drill.grade(Move.FOLD)
        assertEquals(20.0,grade.modelLossChips)
        assertEquals(1,TrainingProgress().record(grade).categories.getValue("river").attempts)
        assertFailsWith<IllegalStateException> { drill.grade(Move.CHECK) }
    }
    @Test fun weaknessSchedulerIsDeterministic() {
        val weak = TrainingDrill("a","rare",listOf(fold,call))
        val strong = TrainingDrill("b","strong",listOf(fold,call))
        val p = TrainingProgress(mapOf("strong" to CategoryProgress(10,10)))
        assertEquals("a",TrainingCurriculum.next(listOf(weak,strong),p)?.id)
        assertEquals("b",TrainingCurriculum.next(listOf(weak,strong),p.copy(last="a"))?.id)
    }
    @Test fun rangeMatrixUses169Classes() {
        val range = WeightedRange.of(listOf(WeightedRange.hand("AsAh"),WeightedRange.hand("KdQd")))
        val grid = RangeMatrix.of(range)
        assertEquals(169,grid.flatten().size)
        assertEquals("AA",grid[0][0].handClass)
        assertEquals("AKs",grid[0][1].handClass)
        assertTrue(abs(grid.flatten().sumOf { it.mass } - 1.0) < 1e-9)
    }
    @Test fun referenceRoundTripRejectsMalformed() {
        val book = ReferenceBook(ReferenceProvenance("owned","writer","licensed","river 100bb"),
            listOf(ReferenceNode("a".repeat(64), listOf(ReferenceAction(Move.FOLD,.25),ReferenceAction(Move.CALL,.75)))))
        assertEquals(book,ReferenceCodec.decode(ReferenceCodec.encode(book)))
        assertFailsWith<IllegalArgumentException> { ReferenceCodec.decode("bad input") }
        assertFailsWith<IllegalArgumentException> { ReferenceNode("a".repeat(64),listOf(ReferenceAction(Move.FOLD,.2))) }
    }

    @Test fun solverSpotRetainsTheRiverRootWhenFacingABet() {
        val session = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO, seed = 7))
        val hand = session.startHand()
        while (hand.street != Street.RIVER) {
            if (hand.phase == Phase.STREET_DONE) hand.dealNextStreet()
            else hand.apply(if (hand.legalActions().canCheck) Move.CHECK else Move.CALL)
        }
        val rootView = ObservationFactory.forActor(hand, GameMode.CASH)
        val root = assertNotNull(SolverSpot.from(rootView))
        hand.apply(Move.raiseTo(hand.legalActions().minRaiseTo))
        val facingBet = ObservationFactory.forActor(hand, GameMode.CASH)
        val spot = assertNotNull(SolverSpot.from(facingBet))
        assertTrue(facingBet.pot > root.startingPot)
        assertEquals(root.startingPot, spot.startingPot)
        assertEquals(root.effectiveStack, spot.effectiveStack)
        assertEquals(ActionType.BET, spot.riverActions.single().type)
        assertNull(SolverSpot.from(facingBet.copy(mode = GameMode.TOURNAMENT)))
        assertNull(SolverSpot.from(facingBet.copy(cashRules = CashRules(rakeBasisPoints = 100, rakeCapBB = 1))))

        val hash = DecisionFingerprint.of(facingBet)
        val provenance = ReferenceProvenance("test", "author", "local", "river")
        val callNode = ReferenceNode(hash, listOf(ReferenceAction(Move.CALL, 1.0)))
        val checkNode = ReferenceNode(hash, listOf(ReferenceAction(Move.CHECK, 1.0)))
        assertEquals(callNode, ReferenceBook(provenance, listOf(callNode)).lookup(facingBet))
        assertNull(ReferenceBook(provenance, listOf(callNode)).lookup(rootView))
        assertNull(ReferenceBook(provenance, listOf(checkNode)).lookup(facingBet))
    }
}
