package com.riverrunner.engine.strategy

import com.riverrunner.engine.*
import java.security.MessageDigest

data class PolicyChoice(val move: Move, val probability: Double) {
    init { require(probability.isFinite() && probability in 0.0..1.0) }
}
data class PolicySnapshot(val choices: List<PolicyChoice>, val equity: Double?, val potOdds: Double, val pot: Int) {
    init {
        require(choices.isNotEmpty() && choices.size <= 64 && choices.map { it.move }.distinct().size == choices.size)
        require(kotlin.math.abs(choices.sumOf { it.probability } - 1.0) < 1e-6)
        require(equity == null || equity.isFinite() && equity in 0.0..1.0)
        require(potOdds.isFinite() && potOdds in 0.0..1.0 && pot >= 0)
    }
    companion object {
        fun from(p: Policy) = PolicySnapshot(p.options.map { PolicyChoice(it.first.copy(), it.second) }, p.equity, p.potOdds, p.pot)
    }
}

/** Bounds describe model sensitivity, not statistical confidence or a verified solver loss. */
data class ActionValue(val move: Move, val chips: Double, val low: Double, val high: Double) {
    init { require(chips.isFinite() && low.isFinite() && high.isFinite() && low <= chips && chips <= high) }
}
data class DecisionTrace(
    val actionIndex: Int, val seat: Int, val street: Street, val contextHash: String,
    val baseline: PolicySnapshot, val exploit: PolicySnapshot, val status: AdviceStatus,
    val evidence: List<ReasonEvidence>, val opponents: List<OpponentSnapshot>,
    val actionValues: List<ActionValue> = emptyList(), val notes: List<String> = emptyList(),
) {
    init { require(actionIndex >= 0 && seat in 0..8 && contextHash.matches(Regex("[0-9a-f]{64}"))) }
}
data class HandAnalysis(
    val handId: Long, val algorithmVersion: String, val randomVersion: String,
    val sessionSeed: Long, val mode: AiMode, val decisions: List<DecisionTrace>,
) {
    init {
        require(handId > 0 && algorithmVersion.length in 1..100 && randomVersion.length in 1..100)
        require(decisions.size <= 256 && decisions.map { it.actionIndex }.distinct().size == decisions.size)
    }
}

object DecisionFingerprint {
    /** Version 1 deliberately serializes only the decision-time information set. */
    fun of(view: DecisionView): String {
        val text = buildString {
            append("decision-v1|").append(view.mode.name).append('|').append(view.actorSeat).append('|')
            append(view.ownHole.sorted().joinToString(",")).append('|').append(view.board.joinToString(",")).append('|')
            append(view.street.name).append('|').append(view.pot).append('|').append(view.bigBlind).append('|')
            append(view.buttonSeat).append('|').append(view.order.joinToString(",")).append('|')
            append(view.legal).append('|')
            view.seats.sortedBy { it.seat }.forEach { append(it).append(';') }
            append('|'); view.actions.forEach { append(it).append(';') }
        }
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
