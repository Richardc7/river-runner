package com.riverrunner.engine

import com.riverrunner.engine.strategy.*
import kotlin.test.*

class TurnEvAndFlopDrillTest {
    private fun turnView(facing: Boolean = false, heroHole: String = "AsKh"): DecisionView {
        val hero = PublicSeat(0, 10000, 9500, 0, 500, false, false)
        val villain = PublicSeat(1, 10000, if (facing) 9000 else 9500, if (facing) 500 else 0, if (facing) 1000 else 500, false, false)
        val pot = hero.committed + villain.committed
        return DecisionView(
            actorSeat = 0,
            ownHole = Cards.parseMany(heroHole).toList(),
            street = Street.TURN,
            board = Cards.parseMany("Ac7d2h9s").toList(),
            pot = pot,
            bigBlind = 100,
            buttonSeat = 0,
            order = listOf(1, 0),
            seats = listOf(hero, villain),
            actions = emptyList(),
            legal = LegalActions(0, if (facing) 500 else 0, !facing, facing, true, !facing, if (facing) 1000 else 100, 9500, if (facing) 500 else 0, 0, pot),
            mode = GameMode.CASH,
        )
    }

    private fun flopCbetView(boardStr: String, heroHole: String = "AsKh"): DecisionView {
        val hero = PublicSeat(0, 10000, 9700, 0, 300, false, false)
        val villain = PublicSeat(1, 10000, 9700, 0, 300, false, false)
        return DecisionView(
            actorSeat = 0,
            ownHole = Cards.parseMany(heroHole).toList(),
            street = Street.FLOP,
            board = Cards.parseMany(boardStr).toList(),
            pot = 600,
            bigBlind = 100,
            buttonSeat = 0,
            order = listOf(1, 0),
            seats = listOf(hero, villain),
            actions = listOf(Action(Street.PREFLOP, 0, ActionType.RAISE, 250, 250, false)),
            legal = LegalActions(0, 0, true, false, true, true, 100, 9700, 0, 0, 600),
            mode = GameMode.CASH,
        )
    }

    @Test
    fun turnEvEvaluatesHeadsUpUnrakedCashAndRejectsUnsupported() {
        val r = WeightedRange.of(listOf(WeightedRange.hand("AhQd", .7), WeightedRange.hand("7h7s", .3)))
        val res = TurnEv.analyze(turnView(facing = true), r)
        assertTrue(res.supported)
        assertNotNull(res.rangeEquity)
        assertTrue(res.values.any { it.move.type == MoveType.CALL })
        assertTrue(res.values.any { it.move.type == MoveType.RAISE })

        assertFalse(TurnEv.analyze(turnView().copy(mode = GameMode.TOURNAMENT), r).supported)
        assertFalse(TurnEv.analyze(turnView().copy(cashRules = CashRules(rakeBasisPoints = 500, rakeCapBB = 3)), r).supported)
    }

    @Test
    fun flopCbetClassifiesDryVsWetBoardAndGeneratesDrills() {
        val r = WeightedRange.of(listOf(WeightedRange.hand("KdQd", .5), WeightedRange.hand("8c7c", .5)))
        val dry = TurnEv.analyzeFlopCbet(flopCbetView("Ac7d2s"), r)
        val wet = TurnEv.analyzeFlopCbet(flopCbetView("JhTh9s"), r)
        assertTrue(dry.supported)
        assertTrue(wet.supported)
        assertTrue(dry.assumptions.any { it.contains("Board texture: dry") })
        assertTrue(wet.assumptions.any { it.contains("Board texture: wet") })

        val analysis = HandAnalysis(
            handId = 99L,
            algorithmVersion = "exploit-0.3.0",
            randomVersion = StrategyRandom.VERSION,
            sessionSeed = 7L,
            mode = AiMode.LEGACY,
            decisions = listOf(
                DecisionTrace(
                    actionIndex = 1,
                    seat = 0,
                    street = Street.FLOP,
                    contextHash = "a".repeat(64),
                    baseline = PolicySnapshot(listOf(PolicyChoice(Move.CHECK, 1.0)), .5, 0.0, 600),
                    exploit = PolicySnapshot(listOf(PolicyChoice(Move.CHECK, 1.0)), .5, 0.0, 600),
                    status = AdviceStatus.INSUFFICIENT_EVIDENCE,
                    evidence = emptyList(),
                    opponents = emptyList(),
                    actionValues = dry.values,
                    notes = dry.assumptions,
                ),
                DecisionTrace(
                    actionIndex = 2,
                    seat = 0,
                    street = Street.FLOP,
                    contextHash = "b".repeat(64),
                    baseline = PolicySnapshot(listOf(PolicyChoice(Move.CHECK, 1.0)), .5, 0.0, 600),
                    exploit = PolicySnapshot(listOf(PolicyChoice(Move.CHECK, 1.0)), .5, 0.0, 600),
                    status = AdviceStatus.INSUFFICIENT_EVIDENCE,
                    evidence = emptyList(),
                    opponents = emptyList(),
                    actionValues = wet.values,
                    notes = wet.assumptions,
                ),
                DecisionTrace(
                    actionIndex = 3,
                    seat = 0,
                    street = Street.TURN,
                    contextHash = "c".repeat(64),
                    baseline = PolicySnapshot(listOf(PolicyChoice(Move.CHECK, 1.0)), .5, 0.0, 600),
                    exploit = PolicySnapshot(listOf(PolicyChoice(Move.CHECK, 1.0)), .5, 0.0, 600),
                    status = AdviceStatus.INSUFFICIENT_EVIDENCE,
                    evidence = emptyList(),
                    opponents = emptyList(),
                    actionValues = TurnEv.analyze(turnView(), r).values,
                ),
            ),
        )
        val drills = TrainingCurriculum.fromHistory(listOf(analysis))
        assertEquals(listOf("flop-cbet-dry", "flop-cbet-wet", "turn-model"), drills.map { it.category })
    }

    @Test
    fun liveCaptureStoresTurnValuesAndRoundTripsCodec() {
        val session = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO, seed = 42,
            edition = EditionConfig(AiMode.LEGACY)))
        val engine = session.startHand()
        while (engine.phase != Phase.HAND_DONE) {
            if (engine.phase == Phase.STREET_DONE) { engine.dealNextStreet(); continue }
            if (engine.street == Street.TURN) session.strategy.capture(engine)
            session.applyMove(if (engine.legalActions().canCheck) Move.CHECK else Move.CALL)
        }
        session.completeHand(engine)
        val analysis = assertNotNull(session.lastAnalysis)
        assertTrue(analysis.decisions.any { it.street == Street.TURN && it.actionValues.isNotEmpty() })
        assertEquals(analysis, TraceCodec.decode(TraceCodec.encode(analysis)))
    }
}
