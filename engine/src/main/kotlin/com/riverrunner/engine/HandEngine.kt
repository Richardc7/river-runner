package com.riverrunner.engine

import kotlin.random.Random

interface CardSource {
    fun hole(seat: Int): IntArray
    fun board(index: Int): Int
}
class RandomCardSource(random: Random) : CardSource {
    private val deck = Deck(random)
    private val holes = HashMap<Int, IntArray>()
    private val boardCards = ArrayList<Int>()
    override fun hole(seat: Int): IntArray = holes.getOrPut(seat) { intArrayOf(deck.draw(), deck.draw()) }
    override fun board(index: Int): Int {
        while (boardCards.size <= index) boardCards += deck.draw()
        return boardCards[index]
    }
}
class PresetCardSource(private val holes: Map<Int, IntArray>, private val boardCards: IntArray) : CardSource {
    override fun hole(seat: Int): IntArray = holes.getValue(seat)
    override fun board(index: Int): Int = boardCards[index]
}
data class PlayerState(val seat: Int, val name: String, val startStack: Int, val stack: Int,
    val hole: IntArray, val folded: Boolean, val allIn: Boolean, val streetBet: Int, val committed: Int)
enum class Phase { BETTING, STREET_DONE, HAND_DONE }

class HandEngine(val setup: HandSetup, private val cards: CardSource) {
    private class P(val seat: Int, val name: String, val startStack: Int, var stack: Int, val hole: IntArray) {
        var folded = false; var allIn = false; var streetBet = 0; var committed = 0
        var hasActed = false; var actedAtRaise = -1
    }
    private val ps: List<P>
    val order: List<Int>
    val smallBlindSeat: Int
    val bigBlindSeat: Int
    val bigBlind = setup.bigBlind
    var street: Street = Street.PREFLOP; private set
    var phase: Phase = Phase.BETTING; private set
    var toAct: Int = -1; private set
    var result: HandResult? = null; private set
    private val _actions = ArrayList<Action>()
    val actions: List<Action> get() = _actions
    private val _board = ArrayList<Int>()
    val board: List<Int> get() = _board
    private var currentBet = 0
    private var lastRaiseSize = 0
    private var raiseCounter = 0
    var lastAggressor: Int = -1; private set
    var straddleSeat: Int? = null; private set
    val openingBlind: Int get() = if (straddleSeat != null) setup.bigBlind * 2 else setup.bigBlind
    val closingBlindSeat: Int get() = straddleSeat ?: bigBlindSeat
    private fun preflopOrder(): List<Int> {
        val first = if (straddleSeat != null) (order.indexOf(straddleSeat) + 1) % order.size
            else if (order.size == 2) order.indexOf(smallBlindSeat) else 2
        return List(order.size) { order[(first + it) % order.size] }
    }

    init {
        require(setup.seats.size in 2..9) { "Need at least 2 players" }
        require(setup.bigBlind in 1..1000000 && setup.smallBlind in 1..setup.bigBlind && setup.ante >= 0)
        require(setup.seats.map { it.seat }.distinct().size == setup.seats.size)
        require(setup.seats.all { it.seat in 0..8 && it.stack in 1..100000000 })
        require(setup.seats.sumOf { it.stack.toLong() } <= 1000000000L)
        val sorted = setup.seats.sortedBy { it.seat }
        val seatsInOrder = sorted.map { it.seat }
        val btnIdx = seatsInOrder.indexOf(setup.buttonSeat)
        require(btnIdx >= 0) { "Button must be a dealt-in seat" }
        order = List(sorted.size) { seatsInOrder[(btnIdx + 1 + it) % sorted.size] }
        ps = order.map { s -> val ss = sorted.first { it.seat == s }; P(s, ss.name, ss.stack, ss.stack, cards.hole(s)) }
        if (ps.size == 2) { smallBlindSeat = setup.buttonSeat; bigBlindSeat = order[0] }
        else { smallBlindSeat = order[0]; bigBlindSeat = order[1] }
        if (setup.ante > 0) for (p in ps) post(p, setup.ante, ActionType.ANTE)
        post(p(smallBlindSeat), setup.smallBlind, ActionType.SMALL_BLIND)
        post(p(bigBlindSeat), setup.bigBlind, ActionType.BIG_BLIND)
        if (setup.cashRules.utgStraddle && ps.size >= 3) {
            val candidate = p(order[2])
            if (candidate.stack >= 2 * setup.bigBlind) {
                straddleSeat = candidate.seat
                post(candidate, 2 * setup.bigBlind, ActionType.STRADDLE)
            }
        }
        currentBet = ps.maxOf { it.streetBet }
        lastRaiseSize = openingBlind
        raiseCounter = 0
        val first = preflopOrder().first()
        startBettingFrom(first, inclusive = true)
    }
    private fun p(seat: Int): P = ps.first { it.seat == seat }
    private fun post(p: P, amount: Int, type: ActionType) {
        val a = minOf(amount, p.stack)
        p.stack -= a; p.committed += a
        if (type != ActionType.ANTE) p.streetBet += a
        if (p.stack == 0) p.allIn = true
        _actions += Action(Street.PREFLOP, p.seat, type, a, p.streetBet, p.allIn)
    }
    val players: List<PlayerState> get() = ps.map { PlayerState(it.seat, it.name, it.startStack, it.stack, it.hole, it.folded, it.allIn, it.streetBet, it.committed) }
    fun player(seat: Int): PlayerState = players.first { it.seat == seat }
    val pot: Int get() = ps.sumOf { it.committed }
    val buttonSeat: Int get() = setup.buttonSeat
    val activeCount: Int get() = ps.count { !it.folded }
    fun legalActions(): LegalActions {
        check(phase == Phase.BETTING) { "Not in betting phase" }
        val p = p(toAct)
        val toCall = minOf(currentBet - p.streetBet, p.stack)
        val othersCanRespond = ps.any { it !== p && !it.folded && !it.allIn }
        val reopened = !p.hasActed || p.actedAtRaise < raiseCounter
        val canRaise = p.stack > toCall && reopened && othersCanRespond
        val maxTo = p.streetBet + p.stack
        var minTo = if (currentBet == 0) setup.bigBlind else currentBet + lastRaiseSize
        if (minTo > maxTo) minTo = maxTo
        return LegalActions(p.seat, toCall, toCall == 0, toCall > 0, canRaise, currentBet == 0,
            minTo, maxTo, currentBet, p.streetBet, pot)
    }
    fun apply(move: Move): Action {
        check(phase == Phase.BETTING) { "Not in betting phase" }
        val la = legalActions(); val p = p(toAct)
        val action: Action = when (move.type) {
            MoveType.FOLD -> {
                if (la.canCheck) return apply(Move.CHECK)
                p.folded = true
                Action(street, p.seat, ActionType.FOLD, 0, p.streetBet, false)
            }
            MoveType.CHECK -> {
                if (!la.canCheck) return apply(Move.CALL)
                Action(street, p.seat, ActionType.CHECK, 0, p.streetBet, false)
            }
            MoveType.CALL -> {
                if (la.canCheck) return apply(Move.CHECK)
                val a = la.toCall
                p.stack -= a; p.committed += a; p.streetBet += a
                if (p.stack == 0) p.allIn = true
                Action(street, p.seat, ActionType.CALL, a, p.streetBet, p.allIn)
            }
            MoveType.RAISE -> {
                if (!la.canRaise) return apply(if (la.canCheck) Move.CHECK else Move.CALL)
                val target = move.raiseTo.coerceIn(la.minRaiseTo, la.maxRaiseTo)
                val add = target - p.streetBet
                p.stack -= add; p.committed += add; p.streetBet = target
                if (p.stack == 0) p.allIn = true
                val raiseSize = target - currentBet
                val type = if (currentBet == 0) ActionType.BET else ActionType.RAISE
                if (raiseSize >= lastRaiseSize) { lastRaiseSize = raiseSize; raiseCounter++ }
                currentBet = target; lastAggressor = p.seat
                Action(street, p.seat, type, add, p.streetBet, p.allIn)
            }
        }
        p.hasActed = true; p.actedAtRaise = raiseCounter
        _actions += action
        afterAction(p.seat)
        return action
    }
    private fun needsToAct(p: P): Boolean = !p.folded && !p.allIn && (!p.hasActed || p.streetBet < currentBet)
    private fun afterAction(lastSeat: Int) {
        if (ps.count { !it.folded } == 1) { finishHand(); return }
        val idx = order.indexOf(lastSeat)
        for (k in 1..ps.size) {
            val s = order[(idx + k) % ps.size]
            if (needsToAct(p(s))) { toAct = s; return }
        }
        toAct = -1
        if (street == Street.RIVER) finishHand() else phase = Phase.STREET_DONE
    }
    val isRunout: Boolean get() = ps.count { !it.folded && !it.allIn } <= 1
    fun dealNextStreet() {
        check(phase == Phase.STREET_DONE) { "Street not complete" }
        street = Street.entries[street.ordinal + 1]
        while (_board.size < street.boardCount) _board += cards.board(_board.size)
        for (p in ps) { p.streetBet = 0; p.hasActed = false; p.actedAtRaise = -1 }
        currentBet = 0; lastRaiseSize = setup.bigBlind; raiseCounter = 0; lastAggressor = -1; phase = Phase.BETTING
        if (isRunout) {
            toAct = -1
            if (street == Street.RIVER) finishHand() else phase = Phase.STREET_DONE
            return
        }
        startBettingFrom(order[0], inclusive = true)
    }
    private fun startBettingFrom(seat: Int, inclusive: Boolean) {
        val idx = order.indexOf(seat); val startK = if (inclusive) 0 else 1
        for (k in startK until startK + ps.size) {
            val s = order[(idx + k) % ps.size]
            if (needsToAct(p(s))) { toAct = s; phase = Phase.BETTING; return }
        }
        toAct = -1
        if (ps.count { !it.folded } == 1) finishHand()
        else if (street == Street.RIVER) finishHand()
        else phase = Phase.STREET_DONE
    }
    private fun finishHand() {
        toAct = -1
        val live = ps.filter { !it.folded }
        val plan = PotSettlement.plan(ps.associate { it.seat to it.committed }, live.map { it.seat })
        val returned = plan.returned
        if (returned != null) {
            val player = p(returned.first)
            player.committed -= returned.second
            player.stack += returned.second
        }
        val scores = HashMap<Int, Int>()
        val showdown = if (live.size == 1) emptyList() else {
            while (_board.size < 5) _board += cards.board(_board.size)
            val boardMask = Cards.mask(_board.toIntArray())
            for (p in live) scores[p.seat] = HandEvaluator.evaluate(boardMask or Cards.mask(p.hole))
            live.map { it.seat }
        }
        val pots = RakeSettlement.apply(plan.pots, setup.cashRules, setup.bigBlind, _board.size >= 3)
            .map { it.copy(winners = PotSettlement.winners(it, scores)) }
        val rake = pots.sumOf { it.rake }
        val won = HashMap<Int, Int>()
        for ((index, pot) in pots.withIndex()) {
            val tail = if (rake == 0 && index == pots.lastIndex) plan.foldedTail else 0
            for ((winnerIndex, winner) in pot.winners.withIndex()) {
                val award = PotSettlement.award(pot.amount, pot.winners.size, winnerIndex, tail)
                won[winner] = (won[winner] ?: 0) + award
            }
        }
        for ((s, amt) in won) p(s).stack += amt
        val wonWithReturn = HashMap(won)
        if (returned != null) wonWithReturn[returned.first] = (wonWithReturn[returned.first] ?: 0) + returned.second
        val net = ps.associate { it.seat to (it.stack - it.startStack) }
        result = HandResult(wonWithReturn, net, pots, showdown, scores, _board.toList(), returned, rake)
        phase = Phase.HAND_DONE
    }
    fun positionName(seat: Int): String = positionName(order.indexOf(seat), ps.size)
    fun playersBehindPreflop(seat: Int): Int {
        val pre = preflopOrder()
        require(seat in pre)
        return pre.size - pre.indexOf(seat) - 1
    }
    companion object {
        fun positionName(idx: Int, n: Int): String {
            if (n == 2) return if (idx == 1) "BTN" else "BB"
            if (idx == 0) return "SB"
            if (idx == 1) return "BB"
            if (idx == n - 1) return "BTN"
            if (idx == 2) return "UTG"
            if (idx == n - 2) return "CO"
            if (idx == n - 3) return "HJ"
            return "UTG+${idx - 2}"
        }
    }
}
