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
}
