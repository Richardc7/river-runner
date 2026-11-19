package com.riverrunner.ui

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal data class StudyIndexEntry(
    val handId: Long,
    val hasBlind: Boolean,
    val hasDeviation: Boolean,
    val hasDrill: Boolean,
)

/**
 * Lightweight per-profile metadata index so History filtering, blind-quiz badges,
 * and River drill discovery avoid re-parsing hundreds of trace files on every open.
 * All reads, read-modify-writes, and deletes are synchronized per index file and
 * use unique temp files to prevent concurrent overwrites.
 */
internal class StudyIndexStore(root: File, profileId: String) {
    private val file = File(root, "study_index_$profileId.json")
    private val lock: Any = locks.computeIfAbsent(file.canonicalPath) { Any() }

    fun load(): MutableMap<Long, StudyIndexEntry> = synchronized(lock) {
        loadLocked()
    }

    private fun loadLocked(): MutableMap<Long, StudyIndexEntry> {
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

    fun save(entries: Map<Long, StudyIndexEntry>) = synchronized(lock) {
        saveLocked(entries)
    }

    private fun saveLocked(entries: Map<Long, StudyIndexEntry>) {
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
        val parent = file.parentFile ?: File(".")
        val tmp = File.createTempFile(file.name, ".tmp", parent)
        try {
            tmp.writeText(payload, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(payload, Charsets.UTF_8)
            }
        } finally {
            tmp.delete()
        }
    }

    fun upsert(entry: StudyIndexEntry, retainedIds: Set<Long>? = null) = upsertAll(listOf(entry), retainedIds)

    fun upsertAll(entries: List<StudyIndexEntry>, retainedIds: Set<Long>? = null) = synchronized(lock) {
        if (entries.isEmpty() && retainedIds == null) return@synchronized
        val map = loadLocked()
        for (entry in entries) {
            map[entry.handId] = entry
        }
        if (retainedIds != null) {
            map.keys.retainAll(retainedIds)
        }
        saveLocked(map)
    }

    fun retain(retainedIds: Set<Long>) = synchronized(lock) {
        if (!file.isFile) return@synchronized
        val map = loadLocked()
        if (map.keys.retainAll(retainedIds)) {
            saveLocked(map)
        }
    }

    fun delete() = synchronized(lock) {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
    }

    companion object {
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
