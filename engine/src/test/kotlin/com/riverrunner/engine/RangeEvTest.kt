package com.riverrunner.engine

import com.riverrunner.engine.strategy.*
import kotlin.random.Random
import kotlin.test.*

class RangeEvTest {
    private fun view(facing: Boolean = false, heroHole: String = "AsKh"): DecisionView {
        val hero = PublicSeat(0, 10000, 9500, 0, 500, false, false)
        val villain = PublicSeat(1, 10000, if (facing) 9000 else 9500, if (facing) 500 else 0, if (facing) 1000 else 500, false, false)
        val pot = hero.committed + villain.committed
        return DecisionView(0, Cards.parseMany(heroHole).toList(), Street.RIVER, Cards.parseMany("Ac7d2h9s3c").toList(), pot,
            100, 0, listOf(1, 0), listOf(hero, villain), emptyList(),
            LegalActions(0, if (facing) 500 else 0, !facing, facing, true, !facing, if (facing) 1000 else 100, 9500, if (facing) 500 else 0, 0, pot), GameMode.CASH)
    }
    @Test fun rangesNormalizeBlockCardsAndSampleWeights() {
        val r = WeightedRange.of(listOf(WeightedRange.hand("AhQd", 8.0), WeightedRange.hand("7h7s", 2.0)))
        assertEquals(1.0, r.combos.sumOf { it.weight }, 1e-12)
        val rng = Random(5); val first = r.combos.first().mask
        val observed = (0 until 5000).count { r.sample(0, rng) == first } / 5000.0
        assertTrue(observed in .77.. .83)
        val blocked = r.without(Cards.mask(Cards.parseMany("Ah")))
        assertEquals(1, blocked.combos.size)
        assertEquals(0L, r.sample(Cards.mask(Cards.parseMany("Ah7h")), Random(1)))
        assertFailsWith<IllegalArgumentException> { WeightedRange.of(listOf(r.combos[0], r.combos[0])) }
    }
    @Test fun uniformHasExactly1326Combinations() {
        assertEquals(1326, WeightedRange.uniform().combos.size)
        assertEquals(990, WeightedRange.uniform(Cards.mask((view().ownHole + view().board).toIntArray())).combos.size)
    }
    @Test fun riverCallMatchesPotOddsAtBreakEven() {
        val r = WeightedRange.of(listOf(WeightedRange.hand("AhQd", .25), WeightedRange.hand("7h7s", .75)))
        val result = RiverEv.analyze(view(true), r)
        assertTrue(result.supported)
        assertEquals(.25, result.rangeEquity!!, 1e-12)
        assertEquals(0.0, result.values.first { it.move.type == MoveType.CALL }.chips, 1e-9)
    }
    @Test fun uncalledExcessDoesNotInflateShortStackCallEv() {
        val v = view(true)
        val hero = v.actor.copy(startStack = 200, stack = 100, committed = 100)
        val villain = v.opponents.single().copy(startStack = 1000, stack = 0, streetBet = 900, committed = 1000, allIn = true)
        val short = v.copy(pot = 1100, seats = listOf(hero, villain), legal = LegalActions(0, 100, false, true, false, false, 100, 100, 900, 0, 1100))
        val result = RiverEv.analyze(short, WeightedRange.of(listOf(WeightedRange.hand("AhQd"))))
        assertEquals(300.0, result.values.first { it.move.type == MoveType.CALL }.chips, 1e-9)
    }
    @Test fun valueBetUsesEffectiveOpponentStack() {
        val v = view()
        val short = v.copy(seats = listOf(v.actor, v.opponents.single().copy(startStack = 600, stack = 100)))
        val result = RiverEv.analyze(short, WeightedRange.of(listOf(WeightedRange.hand("AhQd"))), declaredResponse = ResponseRates(0.0, 0.0))
        assertEquals(1100.0, result.values.first { it.move.type == MoveType.RAISE }.chips, 1e-9)
    }
    @Test fun pureBluffFormulaIsCorrectWhenAlwaysCalledOrAlwaysFolded() {
        val v = view(heroHole = "4h6h")
        val r = WeightedRange.of(listOf(WeightedRange.hand("AhQd")))
        val folded = RiverEv.analyze(v, r, declaredResponse = ResponseRates(1.0, 0.0))
        assertEquals(1000.0, folded.values.first { it.move.type == MoveType.RAISE }.chips, 1e-9)
        val called = RiverEv.analyze(v, r, declaredResponse = ResponseRates(0.0, 0.0))
        val bet = called.values.first { it.move.type == MoveType.RAISE }
        assertEquals(-bet.move.raiseTo.toDouble(), bet.chips, 1e-9)
    }
    @Test fun invalidContextsAreNotGivenFakeEv() {
        val v = view(); val r = WeightedRange.of(listOf(WeightedRange.hand("AhQd")))
        assertFalse(RiverEv.analyze(v.copy(mode = GameMode.TOURNAMENT), r).supported)
        assertFalse(RiverEv.analyze(v.copy(order = listOf(0, 1)), r).supported)
        assertFalse(RiverEv.analyze(v.copy(pot = 999), r).supported)
        assertFalse(RiverEv.analyze(v.copy(cashRules = CashRules(rakeBasisPoints = 500, rakeCapBB = 3)), r).supported)
    }
    @Test fun publicRangeInferenceIsFiniteAndCardSafe() {
        val v = view(); val r = RangeModel.infer(v, 1, null)
        val dead = Cards.mask((v.ownHole + v.board).toIntArray())
        assertEquals(990, r.combos.size)
        assertTrue(r.combos.all { it.mask and dead == 0L && it.weight.isFinite() && it.weight > 0 })
        assertEquals(1.0, r.combos.sumOf { it.weight }, 1e-10)
        assertEquals(1.0, r.classes().values.sum(), 1e-10)
    }
    @Test fun liveCaptureStoresConditionalRiverValues() {
        val session = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO, seed = 41,
            edition = EditionConfig(AiMode.ADAPTIVE)))
        val engine = session.startHand()
        while (engine.phase != Phase.HAND_DONE) {
            if (engine.phase == Phase.STREET_DONE) { engine.dealNextStreet(); continue }
            if (engine.street == Street.RIVER) session.strategy.capture(engine)
            session.applyMove(if (engine.legalActions().canCheck) Move.CHECK else Move.CALL)
        }
        session.completeHand(engine)
        val analysis = assertNotNull(session.lastAnalysis)
        assertTrue(analysis.decisions.any { it.actionValues.isNotEmpty() })
        assertTrue(analysis.decisions.any { it.notes.any { note -> note.contains("coaching-only") } })
        assertEquals(analysis, TraceCodec.decode(TraceCodec.encode(analysis)))
    }
}
