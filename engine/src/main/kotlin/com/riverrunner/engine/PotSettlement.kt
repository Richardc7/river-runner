package com.riverrunner.engine

/** Shared settlement rules for real hands and hypothetical terminal decisions. */
internal object PotSettlement {
    data class Plan(val pots: List<Pot>, val returned: Pair<Int, Int>?, val foldedTail: Int = 0)

    /** [live] is in odd-chip payout order. Folded contributions add chips, not eligibility tiers. */
    fun plan(contributions: Map<Int, Int>, live: List<Int>): Plan {
        require(live.isNotEmpty() && live.all { it in contributions })
        require(contributions.values.all { it >= 0 } &&
            contributions.values.sumOf { it.toLong() } <= Int.MAX_VALUE)
        val remaining = contributions.toMutableMap()
        val top = remaining.entries.sortedByDescending { it.value }
        val returned = if (top.size >= 2 && top[0].value > top[1].value) {
            (top[0].key to (top[0].value - top[1].value)).also {
                remaining[it.first] = remaining.getValue(it.first) - it.second
            }
        } else null
        if (live.size == 1) return Plan(listOf(Pot(remaining.values.sum(), live, emptyList())), returned)

        val pots = ArrayList<Pot>()
        var tail = 0
        while (remaining.values.any { it > 0 }) {
            val eligible = live.filter { remaining.getValue(it) > 0 }
            if (eligible.isEmpty()) {
                // Preserve the engine's legacy allocation of any residual folded-only chips.
                if (pots.isNotEmpty()) {
                    tail = remaining.values.sum()
                    pots[pots.lastIndex] = pots.last().let { it.copy(amount = it.amount + tail) }
                }
                break
            }
            val level = eligible.minOf { remaining.getValue(it) }
            var chips = 0
            for (entry in remaining.entries) {
                val take = minOf(entry.value, level)
                chips += take
                entry.setValue(entry.value - take)
            }
            pots += Pot(chips, eligible, emptyList())
        }
        return Plan(pots, returned, tail)
    }

    fun winners(pot: Pot, scores: Map<Int, Int>): List<Int> {
        if (pot.eligible.size == 1) return pot.eligible
        val best = pot.eligible.maxOf { scores.getValue(it) }
        return pot.eligible.filter { scores.getValue(it) == best }
    }

    fun award(amount: Int, winnerCount: Int, winnerIndex: Int, foldedTail: Int = 0): Int {
        if (winnerIndex < 0) return 0
        val base = amount - foldedTail
        return base / winnerCount + (if (winnerIndex < base % winnerCount) 1 else 0) +
            foldedTail / winnerCount + (if (winnerIndex == 0) foldedTail % winnerCount else 0)
    }
}
