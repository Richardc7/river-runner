package com.riverrunner.engine

import com.riverrunner.engine.strategy.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class StrategyTraceTest {
    private fun captured(): Pair<HandRecord, HandAnalysis> {
        val s = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO, seed = 41,
            edition = EditionConfig(AiMode.ADAPTIVE)))
        val e = s.startHand()
        while (e.phase != Phase.HAND_DONE) {
            if (e.phase == Phase.STREET_DONE) { e.dealNextStreet(); continue }
            if (e.toAct == 0) s.strategy.capture(e)
            val la = e.legalActions(); s.applyMove(if (la.canCheck) Move.CHECK else Move.CALL)
        }
        val record = s.completeHand(e)
        return record to assertNotNull(s.lastAnalysis)
    }
    @Test fun traceRoundTripKeepsDecisionTimeEvidence() {
        val (record, trace) = captured()
        val restored = TraceCodec.decode(TraceCodec.encode(trace))
        assertEquals(trace, restored)
        val first = trace.decisions.first()
        assertEquals(0, first.opponents.first { it.seat == 0 }.stat(StatKind.VPIP).opportunities)
        val e = Replayer(record).engineBeforeAction(first.actionIndex)
        assertEquals(first.contextHash, DecisionFingerprint.of(ObservationFactory.forActor(e, record.mode)))
        assertTrue(trace.decisions.size >= 4)
    }
    @Test fun unknownSchemaCorruptionAndTruncationRejected() {
        val bytes = TraceCodec.encode(captured().second)
        val changed = bytes.copyOf(); changed[changed.lastIndex] = (changed.last().toInt() xor 1).toByte()
        assertFailsWith<IllegalArgumentException> { TraceCodec.decode(changed) }
        val future = bytes.copyOf(); future[7] = 2
        assertFailsWith<IllegalArgumentException> { TraceCodec.decode(future) }
        assertFailsWith<IllegalArgumentException> { TraceCodec.decode(bytes.copyOf(bytes.size - 1)) }
    }
    @Test fun sidecarLifecycleDoesNotChangeHandHistory() {
        val dir = Files.createTempDirectory("rr-trace-").toFile()
        try {
            val (record, trace) = captured(); val store = StrategyTraceStore(dir, "p1")
            val handLine = record.encode(); store.save(trace)
            assertEquals(trace, store.load(record.id))
            store.save(trace.copy(handId = record.id + 1))
            store.retain(setOf(record.id))
            assertNull(store.load(record.id + 1)); assertEquals(trace, store.load(record.id))
            assertEquals(handLine, record.encode())
            assertNotNull(HandRecord.decode(handLine))
            File(dir, "analysis_p1/${record.id}.rrt").writeText("broken")
            assertNull(store.load(record.id))
            store.deleteAll(); assertFalse(File(dir, "analysis_p1").exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun recordAdviceCanBeDisabled() {
        val s = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO,
            edition = EditionConfig(recordAdvice = false)))
        s.strategy.capture(s.startHand()); assertNull(s.strategy.finishAnalysis(1))
    }
    @Test fun pathTraversalIsRejected() {
        assertFailsWith<IllegalArgumentException> { StrategyTraceStore(File("."), "../other") }
    }
}
