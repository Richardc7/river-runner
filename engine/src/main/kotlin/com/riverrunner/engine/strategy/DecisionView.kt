package com.riverrunner.engine.strategy

import com.riverrunner.engine.*

/** Only public facts. No style, future cards, private deck, or another seat's hole cards. */
data class PublicSeat(
    val seat: Int, val startStack: Int, val stack: Int, val streetBet: Int,
    val committed: Int, val folded: Boolean, val allIn: Boolean,
)

data class DecisionView(
    val actorSeat: Int,
    val ownHole: List<Int>,
    val street: Street,
    val board: List<Int>,
    val pot: Int,
    val bigBlind: Int,
    val buttonSeat: Int,
    val order: List<Int>,
    val seats: List<PublicSeat>,
    val actions: List<Action>,
    val legal: LegalActions,
    val mode: GameMode,
) {
    init {
        require(ownHole.size == 2 && (ownHole + board).all { it in 0..51 })
        require((ownHole + board).distinct().size == ownHole.size + board.size)
        require(board.size == street.boardCount && pot >= 0 && bigBlind > 0)
        require(legal.seat == actorSeat && seats.any { it.seat == actorSeat })
    }
    val actor: PublicSeat get() = seats.first { it.seat == actorSeat }
    val opponents: List<PublicSeat> get() = seats.filter { it.seat != actorSeat && !it.folded }
    val actionIndex: Int get() = actions.count { !it.type.isPost }
    val inPosition: Boolean get() = opponents.filter { !it.allIn }.all { order.indexOf(it.seat) < order.indexOf(actorSeat) }
}

object ObservationFactory {
    fun forActor(engine: HandEngine, mode: GameMode): DecisionView {
        val seat = engine.toAct
        require(engine.phase == Phase.BETTING && seat >= 0)
        return DecisionView(
            seat, engine.player(seat).hole.toList(), engine.street, engine.board.toList(),
            engine.pot, engine.bigBlind, engine.buttonSeat, engine.order.toList(),
            engine.players.map { PublicSeat(it.seat, it.startStack, it.stack, it.streetBet, it.committed, it.folded, it.allIn) },
            engine.actions.toList(), engine.legalActions().copy(), mode,
        )
    }
}

/** Bet fraction uses NEW raise money over the pot after calling, not raise-to / pot. */
object BetSizing {
    fun fraction(legal: LegalActions, move: Move): Double {
        require(move.type == MoveType.RAISE)
        val raiseBy = move.raiseTo - legal.myStreetBet - legal.toCall
        return raiseBy.coerceAtLeast(0).toDouble() / (legal.pot.toLong() + legal.toCall).coerceAtLeast(1L)
    }
    fun bucket(fraction: Double): SizeClass {
        require(fraction.isFinite() && fraction >= 0)
        return when { fraction < .42 -> SizeClass.SMALL; fraction < .62 -> SizeClass.MEDIUM
            fraction < .87 -> SizeClass.LARGE; fraction < 1.2 -> SizeClass.POT; else -> SizeClass.OVER }
    }
    fun target(legal: LegalActions, fraction: Double): Move {
        require(legal.canRaise && fraction.isFinite() && fraction > 0)
        val wanted = legal.currentBet.toLong() + ((legal.pot.toLong() + legal.toCall) * fraction).toLong()
        return Move.raiseTo(wanted.coerceIn(legal.minRaiseTo.toLong(), legal.maxRaiseTo.toLong()).toInt())
    }
}
