package com.riverrunner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riverrunner.engine.strategy.EditionConfig

@Composable
fun HouseRulesSettings(config: EditionConfig, chinese: Boolean, onChange: (EditionConfig) -> Unit) {
    fun t(zh: String, en: String) = if (chinese) zh else en
    Text(t("\u79c1\u4eba\u5c40\u89c4\u5219", "Cash house rules"), color = Palette.Text, style = MaterialTheme.typography.titleMedium)
    Text(t("\u4ec5\u4f5c\u865a\u62df\u7b79\u7801\u7ec3\u4e60\uff1b\u9526\u6807\u8d5b\u5ffd\u7565\u4e0b\u5217\u73b0\u91d1\u5c40\u8bbe\u7f6e\u3002", "Simulated chips only. Tournament mode ignores cash-only settings."), color = Palette.TextDim)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(config.cashRules.utgStraddle, { onChange(config.copy(cashRules = config.cashRules.copy(utgStraddle = it))) })
        Text(t("UTG 2BB Straddle\uff08\u4e09\u4eba\u4ee5\u4e0a\u4e14\u7b79\u7801\u8db3\u989d\uff09", "Live UTG 2BB straddle (3+ players, fully funded)"), color = Palette.Text)
    }
    Text(t("\u62bd\u6c34\u6bd4\u4f8b", "Rake rate"), color = Palette.Text)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (rate in listOf(0, 250, 500, 1000)) FilterChip(config.cashRules.rakeBasisPoints == rate,
            onClick = { onChange(config.copy(cashRules = config.cashRules.copy(rakeBasisPoints = rate,
                rakeCapBB = if (rate == 0) 0 else config.cashRules.rakeCapBB.coerceAtLeast(3)))) },
            label = { Text("${rate / 100.0}%") })
    }
    if (config.cashRules.hasRake) {
        Text(t("\u6bcf\u624b\u62bd\u6c34\u5c01\u9876\uff08\u4e0d\u662f\u6bcf\u4e2a\u8fb9\u6c60\uff09", "Per-hand cap, not per side pot"), color = Palette.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (cap in listOf(1, 2, 3, 5, 10)) FilterChip(config.cashRules.rakeCapBB == cap,
                { onChange(config.copy(cashRules = config.cashRules.copy(rakeCapBB = cap))) }, label = { Text("${cap}BB") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(config.cashRules.noFlopNoDrop, { onChange(config.copy(cashRules = config.cashRules.copy(noFlopNoDrop = it))) })
            Text("No flop, no drop", color = Palette.Text)
        }
        Text(t("\u62bd\u6c34\u724c\u5c40\u4e0d\u5c55\u793a\u672a\u6821\u51c6\u7684\u6cb3\u724c EV\uff1b\u5e95\u6c60\u8d54\u7387\u663e\u793a\u7684\u662f\u62bd\u6c34\u524d\u6570\u503c\u3002", "Raked nodes do not show uncalibrated river EV. Pot odds displayed at the table are gross, before fees."), color = Palette.TextDim)
    }
    Text(t("\u6bcf\u5ea7\u4f4d\u8f93\u5149\u540e\u81ea\u52a8\u91cd\u4e70\u6b21\u6570", "Automatic bust rebuys per seat"), color = Palette.Text)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (count in listOf(0, 1, 2, 5)) FilterChip(config.automaticRebuys == count,
            { onChange(config.copy(automaticRebuys = count)) }, label = { Text(count.toString()) })
    }
    Text(t("\u5404\u5ea7\u4f4d\u8d77\u59cb\u7b79\u7801\uff08\u5927\u76f2\u6570\uff09", "Per-seat starting stacks (BB)"), color = Palette.Text)
    for (seat in 0..8) {
        var menu by remember(seat) { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${t("\u5ea7\u4f4d", "Seat")} $seat", color = Palette.Text, modifier = Modifier.padding(top = 12.dp))
            Box {
                OutlinedButton({ menu = true }) { Text(config.seatStackBB[seat]?.let { "${it}BB" } ?: t("\u8ddf\u968f\u724c\u684c", "Table default")) }
                DropdownMenu(menu, { menu = false }) {
                    for (stack in listOf(0, 20, 40, 60, 100, 150, 200, 300, 500, 1000, 2000, 5000)) {
                        DropdownMenuItem(text = { Text(if (stack == 0) t("\u8ddf\u968f\u724c\u684c", "Table default") else "${stack}BB") }, onClick = {
                            val map = config.seatStackBB.toMutableMap()
                            if (stack == 0) map.remove(seat) else map[seat] = stack
                            onChange(config.copy(seatStackBB = map.toMap())); menu = false
                        })
                    }
                }
            }
        }
    }
}
