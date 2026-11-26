package com.riverrunner.engine

import com.riverrunner.engine.strategy.*
import kotlin.random.Random
import kotlin.test.*

class MultiwayRiverEvTest {
    private val board = Cards.parseMany("Ac7d2h9s3c").toList()
    private val hero = PublicSeat(0, 10000, 9500, 0, 500, false, false)

    private fun facing(others: List<PublicSeat>, rules: CashRules = CashRules()): DecisionView {
        val seats = listOf(hero) + others
        val pot = seats.sumOf { it.committed }
        return DecisionView(
            0, Cards.parseMany("AsKh").toList(), Street.RIVER, board, pot, 100, 0,
            others.map { it.seat } + 0, seats, emptyList(),
            LegalActions(0, 500, false, true, true, false, 1000, 9500, 500, 0, pot),
            GameMode.CASH, rules,
        )
    }

    private fun opponent(seat: Int) =
        PublicSeat(seat, 10000, 9000, 500, 1000, false, false)

    @Test fun sequentialJointDrawsUseImportanceWeightsForCardCollisions() {
        val a = WeightedRange.of(listOf(WeightedRange.hand("AsKs", 0.8), WeightedRange.hand("AdKd", 0.2)))
        val b = WeightedRange.of(listOf(WeightedRange.hand("AsQs", 0.7), WeightedRange.hand("JhTh", 0.3)))
        val sampler = JointRangeSampler(linkedMapOf(1 to a, 2 to b))
        val rng = Random(15)
        var total = 0.0
        var first = 0.0
        repeat(18000) {
            val result = assertNotNull(sampler.sample(0L, rng))
            val hands = result.holes.values.toList()
            assertEquals(0L, hands[0] and hands[1])
            val w = result.importanceWeight
            total += w
            if (result.holes[1] == WeightedRange.hand("AsKs").mask) first += w
        }
        // Normalized target mass: .8*.3 / (.8*.3 + .2*.7 + .2*.3).
        assertEquals(.24 / .44, first / total, .025)
    }

    @Test fun eightOpponentsNeverShareCardsOrKnownCards() {
        val holes = listOf("AhQd", "JhTh", "JdTd", "JsTs", "Qh4h", "Qc5d", "Kd6c", "8h8d")
        val ranges = holes.mapIndexed { i, h -> (i + 1) to WeightedRange.of(listOf(WeightedRange.hand(h))) }.toMap()
        val dead = Cards.mask((Cards.parseMany("AsKh").toList() + board).toIntArray())
        val sampled = assertNotNull(JointRangeSampler(ranges).sample(dead, Random(1)))
        var used = dead
        assertEquals(8, sampled.holes.size)
        sampled.holes.values.forEach { cards ->
            assertEquals(0L, used and cards)
            used = used or cards
        }
        assertEquals(1.0, sampled.importanceWeight, 1e-12)
    }

    @Test fun liveCaptureDistinguishesOpeningAndClosingMultiwayRiverChecks() {
        val session = GameSession(SessionConfig(GameMode.CASH, 3, 100, TableType.GTO, seed = 42,
            edition = EditionConfig(AiMode.ADAPTIVE)))
        val engine = session.startHand()
        while (engine.phase != Phase.HAND_DONE) {
            if (engine.phase == Phase.STREET_DONE) { engine.dealNextStreet(); continue }
            if (engine.street == Street.RIVER) session.strategy.capture(engine)
            session.applyMove(if (engine.legalActions().canCheck) Move.CHECK else Move.CALL)
        }
        session.completeHand(engine)
        val analysis = assertNotNull(session.lastAnalysis)
        val decisions = analysis.decisions.filter { it.street == Street.RIVER }
        assertEquals(3, decisions.size)
        assertTrue(decisions.all { it.isMultiwayEv })
        assertTrue(decisions.dropLast(1).all { it.actionValues.isEmpty() })
        assertEquals(listOf(Move.CHECK), decisions.last().actionValues.map { it.move })
        assertNull(decisions.last().gradableBestEv(), "A lone check EV cannot grade a decision")
        assertEquals(analysis, TraceCodec.decode(TraceCodec.encode(analysis)))
    }

    @Test fun threeWayClosingCallUsesTheWholePot() {
        val v = facing(listOf(opponent(1), opponent(2)))
        val ranges = mapOf(
            1 to WeightedRange.of(listOf(WeightedRange.hand("AhQd"))),
            2 to WeightedRange.of(listOf(WeightedRange.hand("JhTh"))),
        )
        val result = MultiwayRiverEv.analyze(v, ranges, Random(2), 80)
        assertTrue(result.supported, result.assumptions.toString())
        assertEquals(2500.0, result.values.single { it.move == Move.CALL }.chips, 1e-8)
        assertEquals(0.0, result.values.single { it.move == Move.FOLD }.chips, 1e-8)
    }

    @Test fun sidePotEligibilityAndCashRakeAreRespected() {
        val allIn = opponent(2).copy(startStack = 200, stack = 0, committed = 200, streetBet = 0, allIn = true)
        val opponents = listOf(opponent(1), allIn)
        val ranges = mapOf(
            1 to WeightedRange.of(listOf(WeightedRange.hand("AhQd"))),
            2 to WeightedRange.of(listOf(WeightedRange.hand("7h7s"))),
        )
        val zeroRake = MultiwayRiverEv.analyze(facing(opponents), ranges, Random(3), 80)
        assertTrue(zeroRake.supported)
        // The set wins the 600-chip main pot; hero wins the 1600-chip side pot.
        assertEquals(1100.0, zeroRake.values.single { it.move == Move.CALL }.chips, 1e-8)
        val withRake = MultiwayRiverEv.analyze(
            facing(opponents, CashRules(rakeBasisPoints = 500, rakeCapBB = 20)),
            ranges, Random(4), 80,
        )
        assertTrue(withRake.supported)
        assertEquals(1020.0, withRake.values.single { it.move == Move.CALL }.chips, 1e-8)
    }

    @Test fun nineWayClosingCallIsEvaluatedRatherThanRejected() {
        val holes = listOf("AhQd", "JhTh", "JdTd", "JsTs", "Qh4h", "Qc5d", "Kd6c", "8h8d")
        val ranges = holes.mapIndexed { i, h -> (i + 1) to WeightedRange.of(listOf(WeightedRange.hand(h))) }.toMap()
        val nine = facing((1..8).map(::opponent))
        val result = MultiwayRiverEv.analyze(nine, ranges, Random(5), 80)
        assertTrue(result.supported, result.assumptions.toString())
        assertEquals(8500.0, result.values.single { it.move == Move.CALL }.chips, 1e-8)
        assertTrue(result.assumptions.any { it.contains("8 live opponents") })
    }

    @Test fun nonterminalRiverCallIsNotMislabelledAsShowdown() {
        val pending = opponent(2).copy(streetBet = 0)
        val v = facing(listOf(opponent(1), pending))
        assertFalse(MultiwayRiverEv.isTerminal(v))
        val result = MultiwayRiverEv.analyze(v, emptyMap(), Random(8), 80)
        assertFalse(result.supported)
        assertTrue(result.values.isEmpty())
    }

    @Test fun finalCheckIsSupportedOnlyWhenEveryoneElseAlreadyChecked() {
        val others = listOf(opponent(1), opponent(2)).map { it.copy(streetBet = 0, committed = 500, stack = 9500) }
        val pot = 1500
        val actions = others.map { Action(Street.RIVER, it.seat, ActionType.CHECK, 0, 0, false) }
        val v = DecisionView(
            0, Cards.parseMany("AsKh").toList(), Street.RIVER, board, pot, 100, 0,
            listOf(1, 2, 0), listOf(hero) + others, actions,
            LegalActions(0, 0, true, false, true, true, 100, 9500, 0, 0, pot), GameMode.CASH,
        )
        val ranges = mapOf(
            1 to WeightedRange.of(listOf(WeightedRange.hand("AhQd"))),
            2 to WeightedRange.of(listOf(WeightedRange.hand("JhTh"))),
        )
        assertTrue(MultiwayRiverEv.isTerminal(v))
        val result = MultiwayRiverEv.analyze(v, ranges, Random(9), 80)
        assertEquals(1500.0, result.values.single { it.move == Move.CHECK }.chips, 1e-8)
        assertFalse(MultiwayRiverEv.isTerminal(v.copy(actions = actions.take(1))))
    }
    @Test fun terminalEvMatchesEngineSettlementAcrossTableSizesAndFoldedBlindTiers() {
        val boardCards = Cards.parseMany("AsKsQsJsTs")
        for (tableSize in listOf(3, 5, 6, 9)) {
            for (rules in listOf(CashRules(), CashRules(rakeBasisPoints = 1000, rakeCapBB = 20))) {
                val holes = (0..51).filterNot { it in boardCards.toSet() }.chunked(2)
                    .take(tableSize).mapIndexed { seat, cards -> seat to cards.toIntArray() }.toMap()
                val setup = HandSetup(1, 2, 0, tableSize - 1,
                    (0 until tableSize).map { SeatSetup(it, "Seat $it", 100) }, rules)
                val engine = HandEngine(setup, PresetCardSource(holes, boardCards))
                engine.apply(Move.raiseTo(4))
                while (engine.phase == Phase.BETTING) {
                    engine.apply(if (tableSize == 5 && engine.toAct in 0..1) Move.FOLD else Move.CALL)
                }
                repeat(2) {
                    engine.dealNextStreet()
                    while (engine.phase == Phase.BETTING) engine.apply(Move.CHECK)
                }
                engine.dealNextStreet()
                while (engine.toAct != setup.buttonSeat) engine.apply(Move.CHECK)
                val view = ObservationFactory.forActor(engine, GameMode.CASH)
                val ranges = view.opponents.associate { seat ->
                    val hand = holes.getValue(seat.seat)
                    seat.seat to WeightedRange.of(listOf(WeightedCombo(hand[0], hand[1], 1.0)))
                }
                val analysis = MultiwayRiverEv.analyze(view, ranges, Random(1), 80)
                engine.apply(Move.CHECK)
                val result = assertNotNull(engine.result)
                assertEquals(1, result.pots.size)
                if (tableSize == 5 && !rules.hasRake) assertEquals(5, result.won.getValue(view.actorSeat))
                assertEquals(result.won.getValue(view.actorSeat).toDouble(),
                    analysis.values.single().chips, 1e-10, "table size $tableSize, rules $rules")
            }
        }
    }

    @Test fun weightedStandardErrorUsesSquaredWeightResiduals() {
        val samples = listOf(0.05 to 30.0, 0.10 to -50.0, 0.40 to 120.0, 0.90 to -10.0)
        val moments = ImportanceSampleMoments()
        samples.forEach { (w, x) -> moments.add(w, x) }
        val total = samples.sumOf { it.first }
        val mean = samples.sumOf { (w, x) -> w * x } / total
        val expected = kotlin.math.sqrt(samples.sumOf { (w, x) ->
            val residual = w * (x - mean)
            residual * residual
        }) / total
        assertEquals(mean, moments.mean, 1e-12)
        assertEquals(expected, moments.standardError, 1e-12)
    }

}
