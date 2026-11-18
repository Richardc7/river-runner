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
        if (!file.exists()) return@runCatching TrainingProgress()
        val obj = JSONObject(file.readText())
        if (obj.optInt("version") != 1) return TrainingProgress()
        val stats = obj.getJSONObject("categories")
        val entries = mutableMapOf<String,CategoryProgress>()
        val it = stats.keys()
        while (it.hasNext()) {
            val key = it.next()
            val s = stats.getJSONObject(key)
            entries[key] = CategoryProgress(s.getInt("attempts"), s.getInt("exact"), s.getDouble("modelLossChips"))
        }
        TrainingProgress(entries, obj.optString("last").takeIf { it.isNotBlank() })
    }.getOrDefault(TrainingProgress())
    fun save(progress: TrainingProgress) {
        val json = JSONObject().put("version", 1).put("last", progress.last ?: "")
        val categories = JSONObject()
        for ((key, value) in progress.categories) {
            categories.put(key, JSONObject().put("attempts", value.attempts).put("exact", value.exact).put("modelLossChips", value.lossChips))
        }
        json.put("categories", categories)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.toString())
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
