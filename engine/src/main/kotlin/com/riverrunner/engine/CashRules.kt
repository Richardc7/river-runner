package com.riverrunner.engine

/** Explicit house-rule variant for simulated cash games. Amounts remain integer chips. */
data class CashRules(
    val utgStraddle: Boolean = false,
    val rakeBasisPoints: Int = 0,
    val rakeCapBB: Int = 0,
    val noFlopNoDrop: Boolean = true,
) {
    init {
        require(rakeBasisPoints in 0..1000 && rakeCapBB in 0..100)
        require(rakeBasisPoints == 0 || rakeCapBB > 0) { "Nonzero rake requires a positive cap" }
    }
    val hasRake: Boolean get() = rakeBasisPoints > 0
    val isDefault: Boolean get() = this == CashRules()
}

/** Cap is per hand, not per side pot. Largest remainders allocate whole-chip deductions. */
object RakeSettlement {
    fun apply(pots: List<Pot>, rules: CashRules, bigBlind: Int, flopDealt: Boolean): List<Pot> {
        require(bigBlind > 0 && pots.all { it.amount >= 0 && it.rake == 0 })
        if (!rules.hasRake || (rules.noFlopNoDrop && !flopDealt)) return pots
        val gross = pots.sumOf { it.amount.toLong() }
        if (gross == 0L) return pots
        val total = minOf(gross * rules.rakeBasisPoints / 10000, rules.rakeCapBB.toLong() * bigBlind).toInt()
        if (total == 0) return pots
        val fees = pots.map { (it.amount.toLong() * total / gross).toInt() }.toMutableList()
        var remaining = total - fees.sum()
        val order = pots.indices.sortedWith(compareByDescending<Int> { pots[it].amount.toLong() * total % gross }.thenBy { it })
        for (i in order) if (remaining > 0 && fees[i] < pots[i].amount) { fees[i]++; remaining-- }
        check(remaining == 0)
        return pots.mapIndexed { i, p -> p.copy(amount = p.amount - fees[i], rake = fees[i]) }
    }
}

data class BuyInEvent(val seat: Int, val amount: Int, val afterHand: Int, val automatic: Boolean)
