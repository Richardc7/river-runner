package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riverrunner.engine.*
import com.riverrunner.engine.strategy.*

@Composable
fun EditionReplayScreen(vm: AppViewModel, initial: HandRecord) {
    var record by remember(initial.id) { mutableStateOf(initial) }
    var report by remember(initial.id) { mutableStateOf(false) }
    var coachTab by remember(initial.id) { mutableIntStateOf(0) }
    var startIndex by remember(initial.id) { mutableIntStateOf(0) }
    var analysis by remember(initial.id) { mutableStateOf<HandAnalysis?>(null) }
    var loaded by remember(initial.id) { mutableStateOf(false) }
    LaunchedEffect(initial.id) { analysis = vm.analysisFor(initial); loaded = true }
    val leave: () -> Unit = { if (vm.reviewing) vm.continueFromReview() else vm.screen = Screen.History }
    BackHandler(onBack = leave)
    val zh = vm.prefs.chinese
    val replayer = remember(initial.id) { Replayer(initial) }
    val nodes = remember(record.id, analysis) { historicalDecisions(record, analysis, replayer) }
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        if (record.rake > 0) Text((if (zh) "\u672c\u624b\u62bd\u6c34: " else "Hand rake: ") + vm.fmt(record.rake, record.bigBlind), color = Palette.TextDim)
        Row(Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { coachTab = 0; report = false }) { Text(if (zh) "逐街回放" else "Replay") }
            TextButton(onClick = { coachTab = 0; report = true }) { Text(if (zh) "双轨分析" else "Policy comparison") }
            if (vm.blindReviewEnabled) TextButton(onClick = { coachTab = 1 }) { Text(if (zh) "盲测复盘" else "Blind quiz") }
            if (vm.weaknessReportEnabled) TextButton(onClick = { coachTab = 2 }) { Text(if (zh) "弱点报告" else "Weaknesses") }
            if (vm.preflopCoachEnabled) TextButton(onClick = { coachTab = 3 }) { Text(if (zh) "翻前范围" else "Preflop range") }
            if (vm.riverWhatIfEnabled) TextButton(onClick = { coachTab = 4 }) { Text(if (zh) "情景对比" else "What-if") }
            TextButton(onClick = leave) { Text(if (vm.reviewing) { if (zh) "下一手" else "Next hand" } else if (zh) "返回" else "Back") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (coachTab == 1 && vm.blindReviewEnabled) {
                BlindReplayCoach(vm, record, nodes)
            } else if (coachTab == 2 && vm.weaknessReportEnabled) {
                WeaknessReportCoach(vm)
            } else if (coachTab == 3 && vm.preflopCoachEnabled) {
                PreflopRangeCoachPanel(vm, nodes)
            } else if (coachTab == 4 && vm.riverWhatIfEnabled) {
                RiverWhatIfCoachPanel(vm, nodes)
            } else if (!report) key(record.id, startIndex) {
                ReplayContent(record, { vm.fmt(it, record.bigBlind) }, leave,
                    { vm.toggleFavorite(record.id)?.let { record = it } }, startIndex,
                    if (vm.reviewing) vm::continueFromReview else null)
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val data = analysis
                if (!loaded) Text(if (zh) "\u8bfb\u53d6\u4e2d" else "Loading", color = Palette.Text)
                else if (data == null) Text(if (zh) "\u6b64\u624b\u6ca1\u6709\u51b3\u7b56\u5f53\u65f6\u7684\u8bc1\u636e\u8bb0\u5f55\u3002\u65e7\u724c\u8c31\u4ecd\u53ef\u6b63\u5e38\u56de\u653e\uff0c\u4e0d\u4f1a\u4f2a\u9020\u5265\u524a\u5efa\u8bae\u3002" else "No contemporaneous evidence was saved for this hand. Legacy replay remains available; retrospective exploit advice is not fabricated.", color = Palette.Text)
                else {
                    Text("${data.algorithmVersion} / ${data.randomVersion}", color = Palette.TextDim)
                    Text(if (zh) "\u57fa\u51c6\u4e0e\u5265\u524a\u5747\u4e3a\u6a21\u578b\u5efa\u8bae\uff0c\u975e\u7ecf\u9a8c\u8bc1\u7684 GTO \u6216\u4fdd\u8bc1\u6536\u76ca\u3002\u8bc1\u636e\u6743\u91cd\u4e0d\u662f\u5efa\u8bae\u6b63\u786e\u7684\u6982\u7387\u3002" else "Heuristic baseline and exploit recommendations, not verified GTO or guaranteed profit. Evidence weight is not the probability that advice is correct.", color = Palette.TextDim)
                    for (trace in data.decisions) {
                        val view = remember(record.id, trace.actionIndex) { runCatching {
                            ObservationFactory.forActor(replayer.engineBeforeAction(trace.actionIndex), record.mode)
                        }.getOrNull() }
                        val matches = view != null && DecisionFingerprint.of(view) == trace.contextHash
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(StudyPresentation.street(trace.street, zh) + " · " +
                                    (if (zh) "决策 #" else "Decision #") + trace.actionIndex,
                                    color = Palette.Gold, style = MaterialTheme.typography.titleMedium)
                                if (view == null || !matches) Text(if (zh) "\u8bb0\u5f55\u4e0e\u51b3\u7b56\u573a\u666f\u4e0d\u5339\u914d\uff0c\u4e0d\u5c55\u793a\u5efa\u8bae\u3002" else "Trace does not match this decision context; advice withheld.")
                                else {
                                    Text("${Cards.format(view.ownHole.toIntArray())} | ${Cards.format(view.board.toIntArray())}")
                                    val actual = record.actions.filter { !it.type.isPost }.getOrNull(trace.actionIndex)
                                    actual?.let { Text((if (zh) "\u4f60\u7684\u884c\u52a8\uff1a" else "Your action: ") + vm.actionLabel(it, record.bigBlind)) }
                                    Text((if (zh) "基准参考：" else "Baseline model: ") +
                                        policyText(trace.baseline, record.bigBlind, zh), color = Palette.Text)
                                    Text((if (zh) "针对性调整：" else "Exploit adjustment: ") +
                                        policyText(trace.exploit, record.bigBlind, zh), color = Palette.Gold)
                                    Text(statusText(trace.status, zh))
                                    for (evidence in trace.evidence) {
                                        Text(reasonText(evidence.code, zh) +
                                            (if (zh) " · 对手座位 " else " · Seat ") + evidence.seat +
                                            (if (zh) " · 观察 " else " · Observed ") +
                                            evidence.successes + "/" + evidence.opportunities +
                                            (if (zh) " · 行为倾向估计 " else " · Behaviour estimate ") +
                                            percent(evidence.posterior) +
                                            (if (zh) " · 样本证据权重 " else " · Evidence weight ") +
                                            percent(evidence.evidenceWeight),
                                            color = Palette.TextDim)
                                    }
                                    if (trace.actionValues.isNotEmpty()) Text(
                                        if (zh) "各行动的条件模型 EV（非真实盈亏）" else "Conditional model EV per action (not realised profit)",
                                        color = Palette.Gold, style = MaterialTheme.typography.labelLarge)
                                    for (value in trace.actionValues) Text(
                                        StudyPresentation.move(value.move, record.bigBlind, zh) +
                                            (if (zh) " · 估计 " else " · estimate ") +
                                            "%.2f".format(java.util.Locale.US, value.chips / record.bigBlind) +
                                            " BB [" + "%.2f".format(java.util.Locale.US, value.low / record.bigBlind) +
                                            ", " + "%.2f".format(java.util.Locale.US, value.high / record.bigBlind) + "]",
                                        color = Palette.TextDim)
                                    trace.notes.forEach {
                                        Text(StudyPresentation.modelNote(it, zh), color = Palette.TextDim)
                                    }
                                    TextButton(onClick = {
                                        startIndex = replayer.frames.indexOfFirst { replayer.actionIndexOf(it) == trace.actionIndex }.coerceAtLeast(0)
                                        report = false
                                    }) { Text(if (zh) "\u67e5\u770b\u5bf9\u5e94\u724c\u9762" else "Show replay at this decision") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun percent(value: Double): String = "%.0f%%".format(value * 100)
private fun policyText(policy: PolicySnapshot, bb: Int, zh: Boolean): String =
    policy.choices.joinToString(" / ") { choice ->
        StudyPresentation.move(choice.move, bb, zh) + " " + percent(choice.probability)
    }
private fun statusText(status: AdviceStatus, zh: Boolean): String = when (status) {
    AdviceStatus.ADJUSTED -> if (zh) "\u5df2\u6309\u8bc1\u636e\u4f5c\u6709\u9650\u8c03\u6574" else "Bounded evidence-based adjustment"
    AdviceStatus.INSUFFICIENT_EVIDENCE -> if (zh) "\u8bc1\u636e\u4e0d\u8db3\uff1a\u4fdd\u7559\u57fa\u51c6" else "Insufficient evidence: baseline retained"
    AdviceStatus.UNSUPPORTED -> if (zh) "\u573a\u666f\u672a\u652f\u6301\uff1a\u4fdd\u7559\u57fa\u51c6" else "Unsupported context: baseline retained"
    AdviceStatus.BASELINE -> if (zh) "\u57fa\u51c6" else "Baseline"
}
private fun reasonText(reason: ReasonCode, zh: Boolean): String = if (!zh) reason.name else when (reason) {
    ReasonCode.LOW_FOLD_RATE -> "\u5f03\u724c\u5c11\uff0c\u51cf\u5c11\u7a7a\u6c14\u8bc8\u552c"
    ReasonCode.WIDE_CALLING -> "\u8ddf\u6ce8\u5bbd\uff0c\u589e\u52a0\u5f3a\u724c\u4ef7\u503c"
    ReasonCode.EXCESS_FOLDING -> "\u5f03\u724c\u504f\u591a\uff0c\u9002\u5ea6\u589e\u52a0\u8bc8\u552c"
    ReasonCode.INDUCE_AGGRESSION -> "\u5f3a\u724c\u8fc7\u724c\u8bf1\u5bfc\u4e0b\u6ce8"
    ReasonCode.PASSIVE_RAISE_CAUTION -> "\u7f55\u89c1\u52a0\u6ce8\uff0c\u5c0f\u5e45\u6536\u7d27\u8fb9\u7f18\u8ddf\u6ce8"
}
