package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riverrunner.engine.strategy.*

@Composable
fun EditionScreen(initial: EditionConfig, chinese: Boolean, onSave: (EditionConfig) -> Unit, onBack: () -> Unit) {
    var config by remember(initial) { mutableStateOf(initial) }
    fun text(zh: String, en: String) = if (chinese) zh else en
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Palette.Bg).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Exploit Edition V0.4", style = MaterialTheme.typography.headlineSmall,
                color = Palette.Text, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text(text("返回", "Back")) }
            Button(onClick = { onSave(config); onBack() }) { Text(text("保存配置", "Save settings")) }
        }
        Text(text("当前配置：", "Current: ") + editionSummary(config, chinese), color = Palette.Gold)
        Text(text("一、AI 模式与对手类型", "1. AI mode and opponent presets"),
            style = MaterialTheme.typography.titleMedium, color = Palette.Text)
        Text(text("\u8bbe\u7f6e\u4ec5\u5bf9\u65b0\u724c\u684c\u751f\u6548\uff1b\u753b\u50cf\u5728\u6bcf\u5c40\u91cd\u65b0\u5b66\u4e60\u3002", "Settings apply to new tables. Opponent evidence resets each session."), color = Palette.TextDim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiMode.entries.forEach { mode ->
                FilterChip(selected = config.aiMode == mode, onClick = { config = config.copy(aiMode = mode) },
                    label = { Text(if (mode == AiMode.LEGACY) text("\u539f\u7248\u57fa\u51c6", "Legacy baseline") else text("\u52a8\u6001\u5265\u524a", "Adaptive exploit")) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = config.recordAdvice, onCheckedChange = { config = config.copy(recordAdvice = it) })
            Text(text("\u4fdd\u5b58\u51b3\u7b56\u65f6\u7684\u8bc1\u636e\u4e0e\u53cc\u8f68\u5efa\u8bae", "Record contemporaneous evidence and both recommendations"), color = Palette.Text)
        }
        Text(text("\u5ea7\u4f4d 0 \u662f\u4f60\uff0c\u5176\u4ed6\u5ea7\u4f4d\u53ef\u6307\u5b9a\u5bf9\u624b\u3002\u8d85\u51fa\u724c\u684c\u4eba\u6570\u7684\u914d\u7f6e\u4f1a\u88ab\u5ffd\u7565\u3002", "Seat 0 is you. Overrides outside the chosen table size are ignored."), color = Palette.TextDim)
        for (seat in 1..8) {
            var expanded by remember(seat) { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text("\u5ea7\u4f4d", "Seat") + " $seat", color = Palette.Text, modifier = Modifier.padding(top = 12.dp))
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text(presetLabel(config.opponents[seat], chinese)) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        val choices: List<OpponentPreset?> = listOf(null) + OpponentPreset.entries
                        choices.forEach { preset ->
                            DropdownMenuItem(text = { Text(presetLabel(preset, chinese)) }, onClick = {
                                val map = config.opponents.toMutableMap()
                                if (preset == null) map.remove(seat) else map[seat] = preset
                                config = config.copy(opponents = map.toMap()); expanded = false
                            })
                        }
                    }
                }
            }
        }
        Text(text("\u57fa\u51c6\u662f\u542f\u53d1\u5f0f\u8fd1\u4f3c\uff0c\u4e0d\u662f\u7ecf\u9a8c\u8bc1\u7684 GTO \u6c42\u89e3\u5668\u3002\u8bc1\u636e\u4e0d\u8db3\u65f6\u4fdd\u7559\u57fa\u51c6\u3002", "The baseline is heuristic, not a verified GTO solver. Sparse evidence retains the baseline."), color = Palette.TextDim)
        HouseRulesSettings(config, chinese) { config = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(config); onBack() }) { Text(text("\u4fdd\u5b58", "Save")) }
            OutlinedButton(onClick = onBack) { Text(text("\u8fd4\u56de", "Back")) }
        }
    }
}

fun presetLabel(preset: OpponentPreset?, chinese: Boolean): String = when (preset) {
    null -> if (chinese) "\u81ea\u52a8" else "Automatic"
    OpponentPreset.BASELINE -> if (chinese) "\u57fa\u51c6" else "Baseline"
    OpponentPreset.TAG -> if (chinese) "\u7d27\u51f6" else "TAG"
    OpponentPreset.LAG -> if (chinese) "\u677e\u51f6" else "LAG"
    OpponentPreset.STATION -> if (chinese) "\u8ddf\u6ce8\u7ad9" else "Calling station"
    OpponentPreset.NIT -> if (chinese) "\u8d85\u7d27" else "Nit"
    OpponentPreset.MANIAC -> if (chinese) "\u8fc7\u5ea6\u6fc0\u8fdb" else "Maniac"
    OpponentPreset.LOOSE_PASSIVE -> if (chinese) "\u677e\u88ab\u52a8" else "Loose passive"
}


/** Visible summary of exactly the settings applied by AppViewModel.startGame. */
fun editionSummary(config: EditionConfig, chinese: Boolean): String {
    val mode = if (config.aiMode == AiMode.ADAPTIVE) {
        if (chinese) "动态剥削" else "Adaptive exploit"
    } else {
        if (chinese) "原版基准" else "Legacy baseline"
    }
    val opponents = if (chinese) "自定义对手 ${config.opponents.size} 位" else "${config.opponents.size} preset opponents"
    val rake = if (config.cashRules.rakeBasisPoints == 0) {
        if (chinese) "无抽水" else "No rake"
    } else "${config.cashRules.rakeBasisPoints / 100.0}% rake"
    val straddle = if (config.cashRules.utgStraddle) " · UTG Straddle" else ""
    return listOf(mode, opponents, rake).joinToString(" · ") + straddle
}
