package com.riverrunner

import com.riverrunner.engine.GameMode
import com.riverrunner.engine.HandEngine
import com.riverrunner.engine.HandSetup
import com.riverrunner.engine.Move
import com.riverrunner.engine.RandomCardSource
import com.riverrunner.engine.SeatSetup
import com.riverrunner.engine.strategy.ObservationFactory
import com.riverrunner.i18n.ZH
import com.riverrunner.ui.DecisionContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DecisionContextTest {
    private fun table(vararg stacks: Int) = HandEngine(
        HandSetup(50, 100, 0, 0, stacks.mapIndexed { seat, chips -> SeatSetup(seat, "Seat $seat", chips) }),
        RandomCardSource(Random(19)),
    )

    @Test fun buttonAndBigBlindUseTheirOwnDecisionStacksAndPrices() {
        val engine = table(10_000, 2_000)
        val button = ObservationFactory.forActor(engine, GameMode.CASH)
        val raise = engine.apply(Move.raiseTo(250))
        val opening = DecisionContext(button)
        assertEquals("BTN", opening.position)
        assertEquals(listOf(1900), opening.effectiveStacks)
        assertEquals(1, opening.othersToAct)
        assertTrue(opening.lines(ZH, raise).toMap().getValue("实际尺度").contains("实际投入 2 BB"))

        val facing = DecisionContext(ObservationFactory.forActor(engine, GameMode.CASH))
        assertEquals("BB", facing.position)
        assertEquals(150, facing.call)
        assertEquals(.30, requireNotNull(facing.equityNeeded), .0001)
        assertEquals(1750, facing.behindAfterCall)
        assertEquals(0, facing.othersToAct)
        val readout = facing.lines(ZH, null).toMap()
        assertTrue(readout.getValue("跟注价格").contains("BTN 加注到 2.5 BB"))
        assertTrue("翻后不利位置" in readout.getValue("位置"))
    }

    @Test fun shortCallExcludesChipsTheActorCannotWin() {
        val engine = table(10_000, 2_000)
        engine.apply(Move.raiseTo(4_000))
        val context = DecisionContext(ObservationFactory.forActor(engine, GameMode.CASH))
        assertEquals(1900, context.call)
        assertEquals(2100, context.eligiblePot)
        assertEquals(4000, context.potAfterCall)
        assertEquals(.475, requireNotNull(context.equityNeeded), .0001)
        assertEquals(0, context.behindAfterCall)
    }

    @Test fun multiwayUsesStackRangeAndCountsOtherResponses() {
        val engine = table(10_000, 3_000, 6_000, 15_000)
        val context = DecisionContext(ObservationFactory.forActor(engine, GameMode.TOURNAMENT))
        assertEquals("UTG", context.position)
        assertEquals(listOf(2950, 5900, 10_000), context.effectiveStacks)
        assertEquals(3, context.othersToAct)
        assertTrue("29.5 BB–100 BB" in context.lines(ZH, null).toMap().getValue("筹码"))
    }
}
