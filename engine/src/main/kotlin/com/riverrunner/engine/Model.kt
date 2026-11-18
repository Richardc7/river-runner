package com.riverrunner.engine
enum class Street { PREFLOP, FLOP, TURN, RIVER;
    val boardCount: Int get() = when (this) { PREFLOP -> 0; FLOP -> 3; TURN -> 4; RIVER -> 5 }
}
enum class ActionType { ANTE, SMALL_BLIND, BIG_BLIND, FOLD, CHECK, CALL, BET, RAISE, STRADDLE;
    val isPost: Boolean get() = this == ANTE || this == SMALL_BLIND || this == BIG_BLIND || this == STRADDLE
    val isAggressive: Boolean get() = this == BET || this == RAISE
}
data class Action(val street: Street, val seat: Int, val type: ActionType, val amount: Int, val streetTotal: Int, val allIn: Boolean)
enum class SizeClass(val center: Double) {
    SMALL(0.33), MEDIUM(0.5), LARGE(0.75), POT(1.0), OVER(1.5);
    companion object {
        fun of(chips: Int, potBefore: Int): SizeClass {
            val r = chips.toDouble() / kotlin.math.max(1, potBefore)
            return when { r < 0.42 -> SMALL; r < 0.62 -> MEDIUM; r < 0.87 -> LARGE; r < 1.2 -> POT; else -> OVER }
        }
    }
}
enum class MoveType { FOLD, CHECK, CALL, RAISE }
/** raiseTo is the player's street total, not the additional amount. */
data class Move(val type: MoveType, val raiseTo: Int = 0) {
    companion object {
        val FOLD = Move(MoveType.FOLD); val CHECK = Move(MoveType.CHECK); val CALL = Move(MoveType.CALL)
        fun raiseTo(amount: Int) = Move(MoveType.RAISE, amount)
    }
}
data class LegalActions(
    val seat: Int, val toCall: Int, val canCheck: Boolean, val canCall: Boolean, val canRaise: Boolean,
    val isOpeningBet: Boolean, val minRaiseTo: Int, val maxRaiseTo: Int, val currentBet: Int,
    val myStreetBet: Int, val pot: Int,
)
data class SeatSetup(val seat: Int, val name: String, val stack: Int)
data class HandSetup(val smallBlind: Int, val bigBlind: Int, val ante: Int, val buttonSeat: Int, val seats: List<SeatSetup>, val cashRules: CashRules = CashRules())
data class Pot(val amount: Int, val eligible: List<Int>, val winners: List<Int>, val rake: Int = 0)
data class HandResult(
    val won: Map<Int, Int>, val net: Map<Int, Int>, val pots: List<Pot>, val showdown: List<Int>,
    val scores: Map<Int, Int>, val board: List<Int>, val uncalledReturned: Pair<Int, Int>?, val rake: Int = 0,
)
