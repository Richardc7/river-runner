package com.riverrunner.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards
import com.riverrunner.engine.MoveType
import com.riverrunner.engine.strategy.DecisionFingerprint
import com.riverrunner.engine.strategy.DecisionView
import com.riverrunner.engine.strategy.ReferenceBook
import com.riverrunner.engine.strategy.ReferenceCodec
import com.riverrunner.engine.strategy.SolverSpot
import com.riverrunner.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.Reader
import java.util.Locale

internal fun SolverSpot.toJson(): String {
    val legal = view.legal
    return JSONObject().apply {
        put("format", "RR-SOLVER-SPOT-1")
        put("contextHash", DecisionFingerprint.of(view))
        put("board", JSONArray(view.board.map(Cards::toString)))
        put("heroCards", Cards.format(view.ownHole.toIntArray()))
        put("heroRole", if (view.inPosition) "IP" else "OOP")
        put("startingPot", startingPot)
        put("effectiveStack", effectiveStack)
        put("bigBlind", view.bigBlind)
        put("riverActions", JSONArray(riverActions.map {
            JSONObject().put("type", it.type.name).put("amount", it.amount)
        }))
        put("legal", JSONObject()
            .put("canCheck", legal.canCheck).put("canCall", legal.canCall)
            .put("canRaise", legal.canRaise).put("minRaiseTo", legal.minRaiseTo)
            .put("maxRaiseTo", legal.maxRaiseTo).put("heroStreetBet", legal.myStreetBet))
    }.toString(2)
}

@Composable
internal fun ExportSolverSpot(view: DecisionView) {
    val spot = remember(view) { SolverSpot.from(view) } ?: return
    val zh = LocalStrings.current.isZh
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var status by remember(view) { mutableStateOf<Boolean?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            status = try {
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot open destination")
                    output.bufferedWriter().use { it.write(spot.toJson()) }
                }
                true
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }
    }
    OutlinedButton(onClick = {
        val id = DecisionFingerprint.of(view).take(10)
        picker.launch("river-runner-$id.json")
    }) { Text(if (zh) "导出求解场景" else "Export solver spot") }
    status?.let { ok ->
        Text(
            if (ok) { if (zh) "场景文件已保存" else "Spot file saved" }
            else if (zh) "无法保存场景文件" else "Could not save the spot file",
            color = if (ok) Palette.TextDim else Palette.Red, fontSize = 11.sp,
        )
    }
}

@Composable
internal fun SolverReferencePanel(view: DecisionView, reference: ReferenceBook?) {
    val book = reference ?: return
    val node = remember(book, view) { book.lookup(view) } ?: return
    val zh = LocalStrings.current.isZh
    val choices = node.actions.filter { it.frequency > 0 }.sortedByDescending { it.frequency }
    var details by remember(book, view) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.PanelHi).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(if (zh) "外部策略参考" else "External strategy reference",
            color = Palette.Gold, style = MaterialTheme.typography.titleMedium)
        Text(book.provenance.source + " · " + book.provenance.author, color = Palette.Text, fontSize = 12.sp)
        StrategyFrequencyBar(choices.map { it.move.type to it.frequency })
        for (choice in choices) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val action = if (view.legal.isOpeningBet && choice.move.type == MoveType.RAISE) {
                    (if (zh) "下注 " else "Bet ") + Format.amount(choice.move.raiseTo, view.bigBlind, true)
                } else StudyPresentation.move(choice.move, view.bigBlind, zh)
                Text(action, color = Palette.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("%.1f%%".format(Locale.US, choice.frequency * 100),
                    color = strategyActionColor(choice.move.type), fontSize = 12.sp)
            }
        }
        TextButton(onClick = { details = !details }) {
            Text(if (zh) "求解条件" else "Solve details")
        }
        if (details) {
            Text(book.provenance.context, color = Palette.TextDim, fontSize = 11.sp)
            Text(book.provenance.licence, color = Palette.TextDim, fontSize = 11.sp)
        }
    }
}

internal fun strategyActionColor(type: MoveType) = when (type) {
    MoveType.FOLD -> Palette.Red
    MoveType.CHECK -> Palette.Blue
    MoveType.CALL -> Palette.Green
    MoveType.RAISE -> Palette.Gold
}

@Composable
internal fun StrategyFrequencyBar(frequencies: List<Pair<MoveType, Double>>) {
    Row(
        Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        for ((type, frequency) in frequencies) {
            if (frequency > 0) Box(Modifier.weight(frequency.toFloat().coerceAtLeast(Float.MIN_VALUE))
                .height(6.dp).background(strategyActionColor(type)))
        }
    }
}

@Composable
internal fun SolverReferenceLibrary(vm: AppViewModel, reference: ReferenceBook?, onImported: (ReferenceBook) -> Unit) {
    val zh = LocalStrings.current.isZh
    fun t(cn: String, en: String) = if (zh) cn else en
    val resolver = LocalContext.current.contentResolver
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var raw by remember(vm.profile.id) { mutableStateOf("") }
    var importing by remember(vm.profile.id) { mutableStateOf(false) }
    var status by remember(vm.profile.id) { mutableStateOf("") }
    var failed by remember(vm.profile.id) { mutableStateOf(false) }

    fun importReference(read: () -> String) {
        if (importing) return
        importing = true
        status = ""
        scope.launch {
            try {
                val book = withContext(Dispatchers.IO) { ReferenceCodec.decode(read().removePrefix("\uFEFF")) }
                vm.saveTrainingReference(book)
                onImported(book)
                raw = ""
                status = t("策略库已更新。", "Reference library updated.")
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                status = t("导入失败。请检查文件格式、大小和策略频率。",
                    "Import failed. Check the file format, size and action frequencies.")
                failed = true
            } finally {
                importing = false
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importReference {
            val input = resolver.openInputStream(uri) ?: throw IOException("Cannot open file")
            input.bufferedReader().use { it.readReferenceText() }
        }
    }

    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)).background(Palette.Panel)
            .verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(t("离线求解器参考", "Offline solver reference"),
            color = Palette.Gold, style = MaterialTheme.typography.titleLarge)
        Text(t("在双轨分析或答案解析中导出单挑河牌场景，使用电脑上的求解器求解，再导入 RR-REFERENCE-1 结果。",
            "Export a heads-up river spot from Policy comparison or a quiz explanation. Solve it on a computer and import the RR-REFERENCE-1 result."),
            color = Palette.TextDim, fontSize = 12.sp)
        Text(t("结果按原始决策精确匹配，频率取决于求解时的范围和下注尺寸。",
            "Results match the original decision exactly. Frequencies depend on the ranges and sizes used for the solve."),
            color = Palette.TextDim, fontSize = 12.sp)
        if (reference != null) {
            Text(t("已保存 ${reference.nodes.size} 个场景", "Saved spots: ${reference.nodes.size}") +
                " · " + reference.provenance.source, color = Palette.Text, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !importing) {
                Text(if (reference == null) t("导入策略文件", "Import strategy file") else t("替换策略文件", "Replace strategy file"))
            }
            if (reference != null) OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(ReferenceCodec.encode(reference)))
            }) { Text(t("复制已保存数据", "Copy saved reference")) }
        }
        if (status.isNotEmpty()) Text(status, color = if (failed) Palette.Red else Palette.Green, fontSize = 12.sp)
        OutlinedTextField(
            raw, { if (it.length <= ReferenceCodec.MAX_TEXT_LENGTH) raw = it },
            label = { Text(t("或粘贴参考策略文本", "Or paste reference text")) },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, enabled = !importing,
        )
        OutlinedButton(onClick = { val input = raw; importReference { input } }, enabled = raw.isNotBlank() && !importing) {
            Text(if (reference == null) t("校验并导入", "Validate & import") else t("校验并替换", "Validate & replace"))
        }
    }
}

private fun Reader.readReferenceText(): String = buildString {
    val buffer = CharArray(4096)
    while (true) {
        val count = read(buffer)
        if (count == -1) break
        require(length + count <= ReferenceCodec.MAX_TEXT_LENGTH) { "Reference file is too large" }
        append(buffer, 0, count)
    }
}
