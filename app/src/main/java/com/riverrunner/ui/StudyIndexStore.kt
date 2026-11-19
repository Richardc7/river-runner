package com.riverrunner.ui

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class StudyIndexEntry(
    val handId: Long,
    val hasBlind: Boolean,
    val hasDeviation: Boolean,
    val hasDrill: Boolean,
)

/**
 * Lightweight per-profile metadata index so History filtering, blind-quiz badges,
 * and River drill discovery avoid re-parsing hundreds of trace files on every open.
 */
internal class StudyIndexStore(root: File, profileId: String) {
    private val file = File(root, "study_index_$profileId.json")

    fun load(): MutableMap<Long, StudyIndexEntry> {
        if (!file.isFile) return linkedMapOf()
        return runCatching {
            val rootObj = JSONObject(file.readText(Charsets.UTF_8))
            if (rootObj.optInt("schema", 1) != 1) return linkedMapOf()
            val arr = rootObj.optJSONArray("entries") ?: return linkedMapOf()
            val out = linkedMapOf<Long, StudyIndexEntry>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optLong("id", -1L)
                if (id < 0L) continue
                out[id] = StudyIndexEntry(
                    handId = id,
                    hasBlind = item.optBoolean("b", false),
                    hasDeviation = item.optBoolean("d", false),
                    hasDrill = item.optBoolean("r", false),
                )
            }
            out
        }.getOrElse { linkedMapOf() }
    }

    fun save(entries: Map<Long, StudyIndexEntry>) {
        val arr = JSONArray()
        for (entry in entries.values) {
            arr.put(
                JSONObject()
                    .put("id", entry.handId)
                    .put("b", entry.hasBlind)
                    .put("d", entry.hasDeviation)
                    .put("r", entry.hasDrill)
            )
        }
        val payload = JSONObject()
            .put("schema", 1)
            .put("entries", arr)
            .toString()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(payload, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.writeText(payload, Charsets.UTF_8)
            tmp.delete()
        }
    }

    fun upsert(entry: StudyIndexEntry, retainedIds: Set<Long>? = null) {
        val map = load()
        map[entry.handId] = entry
        if (retainedIds != null) {
            map.keys.retainAll(retainedIds)
        }
        save(map)
    }

    fun retain(retainedIds: Set<Long>) {
        if (!file.isFile) return
        val map = load()
        if (map.keys.retainAll(retainedIds)) {
            save(map)
        }
    }

    fun delete() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
    }
}
