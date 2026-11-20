package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.engine.*
import com.riverrunner.engine.strategy.*

private enum class ReplayPage(val zh: String, val en: String) {
    TABLE("牌局回放", "Replay"),
    COMPARISON("双轨分析", "Policy comparison"),
    BLIND("盲测复盘", "Blind quiz"),
    WEAKNESSES("弱点报告", "Weaknesses"),
    RANGE("翻前范围", "Preflop range"),
    WHAT_IF("情景对比", "What-if");

    fun title(chinese: Boolean) = if (chinese) zh else en
}

@Composable
fun EditionReplayScreen(vm: AppViewModel, initial: HandRecord, startBlind: Boolean = false) {
    var record by remember(initial.id) { mutableStateOf(initial) }
    var page by remember(initial.id, startBlind) {
        mutableStateOf(if (startBlind && vm.blindReviewEnabled) ReplayPage.BLIND else ReplayPage.TABLE)
    }
    var frameIndex by remember(initial.id) { mutableIntStateOf(0) }
    var analysis by remember(initial.id) { mutableStateOf<HandAnalysis?>(null) }
    var reference by remember(initial.id) { mutableStateOf<ReferenceBook?>(null) }
    var loaded by remember(initial.id) { mutableStateOf(false) }
    LaunchedEffect(initial.id) { analysis = vm.analysisFor(initial); loaded = true }
    LaunchedEffect(page, initial.id) { if (page == ReplayPage.COMPARISON) reference = vm.referenceForTraining() }
    val leave: () -> Unit = { if (vm.reviewing) vm.continueFromReview() else vm.screen = Screen.History }
    BackHandler(onBack = leave)
    val st = LocalStrings.current
    val zh = st.isZh
    val replayer = remember(initial.id) { Replayer(initial) }
    val nodes = remember(record.id, analysis) { historicalDecisions(record, analysis, replayer) }
    val quizNodes = remember(record.id, nodes) { blindDecisions(record, nodes) }
    val tools = buildList {
        if (vm.blindReviewEnabled && (quizNodes.isNotEmpty() || page == ReplayPage.BLIND)) add(ReplayPage.BLIND)
        if (vm.weaknessReportEnabled) add(ReplayPage.WEAKNESSES)
        if (vm.preflopCoachEnabled) add(ReplayPage.RANGE)
        if (vm.riverWhatIfEnabled) add(ReplayPage.WHAT_IF)
    }
    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        ReplayNavigation(page, tools, if (vm.reviewing) null else leave) { page = it }
        if (page != ReplayPage.BLIND && record.rake > 0) {
            Text((if (zh) "本手抽水 " else "Hand rake ") + vm.fmt(record.rake, record.bigBlind),
                color = Palette.TextDim, modifier = Modifier.padding(horizontal = 12.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (page == ReplayPage.BLIND) {
                if (loaded) BlindReplayCoach(vm, record, quizNodes)
                else Text(
                    if (zh) "正在读取盲测决策…" else "Loading blind replay decisions…",
                    color = Palette.TextDim, modifier = Modifier.padding(12.dp),
                )
            } else if (page == ReplayPage.WEAKNESSES) {
                WeaknessReportCoach(vm)
            } else if (page == ReplayPage.RANGE) {
                PreflopRangeCoachPanel(vm, nodes)
            } else if (page == ReplayPage.WHAT_IF) {
                RiverWhatIfCoachPanel(vm, nodes)
            } else if (page == ReplayPage.TABLE) {
                ReplayContent(
                    record = record, fmt = { vm.fmt(it, record.bigBlind) }, onBack = null,
                    onStar = { vm.toggleFavorite(record.id)?.let { record = it } },
                    startIndex = frameIndex, decisions = nodes, onFrameChanged = { frameIndex = it },
                )
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val data = analysis
                if (!loaded) Text(if (zh) "\u8bfb\u53d6\u4e2d" else "Loading", color = Palette.Text)
                else if (data == null) Text(if (zh) "\u6b64\u624b\u6ca1\u6709\u51b3\u7b56\u5f53\u65f6\u7684\u8bc1\u636e\u8bb0\u5f55\u3002\u65e7\u724c\u8c31\u4ecd\u53ef\u6b63\u5e38\u56de\u653e\uff0c\u4e0d\u4f1a\u4f2a\u9020\u5265\u524a\u5efa\u8bae\u3002" else "No contemporaneous evidence was saved for this hand. Legacy replay remains available; retrospective exploit advice is not fabricated.", color = Palette.Text)
                else {
                    for (trace in data.decisions) {
                        val view = remember(record.id, trace.actionIndex) { runCatching {
                            ObservationFactory.forActor(replayer.engineBeforeAction(trace.actionIndex), record.mode)
                        }.getOrNull() }
                        val matches = view != null && DecisionFingerprint.of(view) == trace.contextHash
                        Box(Modifier.fillMaxWidth().studioCard(accent = Palette.Gold, radius = 14.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(StudyPresentation.street(trace.street, zh) + " · " +
                                    (if (zh) "决策 #" else "Decision #") + trace.actionIndex,
                                    color = Palette.Gold, style = MaterialTheme.typography.titleMedium)
                                if (view == null || !matches) Text(if (zh) "\u8bb0\u5f55\u4e0e\u51b3\u7b56\u573a\u666f\u4e0d\u5339\u914d\uff0c\u4e0d\u5c55\u793a\u5efa\u8bae\u3002" else "Trace does not match this decision context; advice withheld.")
                                else {
                                    DecisionCards(view)
                                    val actual = record.actions.filter { !it.type.isPost }.getOrNull(trace.actionIndex)
                                    actual?.let { Text((if (zh) "\u4f60\u7684\u884c\u52a8\uff1a" else "Your action: ") + vm.actionLabel(it, record.bigBlind)) }
                                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                                        if (maxWidth >= 640.dp) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                                Box(Modifier.weight(1.5f)) {
                                                    DecisionContextDetails(view, actual, trace.baseline.equity)
                                                }
                                                ReplayPolicyComparison(trace, record.bigBlind, view.legal.isOpeningBet,
                                                    Modifier.weight(1f))
                                            }
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                DecisionContextDetails(view, actual, trace.baseline.equity)
                                                ReplayPolicyComparison(trace, record.bigBlind, view.legal.isOpeningBet)
                                            }
                                        }
                                    }
                                    actual?.let {
                                        StudyCoach.assess(record.id, view, trace, it).modelGapBB?.let { gap ->
                                            Text((if (zh) "相对模型最优 EV · " else "EV versus model best · ") +
                                                "%.2f BB".format(if (gap < .005) 0.0 else -gap), color = Palette.Gold)
                                        }
                                    }
                                    Text(statusText(trace.status, zh))
                                    SolverReferencePanel(view, reference)
                                    ExportSolverSpot(view)
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
                                        StudyPresentation.move(value.move, record.bigBlind, zh, view.legal.isOpeningBet) +
                                            (if (zh) " · 估计 " else " · estimate ") +
                                            "%.2f".format(java.util.Locale.US, value.chips / record.bigBlind) +
                                            " BB [" + "%.2f".format(java.util.Locale.US, value.low / record.bigBlind) +
                                            ", " + "%.2f".format(java.util.Locale.US, value.high / record.bigBlind) + "]",
                                        color = Palette.TextDim)
                                    trace.notes.forEach {
                                        Text(StudyPresentation.modelNote(it, zh), color = Palette.TextDim)
                                    }
                                    TextButton(onClick = {
                                        frameIndex = replayer.frames.indexOfFirst { replayer.actionIndexOf(it) == trace.actionIndex }.coerceAtLeast(0)
                                        page = ReplayPage.TABLE
                                    }) { Text(if (zh) "\u67e5\u770b\u5bf9\u5e94\u724c\u9762" else "Show replay at this decision") }
                                }
                            }
                        }
                    }
                    if (data.decisions.isNotEmpty()) {
                        Text(if (zh) "分析模型与牌谱详情" else "Analysis details",
                            color = Palette.TextDim, style = MaterialTheme.typography.labelMedium)
                        Text("${data.algorithmVersion} / ${data.randomVersion}", color = Palette.TextDim,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (vm.reviewing) {
            Box(Modifier.fillMaxWidth().background(Palette.Panel).padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterEnd) {
                ActionButton(st.reviewNext, Palette.Gold, Modifier.width(224.dp).height(40.dp),
                    textColor = Palette.Ink, onClick = vm::continueFromReview)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplayNavigation(
    page: ReplayPage,
    tools: List<ReplayPage>,
    onExit: (() -> Unit)?,
    onPage: (ReplayPage) -> Unit,
) {
    val st = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow {
                listOf(ReplayPage.TABLE, ReplayPage.COMPARISON).forEachIndexed { index, target ->
                    SegmentedButton(
                        selected = page == target, onClick = { onPage(target) },
                        shape = SegmentedButtonDefaults.itemShape(index, count = 2),
                        modifier = Modifier.widthIn(min = ((if (st.isZh) 91 else 104) * fontScale).dp),
                        icon = {},
                    ) { Text(target.title(st.isZh), maxLines = 1) }
                }
            }
            if (tools.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        val label = if (page in tools) page.title(st.isZh)
                        else if (st.isZh) "训练工具" else "Study tools"
                        Text("$label ▾", maxLines = 1)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        tools.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.title(st.isZh)) },
                                onClick = { expanded = false; onPage(target) },
                            )
                        }
                    }
                }
            }
        }
        if (onExit != null) TextButton(onClick = onExit) { Text("← " + st.back) }
    }
}

private fun percent(value: Double): String = "%.0f%%".format(value * 100)

@Composable
private fun ReplayPolicyComparison(
    trace: DecisionTrace, bb: Int, openingBet: Boolean, modifier: Modifier = Modifier,
) {
    val zh = LocalStrings.current.isZh
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ReplayPolicyChart(if (zh) "基准参考" else "Baseline model", trace.baseline, bb, openingBet, Modifier.weight(1f))
        ReplayPolicyChart(if (zh) "针对性调整" else "Exploit adjustment", trace.exploit, bb, openingBet, Modifier.weight(1f))
    }
}

@Composable
private fun ReplayPolicyChart(label: String, policy: PolicySnapshot, bb: Int, openingBet: Boolean, modifier: Modifier) {
    val zh = LocalStrings.current.isZh
    val choices = policy.choices.filter { it.probability > 0 }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Palette.Text, style = MaterialTheme.typography.labelLarge)
        StrategyFrequencyBar(choices.groupBy { it.move.type }.map { (type, moves) ->
            type to moves.sumOf { it.probability }
        })
        for (choice in choices.sortedByDescending { it.probability }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(StudyPresentation.move(choice.move, bb, zh, openingBet), color = Palette.Text)
                Text(Format.pct(choice.probability), color = strategyActionColor(choice.move.type))
            }
        }
    }
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
    ReasonCode.COUNTER_EXCESS_CBET_FOLD -> "\u5bf9\u624b C-Bet \u5f03\u724c\u8fc7\u9ad8\uff0c\u9002\u5ea6\u589e\u52a0\u65bd\u538b"
    ReasonCode.COUNTER_OVER_BLUFF_STAB -> "\u5bf9\u624b\u9ad8\u9891\u5077\u6c60\uff0c\u589e\u52a0\u6293\u9e21\u8ddf\u6ce8"
}
