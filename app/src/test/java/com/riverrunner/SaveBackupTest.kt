package com.riverrunner

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riverrunner.data.Profile
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.GameSession
import com.riverrunner.engine.Move
import com.riverrunner.engine.SessionConfig
import com.riverrunner.engine.TableType
import com.riverrunner.engine.strategy.CategoryProgress
import com.riverrunner.engine.strategy.ReferenceAction
import com.riverrunner.engine.strategy.ReferenceBook
import com.riverrunner.engine.strategy.ReferenceNode
import com.riverrunner.engine.strategy.ReferenceProvenance
import com.riverrunner.engine.strategy.ReviewMemory
import com.riverrunner.engine.strategy.ReviewProgress
import com.riverrunner.engine.strategy.TraceCodec
import com.riverrunner.engine.strategy.TrainingProgress
import com.riverrunner.ui.SaveBackup
import com.riverrunner.ui.SaveBackupCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SaveBackupTest {
    @Test fun roundTripsSlotDataAndDecisionEvidence() {
        val session = GameSession(SessionConfig(GameMode.CASH, 2, 100, TableType.GTO, seed = 7))
        val hand = session.startHand()
        session.strategy.capture(hand)
        session.applyMove(Move.FOLD)
        val record = session.completeHand(hand).copy(favorite = true, reviewed = true)
        val drillId = "h${record.id}a0"
        val original = SaveBackup(
            Profile("p1", "河牌练习", 1234L, 2, mapOf("hands" to 1L), mapOf("guide" to 5678L)),
            listOf(record), listOf(requireNotNull(session.lastAnalysis)),
            TrainingProgress(mapOf("river-model" to CategoryProgress(2, 1, 15.0)), drillId),
            ReviewProgress(mapOf(drillId to ReviewMemory(2, 1, 13L, .5)), drillId),
            ReferenceBook(
                ReferenceProvenance("Test", "Author", "MIT", "Context"),
                listOf(ReferenceNode("a".repeat(64), listOf(ReferenceAction(Move.FOLD, 1.0)))),
            ),
        )
        val bytes = ByteArrayOutputStream().also { SaveBackupCodec.write(original, it) }
        val restored = SaveBackupCodec.read(bytes.toByteArray().inputStream())
        assertEquals(original.profile, restored.profile)
        assertEquals(original.hands.single().encode(), restored.hands.single().encode())
        assertArrayEquals(TraceCodec.encode(original.analyses.single()), TraceCodec.encode(restored.analyses.single()))
        assertEquals(original.training, restored.training)
        assertEquals(original.review, restored.review)
        assertEquals(original.reference, restored.reference)
    }

    @Test fun rejectsArchivesWithUnexpectedOrMissingData() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use {
            it.putNextEntry(ZipEntry("unknown.txt"))
            it.write("Not a save".toByteArray())
        }
        assertTrue(runCatching { SaveBackupCodec.read(bytes.toByteArray().inputStream()) }.isFailure)
        assertTrue(runCatching { SaveBackupCodec.read(byteArrayOf().inputStream()) }.isFailure)
    }
}
