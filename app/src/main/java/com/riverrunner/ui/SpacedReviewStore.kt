package com.riverrunner.ui

import com.riverrunner.engine.strategy.*
import org.json.JSONObject
import java.io.File

/** Per-profile progress is separate from legacy replay and saved hand formats. */
class SpacedReviewStore(private val root: File, private val profile: String) {
    init { require(profile.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) }
    private val file get() = File(root, "study_spaced_" + profile + ".json")
    fun load(): ReviewProgress = runCatching {
        if (!file.exists()) return@runCatching ReviewProgress()
        val json = JSONObject(file.readText())
        if (json.optInt("schema") != 1) return@runCatching ReviewProgress()
        val map = mutableMapOf<String, ReviewMemory>()
        val entries = json.getJSONObject("entries")
        val keys = entries.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.matches(Regex("[a-zA-Z0-9_-]{1,90}"))) continue
            val row = entries.getJSONObject(key)
            map[key] = ReviewMemory(row.getInt("attempts"), row.getInt("correct"),
                row.getLong("dueDay"), row.getDouble("lastScore"))
        }
        ReviewProgress(map, json.optString("last").takeIf { it.isNotBlank() })
    }.getOrDefault(ReviewProgress())
    fun save(progress: ReviewProgress) {
        val data = JSONObject().put("schema", 1).put("last", progress.lastDrill ?: "")
        val rows = JSONObject()
        for ((id, row) in progress.entries) rows.put(id, JSONObject()
            .put("attempts", row.attempts).put("correct", row.correct)
            .put("dueDay", row.dueDay).put("lastScore", row.lastScore))
        data.put("entries", rows)
        val tmp = File(file.path + ".tmp")
        tmp.writeText(data.toString())
        check(tmp.renameTo(file)) { "Could not save spaced review progress" }
    }
    fun delete() { file.delete(); File(file.path + ".tmp").delete() }
}
