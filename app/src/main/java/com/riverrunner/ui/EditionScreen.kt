package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.riverrunner.engine.strategy.*

@Composable
fun EditionScreen(initial: EditionConfig, chinese: Boolean, onSave: (EditionConfig) -> Unit, onBack: () -> Unit,
    showCashRules: Boolean = false,
) {
    var config by remember(initial) { mutableStateOf(initial) }
    var cashSelected by rememberSaveable(showCashRules) { mutableStateOf(showCashRules) }
    var editingSeat by rememberSaveable { mutableStateOf<Int?>(null) }
    fun t(zh: String, en: String) = if (chinese) zh else en

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            SmallButton(t("← 返回", "← Back"), onBack)
            Column(Modifier.weight(1f)) {
                Text(t("自定义 AI、对手与规则", "Customize AI, opponents and rules"),
                    color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(t("保存后用于新牌局", "Applies to new tables after saving"), color = Palette.TextDim, fontSize = 11.sp)
            }
            ActionButton(t("保存配置", "Save settings"), Palette.Gold, Modifier.widthIn(min = 110.dp).height(42.dp),
                textColor = Palette.Ink) {
                onSave(config)
                onBack()
            }
        }

        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingsChip(t("AI 策略", "AI strategy"), !cashSelected, { cashSelected = false })
                    SettingsChip(t("筹码局规则", "Cash rules"), cashSelected, { cashSelected = true })
                }
                val scroll = key(cashSelected) { rememberScrollState() }
                Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (cashSelected) {
                        HouseRulesSettings(config, chinese) { config = it }
                    } else {
                        ConfigurationChoices(t("AI 模式", "AI mode"), AiMode.entries, config.aiMode, {
                            if (it == AiMode.LEGACY) t("原版基准", "Legacy baseline") else t("动态剥削", "Adaptive exploit")
                        }) { config = config.copy(aiMode = it) }
                        Text(
                            if (config.aiMode == AiMode.LEGACY) t("对手使用固定的基准策略。", "Opponents use the fixed baseline policy.")
                            else t("对手会根据本次牌局的行动调整。样本不足时沿用基准策略。",
                                "Opponents adapt to actions in this session. Sparse evidence retains the baseline."),
                            fontSize = 13.sp, lineHeight = 18.sp, color = Palette.TextDim,
                        )
                        if (config.aiMode == AiMode.ADAPTIVE) {
                            SettingsSwitch(
                                t("高阶反剥削（识别过度弃牌与高频偷池）", "Counter-exploit mode (punish over-folding & frequent stabs)"),
                                t("仅基于本桌公开行动做有上限反制，不读取底牌。", "Bounded public-info counter-adjustments only; never sees hidden cards."),
                                config.antiExploit,
                            ) { config = config.copy(antiExploit = it) }
                        }
                        HorizontalDivider(color = Palette.Line)
                        SettingsSwitch(
                            t("保存决策时的证据与双轨建议", "Record evidence and both strategy recommendations"),
                            t("用于盲测、河牌练习及策略对比。", "Used for blind reviews, river drills and strategy comparison."),
                            config.recordAdvice,
                        ) { config = config.copy(recordAdvice = it) }
                    }
                }
            }

            Column(Modifier.weight(1.06f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel)
                .verticalScroll(rememberScrollState()).padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(t("座位配置", "Seat settings"), color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(t("点选座位设置对手风格或筹码。超出开局人数的座位会忽略。",
                    "Select a seat to set its opponent style or stack. Seats outside the table size are ignored."),
                    color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
                for (seats in (0..8).chunked(3)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (seat in seats) {
                            val preset = config.opponents[seat]
                            val stack = config.seatStackBB[seat]
                            SeatSettingsCard(
                                Modifier.weight(1f),
                                if (seat == 0) t("你 · 座位 0", "You · Seat 0") else t("座位 $seat", "Seat $seat"),
                                presetLabel(preset, chinese).takeIf { seat != 0 },
                                stack?.let { "$it BB" } ?: t("默认筹码", "Default stack"),
                                preset != null || stack != null,
                            ) { editingSeat = seat }
                        }
                    }
                }
            }
        }
    }

    editingSeat?.let { seat ->
        SeatSettingsDialog(seat, config.opponents[seat], config.seatStackBB[seat], chinese,
            close = { editingSeat = null }) { preset, stack ->
            val opponents = config.opponents.toMutableMap()
            if (seat > 0 && preset != null) opponents[seat] = preset else opponents.remove(seat)
            val stacks = config.seatStackBB.toMutableMap()
            if (stack != null) stacks[seat] = stack else stacks.remove(seat)
            config = config.copy(opponents = opponents, seatStackBB = stacks)
            editingSeat = null
        }
    }
}

@Composable
private fun SeatSettingsCard(modifier: Modifier, title: String, preset: String?, stack: String,
    customized: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(modifier.clip(shape).background(Palette.PanelHi).border(1.dp, if (customized) Palette.Gold else Palette.Line, shape)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("›", color = Palette.Gold, fontSize = 15.sp, lineHeight = 15.sp)
        }
        Text(listOfNotNull(preset, stack).joinToString(" · "), color = Palette.TextDim, fontSize = 11.sp,
            lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SeatSettingsDialog(
    seat: Int, initialPreset: OpponentPreset?, initialStack: Int?, chinese: Boolean,
    close: () -> Unit, apply: (OpponentPreset?, Int?) -> Unit,
) {
    var preset by rememberSaveable(seat) { mutableStateOf(initialPreset) }
    var stack by rememberSaveable(seat) { mutableStateOf(initialStack) }
    fun t(zh: String, en: String) = if (chinese) zh else en
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.85f).widthIn(max = 690.dp).fillMaxHeight(.84f),
            shape = RoundedCornerShape(14.dp), color = Palette.Panel) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (seat == 0) t("你的起始筹码", "Your starting stack") else t("编辑座位 $seat", "Edit seat $seat"),
                    color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (seat != 0) {
                        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                            ConfigurationChoices(t("对手风格", "Opponent style"), listOf(null) + OpponentPreset.entries,
                                preset, { presetLabel(it, chinese) }) { preset = it }
                        }
                    }
                    Column(Modifier.weight(1.08f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        val stacks = listOf(null, 20, 40, 60, 100, 150, 200, 300, 500, 1000, 2000, 5000)
                        val choices = if (initialStack == null || initialStack in stacks) stacks else stacks + initialStack
                        ConfigurationChoices(t("起始筹码", "Starting stack"), choices, stack,
                            { it?.let { "$it BB" } ?: t("跟随牌桌", "Table default") }) { stack = it }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = close) { Text(t("取消", "Cancel"), color = Palette.Text) }
                    Button(onClick = { apply(preset, stack) }) { Text(t("完成", "Done")) }
                }
            }
        }
    }
}

@Composable
internal fun SettingsSwitch(label: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(checked, change)
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
        if (config.antiExploit) {
            if (chinese) "动态剥削（反制）" else "Adaptive + counter"
        } else {
            if (chinese) "动态剥削" else "Adaptive exploit"
        }
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
