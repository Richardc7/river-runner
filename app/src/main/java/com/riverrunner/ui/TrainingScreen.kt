package com.riverrunner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.riverrunner.engine.*
import com.riverrunner.engine.strategy.*

data class TrainingItem(
    val drill: TrainingDrill,
    val record: HandRecord,
    val view: DecisionView,
    val baseline: PolicySnapshot,
    val notes: List<String>,
)

/** Practices past decision-time *model* action values, not privileged showdown outcomes. */
@Composable
fun TrainingScreen(vm: AppViewModel, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val zh = vm.prefs.chinese
    fun label(cn: String, en: String) = if (zh) cn else en
    var entries by remember(vm.profile.id) { mutableStateOf<List<TrainingItem>>(emptyList()) }
    var progress by remember(vm.profile.id) { mutableStateOf(TrainingProgress()) }
    var reference by remember(vm.profile.id) { mutableStateOf<ReferenceBook?>(null) }
    var loading by remember(vm.profile.id) { mutableStateOf(true) }
    var chosen by remember(vm.profile.id) { mutableStateOf<Move?>(null) }
    var imported by remember(vm.profile.id) { mutableStateOf("") }
    var importStatus by remember(vm.profile.id) { mutableStateOf("") }
    var showImport by remember(vm.profile.id) { mutableStateOf(false) }
    LaunchedEffect(vm.profile.id) {
        entries = vm.trainingItems()
        progress = vm.loadTrainingProgress()
        reference = vm.referenceForTraining()
        loading = false
    }
    val current = TrainingCurriculum.next(entries.map { it.drill }, progress)
    val item = entries.firstOrNull { it.drill.id == current?.id }
    Column(Modifier.fillMaxSize().background(Palette.Bg).verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text(label("返回", "Back")) }
            Text("Exploit V0.4 · " + label("专项训练", "Training"), color = Palette.Text, style = MaterialTheme.typography.titleLarge)
        }
        Text(label(
            "仅使用你已保存的河牌决策时模型；模型 EV 差异不代表真实损失或 GTO。没有对应牌谱时不会生成假练习。",
            "Exercises use saved decision-time river models only. Model EV loss is not actual loss or verified GTO. No fabricated exercises."
        ), color = Palette.TextDim)
        val completed = progress.categories.values.sumOf { it.attempts }
        Text(label("完成练习：", "Attempts: ") + completed, color = Palette.Text)
        if (loading) Text(label("正在读取历史...", "Loading saved hands..."), color = Palette.Text)
        else if (item == null) Text(
            label("暂时没有适用的练习。先开启决策分析、打现金局并记录支持的河牌决策。",
                "No eligible exercises. Enable advice capture and play supported cash river decisions first."),
            color = Palette.Text
        )
        else {
            val d = item.drill
            Text(label("练习场景", "Scenario") + " · " + d.category, color = Palette.Text)
            Text("Hand #" + item.record.id + " | " + item.view.street + " | #" + item.view.actionIndex, color = Palette.TextDim)
            Text(label("手牌：", "Hole: ") + Cards.format(item.view.ownHole.toIntArray()) +
                "  " + label("公共牌：", "Board: ") + Cards.format(item.view.board.toIntArray()), color = Palette.Text)
            Text(label("底池：", "Pot: ") + vm.fmt(item.view.pot, item.record.bigBlind), color = Palette.Text)
            Text(label("请选择一个行动，再揭晓模型结果。", "Choose an action to reveal model values."), color = Palette.TextDim)
            for (option in d.options) {
                val text = option.move.type.name + if (option.move.type == MoveType.RAISE) " " +
                    vm.fmt(option.move.raiseTo, item.record.bigBlind) else ""
                OutlinedButton(onClick = { chosen = option.move }, enabled = chosen == null) { Text(text) }
            }
            val pick = chosen
            if (pick != null) {
                val result = d.grade(pick)
                Text(label("模型 EV 差距：", "Model EV difference: ") +
                    "%.2f".format(java.util.Locale.US, result.modelLossChips / item.record.bigBlind) + " BB", color = Palette.Text)
                Text(label("模型首选：", "Model choice: ") + d.best.move.type.name, color = Palette.Text)
                d.options.forEach {
                    Text(it.move.type.name + ": " + "%.2f".format(java.util.Locale.US, it.chips / item.record.bigBlind) +
                        " BB [" + "%.2f".format(java.util.Locale.US, it.low / item.record.bigBlind) +
                        ", " + "%.2f".format(java.util.Locale.US, it.high / item.record.bigBlind) + "]", color = Palette.TextDim)
                }
                item.notes.take(4).forEach { Text(it, color = Palette.TextDim) }
                Button(onClick = {
                    progress = progress.record(result)
                    vm.saveTrainingProgress(progress)
                    chosen = null
                }) { Text(label("记录并进入下一题", "Save and next")) }
            }
            val ref = reference?.lookup(item.view)
            if (ref != null) {
                Text(label("外部参考（来源声明，不保证真实 GTO）：", "Declared external reference (not verified GTO):"),
                    color = Palette.Gold)
                Text(reference?.provenance?.source ?: "", color = Palette.TextDim)
                ref.actions.forEach { Text(it.move.type.name + ": " +
                    "%.1f".format(java.util.Locale.US, 100 * it.frequency) + "%", color = Palette.Text) }
            }
        }
        HorizontalDivider()
        TextButton(onClick = { showImport = !showImport }) { Text(label("离线参考策略导入", "Offline reference import")) }
        val savedReference = reference
        if (savedReference != null) {
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(ReferenceCodec.encode(savedReference))) }) {
                Text(label("复制已保存参考策略", "Copy saved reference"))
            }
        }
        if (showImport) {
            Text(label("仅接受 RR-REFERENCE-1 格式及精确决策哈希；不包含求解器数据。",
                "Accepts RR-REFERENCE-1 with exact decision hashes. No solver data is bundled."), color = Palette.TextDim)
            OutlinedTextField(imported, onValueChange = { if (it.length <= 1_000_000) imported = it },
                label = { Text(label("粘贴参考策略文本", "Paste reference text")) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
            Button(onClick = {
                runCatching { ReferenceCodec.decode(imported) }.onSuccess {
                    reference = it
                    vm.saveTrainingReference(it)
                    importStatus = label("参考策略已保存", "Reference saved")
                }.onFailure {
                    importStatus = label("格式不合法：", "Invalid reference: ") + (it.message ?: "")
                }
            }) { Text(label("验证并保存", "Validate and save")) }
            if (importStatus.isNotBlank()) Text(importStatus, color = Palette.TextDim)
        }
    }
}
