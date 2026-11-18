package com.riverrunner.data

import android.content.Context
import com.riverrunner.engine.strategy.*
import com.riverrunner.engine.CashRules
import org.json.JSONObject

/** Separate, versioned preferences; old saves and upstream preference keys are unchanged. */
class EditionPrefs(context: Context) {
    private val preferences = context.getSharedPreferences("exploit_edition", Context.MODE_PRIVATE)
    fun load(): EditionConfig = decode(preferences.getString("settings", null))
    fun save(config: EditionConfig) { preferences.edit().putString("settings", encode(config)).apply() }

    companion object {
        fun decode(text: String?): EditionConfig {
            if (text == null || text.length > 16384) return EditionConfig()
            return runCatching {
                val json = JSONObject(text)
                require(json.optInt("schema", 1) == 1)
                val mode = AiMode.entries.firstOrNull { it.name == json.optString("mode") } ?: AiMode.LEGACY
                val seats = json.optJSONObject("opponents") ?: JSONObject()
                val overrides = (1..8).mapNotNull { seat ->
                    OpponentPreset.entries.firstOrNull { it.name == seats.optString(seat.toString()) }?.let { seat to it }
                }.toMap()
                val rules = json.optJSONObject("cashRules") ?: JSONObject()
                val stacks = json.optJSONObject("seatStackBB") ?: JSONObject()
                val stackMap = (0..8).filter { stacks.has(it.toString()) }.associateWith { stacks.getInt(it.toString()) }
                EditionConfig(mode, overrides, json.optBoolean("recordAdvice", true),
                    CashRules(rules.optBoolean("utgStraddle", false), rules.optInt("rakeBasisPoints", 0),
                        rules.optInt("rakeCapBB", 0), rules.optBoolean("noFlopNoDrop", true)),
                    stackMap, json.optInt("automaticRebuys", 0))
            }.getOrElse { EditionConfig() }
        }
        fun encode(config: EditionConfig): String = JSONObject().apply {
            put("schema", 1); put("mode", config.aiMode.name); put("recordAdvice", config.recordAdvice)
            put("automaticRebuys", config.automaticRebuys)
            put("seatStackBB", JSONObject().apply { config.seatStackBB.forEach { (seat, stack) -> put(seat.toString(), stack) } })
            put("cashRules", JSONObject().apply {
                put("utgStraddle", config.cashRules.utgStraddle); put("rakeBasisPoints", config.cashRules.rakeBasisPoints)
                put("rakeCapBB", config.cashRules.rakeCapBB); put("noFlopNoDrop", config.cashRules.noFlopNoDrop)
            })
            put("opponents", JSONObject().apply { config.opponents.forEach { (seat, preset) -> put(seat.toString(), preset.name) } })
        }.toString()
    }
}
