package com.riverrunner

import com.riverrunner.engine.Move
import com.riverrunner.engine.strategy.ReferenceAction
import com.riverrunner.engine.strategy.ReferenceBook
import com.riverrunner.engine.strategy.ReferenceNode
import com.riverrunner.engine.strategy.ReferenceProvenance
import com.riverrunner.ui.StudyIndexEntry
import com.riverrunner.ui.StudyIndexStore
import com.riverrunner.ui.mergeReferenceBooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class StudyIndexAndBatchTest {
    private fun hash(ch: Char) = ch.toString().repeat(64)

    @Test
    fun studyIndexRoundTripsAndRetainsValidIds() {
        val dir = Files.createTempDirectory("rr-index-test").toFile()
        try {
            val store = StudyIndexStore(dir, "p1")
            store.upsert(StudyIndexEntry(1L, hasBlind = true, hasDeviation = false, hasDrill = true))
            store.upsert(StudyIndexEntry(2L, hasBlind = false, hasDeviation = true, hasDrill = false))
            val loaded = store.load()
            assertEquals(2, loaded.size)
            assertTrue(loaded.getValue(1L).hasBlind)
            assertTrue(loaded.getValue(2L).hasDeviation)

            store.retain(setOf(2L))
            val afterRetain = store.load()
            assertEquals(setOf(2L), afterRetain.keys)
            assertFalse(afterRetain.containsKey(1L))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun mergeReferenceBooksDeduplicatesAndOverridesByHash() {
        val prov1 = ReferenceProvenance("Solver A", "User", "MIT", "HU river")
        val prov2 = ReferenceProvenance("Solver B", "User", "MIT", "HU river batch")
        val n1 = ReferenceNode(hash('a'), listOf(ReferenceAction(Move.CHECK, 0.7), ReferenceAction(Move.raiseTo(500), 0.3)))
        val n1Updated = ReferenceNode(hash('a'), listOf(ReferenceAction(Move.CHECK, 0.4), ReferenceAction(Move.raiseTo(500), 0.6)))
        val n2 = ReferenceNode(hash('b'), listOf(ReferenceAction(Move.CALL, 1.0)))

        val merged = mergeReferenceBooks(
            existing = ReferenceBook(prov1, listOf(n1)),
            incoming = listOf(ReferenceBook(prov2, listOf(n1Updated, n2))),
        )
        assertEquals(2, merged.nodes.size)
        assertEquals("Solver B", merged.provenance.source)
        val updated = merged.nodes.first { it.contextHash == hash('a') }
        assertEquals(0.4, updated.actions.first { it.move == Move.CHECK }.frequency, 1e-9)
    }
}
