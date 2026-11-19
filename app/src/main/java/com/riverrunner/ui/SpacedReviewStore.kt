package com.riverrunner.ui

import com.riverrunner.engine.strategy.*
import org.json.JSONObject
import java.io.File

/** Per-profile progress is separate from legacy replay and saved hand formats. */
class SpacedReviewStore(private val root: File, private val profile: String) {
    init { require(profile.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) }
    private val file get() = File(root, "study_spaced_" + profile + ".json")
    fun load(): ReviewProgress = runCatching {
        if (file.exists()) decodeSpacedReview(file.readText()) else ReviewProgress()
    }.getOrDefault(ReviewProgress())
    fun save(progress: ReviewProgress) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(encodeSpacedReview(progress))
        check(tmp.renameTo(file)) { "Could not save spaced review progress" }
    }
    fun delete() { file.delete(); File(file.path + ".tmp").delete() }
}

internal fun decodeSpacedReview(text: String): ReviewProgress {
    val json = JSONObject(text)
    require(json.getInt("schema") == 1)
    val entries = json.getJSONObject("entries")
    val map = entries.keys().asSequence().filter { it.matches(Regex("[a-zA-Z0-9_-]{1,90}")) }.associateWith { id ->
        val row = entries.getJSONObject(id)
        ReviewMemory(row.getInt("attempts"), row.getInt("correct"), row.getLong("dueDay"), row.getDouble("lastScore"))
    }
    return ReviewProgress(map, json.optString("last").takeIf { it.isNotBlank() })
}

internal fun encodeSpacedReview(progress: ReviewProgress): String {
    val rows = JSONObject()
    for ((id, row) in progress.entries) rows.put(id, JSONObject()
        .put("attempts", row.attempts).put("correct", row.correct)
        .put("dueDay", row.dueDay).put("lastScore", row.lastScore))
    return JSONObject().put("schema", 1).put("last", progress.lastDrill ?: "")
        .put("entries", rows).toString()
}
