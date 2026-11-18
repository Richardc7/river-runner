package com.riverrunner.engine.strategy

import kotlin.random.Random

/** Versioned SplitMix64: simulation randomness, not a cryptographic generator. */
class StrategyRandom(seed: Long) : Random() {
    private var state = seed
    override fun nextBits(bitCount: Int): Int {
        require(bitCount in 0..32)
        if (bitCount == 0) return 0
        state += -7046029254386353131L
        var z = state
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        z = z xor (z ushr 31)
        return (z ushr (64 - bitCount)).toInt()
    }
    companion object {
        const val VERSION = "splitmix64-v1"
        fun forDecision(seed: Long, hand: Int, action: Int, seat: Int, purpose: Int): StrategyRandom {
            var mixed = seed
            for (value in listOf(hand, action, seat, purpose)) {
                mixed = (mixed xor value.toLong()) * -7046029254386353131L
                mixed = mixed xor (mixed ushr 29)
            }
            return StrategyRandom(mixed)
        }
    }
}
