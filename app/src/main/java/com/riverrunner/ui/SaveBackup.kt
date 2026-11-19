package com.riverrunner.ui

import com.riverrunner.data.Profile
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.strategy.HandAnalysis
import com.riverrunner.engine.strategy.ReferenceBook
import com.riverrunner.engine.strategy.ReferenceCodec
import com.riverrunner.engine.strategy.ReviewProgress
import com.riverrunner.engine.strategy.TraceCodec
import com.riverrunner.engine.strategy.TrainingProgress
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class SaveBackup(
    val profile: Profile,
    val hands: List<HandRecord>,
    val analyses: List<HandAnalysis>,
    val training: TrainingProgress,
    val review: ReviewProgress,
    val reference: ReferenceBook?,
)

internal object SaveBackupCodec {
    private const val FORMAT = "River Runner save 1"
    private const val MAX_BYTES = 64 * 1024 * 1024
    private const val MAX_HANDS_BYTES = 16 * 1024 * 1024
    private const val MAX_METADATA_BYTES = 4 * 1024 * 1024
    private val tracePath = Regex("traces/([1-9][0-9]{0,18})\\.rrt")

    fun write(backup: SaveBackup, output: OutputStream) {
        ZipOutputStream(output).use { zip ->
            var total = 0L
            fun entry(name: String, bytes: ByteArray, limit: Int = MAX_METADATA_BYTES) {
                total += bytes.size
                require(total <= MAX_BYTES && bytes.size <= limit)
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            entry("format", FORMAT.toByteArray())
            entry("profile.json", backup.profile.toJson().toString().toByteArray())
            entry("hands.txt", backup.hands.joinToString("\n") { it.encode() }.toByteArray(), MAX_HANDS_BYTES)
            entry("training.json", encodeTrainingProgress(backup.training).toByteArray())
            entry("review.json", encodeSpacedReview(backup.review).toByteArray())
            backup.reference?.let { entry("reference.txt", ReferenceCodec.encode(it).toByteArray()) }
            for (analysis in backup.analyses) {
                entry("traces/${analysis.handId}.rrt", TraceCodec.encode(analysis), TraceCodec.MAX_BYTES + 20)
            }
            entry("complete", "${backup.hands.size}:${backup.analyses.size}:${backup.reference != null}".toByteArray())
        }
    }

    fun read(input: InputStream): SaveBackup {
        var profile: Profile? = null
        var hands: List<HandRecord>? = null
        var training: TrainingProgress? = null
        var review: ReviewProgress? = null
        var reference: ReferenceBook? = null
        var completion: String? = null
        val analyses = mutableListOf<HandAnalysis>()
        val names = mutableSetOf<String>()
        var total = 0
        val buffer = ByteArray(8192)
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(completion == null && !entry.isDirectory && names.add(entry.name))
                val trace = tracePath.matchEntire(entry.name)
                val limit = when (entry.name) {
                    "format" -> FORMAT.length
                    "complete" -> 64
                    "hands.txt" -> MAX_HANDS_BYTES
                    "profile.json", "training.json", "review.json", "reference.txt" -> MAX_METADATA_BYTES
                    else -> {
                        require(trace != null)
                        TraceCodec.MAX_BYTES + 20
                    }
                }
                val bytes = ByteArrayOutputStream()
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= MAX_BYTES && bytes.size() + read <= limit)
                    bytes.write(buffer, 0, read)
                }
                fun text() = bytes.toString(Charsets.UTF_8.name())
                when (entry.name) {
                    "format" -> require(text() == FORMAT)
                    "complete" -> completion = text()
                    "profile.json" -> profile = Profile.fromJson(JSONObject(text())).also {
                        require(it.name.isNotBlank() && it.emblem in 0..3)
                    }
                    "hands.txt" -> hands = text().lineSequence().filter { it.isNotBlank() }.map {
                        requireNotNull(HandRecord.decode(it))
                    }.toList()
                    "training.json" -> training = decodeTrainingProgress(text())
                    "review.json" -> review = decodeSpacedReview(text())
                    "reference.txt" -> reference = ReferenceCodec.decode(text())
                    else -> analyses += TraceCodec.decode(bytes.toByteArray()).also {
                        require(it.handId == requireNotNull(trace).groupValues[1].toLong())
                    }
                }
                zip.closeEntry()
            }
        }
        val records = requireNotNull(hands)
        val ids = records.map { it.id }.toSet()
        require(completion == "${records.size}:${analyses.size}:${reference != null}")
        require("format" in names && ids.size == records.size && analyses.all { it.handId in ids })
        return SaveBackup(requireNotNull(profile), records, analyses,
            requireNotNull(training), requireNotNull(review), reference)
    }
}
