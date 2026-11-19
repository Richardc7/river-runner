package com.riverrunner.ui

import com.riverrunner.engine.strategy.*
import org.json.JSONObject
import java.io.File

/** Separate per-profile training and reference files; never touches H1/H2 histories. */
private fun trainingFile(root: File, profile: String, type: String): File {
    require(profile.matches(Regex("[A-Za-z0-9_-]{1,80}")))
    return File(root, type + "_" + profile + ".json")
}
class TrainingProgressStore(private val root: File, private val profile: String) {
    private val file get() = trainingFile(root, profile, "training_progress")
    fun load(): TrainingProgress = runCatching {
        if (file.exists()) decodeTrainingProgress(file.readText()) else TrainingProgress()
    }.getOrDefault(TrainingProgress())
    fun save(progress: TrainingProgress) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(encodeTrainingProgress(progress))
        check(tmp.renameTo(file)) { "Training progress commit failed" }
    }
    fun delete() { file.delete(); File(file.path + ".tmp").delete() }
}
class TrainingReferenceStore(private val root: File, private val profile: String) {
    private val file get() = trainingFile(root, profile, "training_reference")
    fun load(): ReferenceBook? = runCatching { if (file.exists()) ReferenceCodec.decode(file.readText()) else null }.getOrNull()
    fun save(book: ReferenceBook) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(ReferenceCodec.encode(book))
        check(tmp.renameTo(file)) { "Reference commit failed" }
    }
    fun delete() { file.delete(); File(file.path + ".tmp").delete() }
}

internal fun decodeTrainingProgress(text: String): TrainingProgress {
    val obj = JSONObject(text)
    require(obj.getInt("version") == 1)
    val stats = obj.getJSONObject("categories")
    val entries = stats.keys().asSequence().associateWith { key ->
        val row = stats.getJSONObject(key)
        CategoryProgress(row.getInt("attempts"), row.getInt("exact"), row.getDouble("modelLossChips"))
    }
    return TrainingProgress(entries, obj.optString("last").takeIf { it.isNotBlank() })
}

internal fun encodeTrainingProgress(progress: TrainingProgress): String {
    val categories = JSONObject()
    for ((key, value) in progress.categories) {
        categories.put(key, JSONObject().put("attempts", value.attempts)
            .put("exact", value.exact).put("modelLossChips", value.lossChips))
    }
    return JSONObject().put("version", 1).put("last", progress.last ?: "")
        .put("categories", categories).toString()
}
