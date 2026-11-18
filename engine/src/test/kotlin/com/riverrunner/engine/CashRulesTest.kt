package com.riverrunner.engine

import com.riverrunner.engine.strategy.*
import kotlin.random.Random
import kotlin.test.*

class CashRulesTest {
    private fun engine(stacks: List<Int>, rules: CashRules = CashRules(), ante: Int = 0): HandEngine {
        val holes = listOf("AsAh", "KsKh", "QsQh", "JsJh", "TsTh", "9s9h", "8s8h", "7s7h", "6s6h")
        return HandEngine(HandSetup(50, 100, ante, 0, stacks.mapIndexed { i, s -> SeatSetup(i, "P$i", s) }, rules),
            PresetCardSource(stacks.indices.associateWith { Cards.parseMany(holes[it]) }, Cards.parseMany("2c3d4c5d9c")))
    }
    private fun checkDown(e: HandEngine) {
        var steps = 0
        while (e.phase != Phase.HAND_DONE) {
            if (e.phase == Phase.STREET_DONE) e.dealNextStreet()
            else { e.apply(if (e.legalActions().canCheck) Move.CHECK else Move.CALL); assertTrue(++steps < 500) }
        }
    }
    @Test fun straddleChangesOrderAndMinimumRaise() {
        val e = engine(List(4) { 10000 }, CashRules(utgStraddle = true))
        assertEquals(3, e.straddleSeat)
        assertEquals(0, e.toAct)
        assertEquals(200, e.legalActions().toCall)
        assertEquals(400, e.legalActions().minRaiseTo)
        assertEquals(350, e.pot)
        assertEquals(ActionType.STRADDLE, e.actions.last().type)
        assertEquals(0, e.playersBehindPreflop(3))
        e.apply(Move.CALL); e.apply(Move.CALL); e.apply(Move.CALL)
        assertEquals(3, e.toAct); assertTrue(e.legalActions().canCheck)
        e.apply(Move.CHECK); e.dealNextStreet()
        assertEquals(1, e.toAct); assertEquals(100, e.legalActions().minRaiseTo)
    }
    @Test fun noHeadsUpOrUnderfundedStraddle() {
        assertNull(engine(listOf(1000, 1000), CashRules(utgStraddle = true)).straddleSeat)
        val e = engine(listOf(1000, 1000, 1000, 210), CashRules(utgStraddle = true), ante = 20)
        assertNull(e.straddleSeat); assertEquals(3, e.toAct)
    }
    @Test fun rakeCapIsOncePerHandAcrossSidePots() {
        val e = engine(listOf(1000, 2000, 3000), CashRules(rakeBasisPoints = 500, rakeCapBB = 2))
        while (e.phase != Phase.HAND_DONE) {
            if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
            val la = e.legalActions(); e.apply(if (la.canRaise) Move.raiseTo(la.maxRaiseTo) else Move.CALL)
        }
        val result = assertNotNull(e.result)
        assertEquals(200, result.rake)
        assertEquals(6000, e.players.sumOf { it.stack } + result.rake)
        assertEquals(result.rake, result.pots.sumOf { it.rake })
        assertTrue(result.pots.all { it.amount >= 0 && it.rake >= 0 })
        assertNull(result.uncalledReturned)
        assertEquals(5000, result.pots.sumOf { it.amount + it.rake })
    }
    @Test fun noFlopNoDropAndUncalledMoneyExcluded() {
        val rules = CashRules(rakeBasisPoints = 1000, rakeCapBB = 100)
        val noFlop = engine(listOf(10000, 10000, 10000), rules)
        noFlop.apply(Move.raiseTo(5000)); noFlop.apply(Move.FOLD); noFlop.apply(Move.FOLD)
        assertEquals(0, noFlop.result!!.rake)
        val charge = engine(listOf(10000, 10000, 10000), rules.copy(noFlopNoDrop = false))
        charge.apply(Move.raiseTo(5000)); charge.apply(Move.FOLD); charge.apply(Move.FOLD)
        assertEquals(4900, charge.result!!.uncalledReturned!!.second)
        assertEquals(25, charge.result!!.rake)
        assertEquals(30000, charge.players.sumOf { it.stack } + charge.result!!.rake)
    }
    @Test fun largestRemainderRakeConservesEveryChip() {
        val pots = listOf(Pot(101, listOf(0, 1), listOf(0, 1)), Pot(202, listOf(1, 2), listOf(2)))
        val net = RakeSettlement.apply(pots, CashRules(rakeBasisPoints = 500, rakeCapBB = 10), 100, true)
        assertEquals(15, net.sumOf { it.rake })
        assertEquals(303, net.sumOf { it.amount + it.rake })
        assertEquals(listOf(5, 10), net.map { it.rake })
    }
    @Test fun h1AndH2RoundTripWithExactReplaySettlement() {
        for (rules in listOf(CashRules(), CashRules(true, 500, 3))) {
            val e = engine(List(4) { 10000 }, rules); checkDown(e)
            val record = HandRecord.from(e, GameMode.CASH, 1, 0, 4, emptyMap())
            val line = record.encode()
            assertTrue(line.startsWith(if (rules.isDefault) "H1|" else "H2|"))
            val back = assertNotNull(HandRecord.decode(line)); assertEquals(line, back.encode())
            assertEquals(rules, back.cashRules); assertEquals(e.result!!.rake, back.rake)
            assertEquals(e.players.associate { it.seat to it.stack }, Replayer(back).frames.last().players.associate { it.seat to it.stack })
            val view = ObservationFactory.forActor(Replayer(back).engineBeforeAction(0), back.mode)
            assertEquals(rules, view.cashRules)
        }
        assertNull(HandRecord.decode("H99|invalid"))
    }
    @Test fun mixedStacksAndRebuyLedgerDoNotInventProfit() {
        val s = GameSession(SessionConfig(GameMode.CASH, 3, 100, TableType.GTO, seed = 10,
            edition = EditionConfig(seatStackBB = mapOf(0 to 50, 1 to 200))))
        assertEquals(listOf(5000, 20000, 10000), s.players.map { it.stack })
        assertEquals(5000, s.rebuy(0, 100))
        assertEquals(0, s.rebuy(0, 100))
        assertEquals(10000, s.paidIn(0))
        assertEquals(0.0, s.finish(true).heroProfit, 1e-12)
        assertEquals(1, s.buyIns.size)
    }
    @Test fun noMidHandOrTournamentRebuy() {
        val cash = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO)); cash.startHand()
        assertFailsWith<IllegalStateException> { cash.rebuy(0, 200) }
        val tournament = GameSession(SessionConfig(GameMode.TOURNAMENT, 3, 100, TableType.GTO,
            edition = EditionConfig(cashRules = CashRules(true, 500, 3), seatStackBB = mapOf(0 to 500), automaticRebuys = 2)))
        assertEquals(10000, tournament.hero.stack)
        assertTrue(tournament.startHand().setup.cashRules.isDefault)
        assertFailsWith<IllegalStateException> { tournament.rebuy(0, 200) }
    }
    @Test fun automaticRebuyOccursOnlyAtNextHandAndIsBounded() {
        val s = GameSession(SessionConfig(GameMode.CASH, 2, 10, TableType.GTO, seed = 2,
            edition = EditionConfig(automaticRebuys = 1)))
        s.players[0].stack = 0; s.players[0].place = 2; s.players[1].stack = 2000
        assertFalse(s.isOver); assertEquals(1000, s.paidIn(0))
        val e = s.startHand(); assertEquals(2000, s.paidIn(0)); assertEquals(1, s.buyIns.size)
        while (e.phase != Phase.HAND_DONE) {
            if (e.phase == Phase.STREET_DONE) e.dealNextStreet() else s.applyMove(if (e.legalActions().canCheck) Move.CHECK else Move.CALL)
        }
        s.completeHand(e)
        val sum = s.players.sumOf { it.stack }; s.players[0].stack = 0; s.players[0].place = 2; s.players[1].stack = sum
        assertTrue(s.isOver)
    }
    @Test fun randomizedCashRulesConserveChipsIncludingRake() {
        val rng = Random(301)
        repeat(300) { n ->
            val count = 2 + rng.nextInt(7); val stacks = List(count) { 150 + rng.nextInt(10000) }
            val e = HandEngine(HandSetup(50, 100, if (n % 3 == 0) 10 else 0, n % count,
                stacks.mapIndexed { i, st -> SeatSetup(i, "P$i", st) }, CashRules(n % 2 == 0, 500, 3, n % 4 != 0)), RandomCardSource(rng))
            var steps = 0
            while (e.phase != Phase.HAND_DONE) {
                if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
                val la = e.legalActions()
                val move = when (rng.nextInt(4)) {
                    0 -> if (la.canCheck) Move.CHECK else Move.FOLD
                    1 -> if (la.canCheck) Move.CHECK else Move.CALL
                    else -> if (la.canRaise) Move.raiseTo(la.minRaiseTo + rng.nextInt(la.maxRaiseTo - la.minRaiseTo + 1)) else if (la.canCheck) Move.CHECK else Move.CALL
                }
                e.apply(move); assertTrue(++steps < 500)
            }
            assertEquals(stacks.sum(), e.players.sumOf { it.stack } + e.result!!.rake)
            assertTrue(e.players.all { it.stack >= 0 })
            assertTrue(e.result!!.rake <= 300)
        }
    }
    @Test fun invalidCashSettingsRejected() {
        assertFailsWith<IllegalArgumentException> { CashRules(rakeBasisPoints = 500) }
        assertFailsWith<IllegalArgumentException> { CashRules(rakeBasisPoints = -1) }
        assertFailsWith<IllegalArgumentException> { EditionConfig(automaticRebuys = 21) }
        assertFailsWith<IllegalArgumentException> { EditionConfig(seatStackBB = mapOf(0 to -1)) }
    }
}
