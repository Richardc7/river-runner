package com.riverrunner.engine.strategy

import kotlin.random.Random

/**
 * Draws a legal joint assignment from up to eight independently estimated marginal ranges.
 *
 * Sequentially re-normalizing each remaining range is a biased joint proposal. The product
 * of its conditional available masses is therefore returned as an importance weight.
 * Weighted aggregates estimate the product-of-marginals distribution conditioned on no
 * shared cards. This does not claim opponents' ranges are truly independent.
 */
data class JointRangeSample(val holes: Map<Int, Long>, val importanceWeight: Double)

class JointRangeSampler(private val ranges: Map<Int, WeightedRange>) {
    init {
        require(ranges.size in 1..8 && ranges.keys.all { it in 0..8 })
    }

    fun sample(dead: Long, random: Random): JointRangeSample? {
        val seats = ranges.keys.toList()
        val offset = random.nextInt(seats.size)
        val selected = LinkedHashMap<Int, Long>(seats.size)
        var unavailable = dead
        var importance = 1.0
        for (i in seats.indices) {
            val seat = seats[(i + offset) % seats.size]
            val range = ranges.getValue(seat)
            var availableMass = 0.0
            for (combo in range.combos) {
                if (combo.mask and unavailable == 0L) availableMass += combo.weight
            }
            if (availableMass <= 0.0 || !availableMass.isFinite()) return null
            var target = random.nextDouble() * availableMass
            var chosen = 0L
            var lastValid = 0L
            for (combo in range.combos) {
                if (combo.mask and unavailable != 0L) continue
                lastValid = combo.mask
                target -= combo.weight
                if (target <= 0.0) {
                    chosen = combo.mask
                    break
                }
            }
            if (chosen == 0L) chosen = lastValid // round-off at the edge of the CDF
            if (chosen == 0L) return null
            selected[seat] = chosen
            unavailable = unavailable or chosen
            importance *= availableMass
        }
        return JointRangeSample(selected, importance)
    }
}
