package com.riverrunner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.engine.*
import com.riverrunner.engine.strategy.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.min

private fun number(value: Double) = "%.2f".format(Locale.US, value)

data class HistoricalDecision(val view: DecisionView, val trace: DecisionTrace, val action: Action?)

/** No post-decision public cards or opponent private cards are passed into these panels. */
fun historicalDecisions(record: HandRecord, analysis: HandAnalysis?, replay: Replayer): List<HistoricalDecision> {
    if (analysis == null || analysis.handId != record.id) return emptyList()
    val voluntary = record.actions.filter { !it.type.isPost }
    return analysis.decisions.filter { it.seat == record.heroSeat }.mapNotNull { trace ->
        val view = runCatching {
            ObservationFactory.forActor(replay.engineBeforeAction(trace.actionIndex), record.mode)
        }.getOrNull() ?: return@mapNotNull null
        if (view.actorSeat != record.heroSeat || DecisionFingerprint.of(view) != trace.contextHash) return@mapNotNull null
        HistoricalDecision(view, trace, voluntary.getOrNull(trace.actionIndex))
    }
}

private fun studyChoices(view: DecisionView): List<Move> = buildList {
    val legal = view.legal
    if (legal.canCheck) add(Move.CHECK) else add(Move.FOLD)
    if (legal.canCall) add(Move.CALL)
    if (legal.canRaise) {
        add(BetSizing.target(legal, .5))
        add(BetSizing.target(legal, 1.0))
    }
}.distinct()

private fun studyMoveLabel(move: Move, bb: Int, zh: Boolean): String =
    StudyPresentation.move(move, bb, zh)

internal fun blindDecisions(record: HandRecord, nodes: List<HistoricalDecision>): List<HistoricalDecision> {
    val exitFoldIndex = if (record.autoFoldedOnExit) {
        record.actions.filterNot { it.type.isPost }.indexOfLast { it.seat == record.heroSeat }
    } else -1
    return nodes.filter { it.action != null && it.trace.actionIndex != exitFoldIndex }
}

@Composable
internal fun DecisionCards(view: DecisionView) {
    val zh = LocalStrings.current.isZh
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (zh) "你的底牌" else "Your cards", color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                view.ownHole.forEach { PlayingCard(it, 27.dp) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (zh) "当时可见的公共牌" else "Board at decision time", color = Palette.TextDim, fontSize = 12.sp, lineHeight = 16.sp)
            if (view.board.isEmpty()) {
                Text(if (zh) "翻牌前尚无公共牌" else "No public cards yet", color = Palette.Text, fontSize = 12.sp, lineHeight = 16.sp)
            } else Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                view.board.forEach { PlayingCard(it, 25.dp) }
            }
        }
    }
}

@Composable
fun BlindReplayCoach(vm: AppViewModel, record: HandRecord, nodes: List<HistoricalDecision>) {
    val zh = LocalStrings.current.isZh
    fun t(cn: String, en: String) = if (zh) cn else en
    var index by remember(record.id) { mutableIntStateOf(0) }
    var answer by remember(record.id) { mutableStateOf<Move?>(null) }
    var progress by remember(vm.profile.id) { mutableStateOf(ReviewProgress()) }
    LaunchedEffect(vm.profile.id) { progress = vm.loadSpacedProgress() }
    val node = nodes.getOrNull(index.coerceIn(0, (nodes.size - 1).coerceAtLeast(0)))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("盲测复盘 · 先选后看", "Blind replay · answer before reveal"),
            style = MaterialTheme.typography.titleMedium, color = Palette.Gold)
        Text(t("作答时只显示当时可见的牌面和行动。", "Answer using the cards and actions visible at the time."),
            color = Palette.TextDim)
        if (node == null) {
            Text(t("这手牌没有可作答的决策。可以切换到牌局回放查看牌局。",
                "This hand has no quiz decisions. Open the street replay to view it."), color = Palette.Text)
        } else {
            val view = node.view
            val trace = node.trace
            val chosen = answer
            Text(t("决策", "Decision") + " " + (index + 1) + "/" + nodes.size + " · " +
                StudyPresentation.street(view.street, zh),
                color = Palette.Text, fontWeight = FontWeight.Bold)
            DecisionCards(view)
            val position = HandEngine.positionName(view.order.indexOf(view.actorSeat), view.order.size)
            Text(t("位置：", "Position: ") + position +
                t(" · 底池：", " · Pot: ") + number(view.pot.toDouble() / view.bigBlind) + " BB" +
                t(" · 跟注额：", " · To call: ") + number(view.legal.toCall.toDouble() / view.bigBlind) + " BB",
                color = Palette.TextDim)
            Text(t("此前公开行动：", "Public actions so far: ") +
                (view.actions.filter { !it.type.isPost }.takeLast(8).joinToString(" / ") {
                    it.seat.toString() + ": " + StudyPresentation.actionType(it.type, zh) +
                        if (it.type.isAggressive) " " + number(it.streetTotal.toDouble() / view.bigBlind) + "BB" else ""
                }.ifBlank { "-" }), color = Palette.TextDim)
            Text(t("你会怎么打？", "What would you do?"), color = Palette.Gold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (move in studyChoices(view)) {
                    OutlinedButton(onClick = { answer = move }, enabled = chosen == null) {
                        Text(StudyPresentation.move(move, view.bigBlind, zh, view.legal.isOpeningBet))
                    }
                }
            }
            if (chosen != null) {
                val policy = if (trace.status == AdviceStatus.ADJUSTED) trace.exploit else trace.baseline
                val best = policy.choices.groupBy { it.move.type }.mapValues { (_, options) -> options.sumOf { it.probability } }
                    .maxBy { it.value }.key
                val score = if (chosen.type == best) 1.0 else 0.0
                Text(t("模型偏好的行动：", "Model-favoured move: ") +
                    StudyPresentation.moveType(best, zh, view.legal.isOpeningBet) +
                    t("；你选：", "; your pick: ") + StudyPresentation.moveType(chosen.type, zh, view.legal.isOpeningBet),
                    color = Palette.Text)
                Text(t("模型混合频率：", "Model mixed action frequencies: ") + policy.choices.joinToString(" / ") {
                    StudyPresentation.moveType(it.move.type, zh, view.legal.isOpeningBet) + " " + number(it.probability * 100) + "%"
                }, color = Palette.TextDim)
                node.action?.let {
                    Text(t("实际历史行动：", "Original recorded move: ") +
                        StudyPresentation.move(StudyCoach.moveOf(it) ?: Move.FOLD, view.bigBlind, zh, view.legal.isOpeningBet),
                        color = Palette.TextDim)
                }
                val modeled = trace.actionValues.firstOrNull { it.move == chosen }
                if (modeled != null && trace.actionValues.size >= 2) {
                    val gap = (trace.actionValues.maxOf { it.chips } - modeled.chips).coerceAtLeast(0.0) / view.bigBlind
                    Text(t("条件模型 EV 差：", "Conditional model EV gap: ") + number(gap) + " BB",
                        color = Palette.Gold)
                } else Text(t("本节点没有可靠的行动 EV 比较，不计算虚构的损失。",
                    "No supported action EV comparison; no fabricated loss estimate."), color = Palette.TextDim)
                Button(onClick = {
                    val today = System.currentTimeMillis() / 86_400_000L
                    progress = progress.record("h" + record.id + "a" + trace.actionIndex, score, today)
                    vm.saveSpacedProgress(progress)
                    answer = null
                    index = (index + 1) % nodes.size
                }) { Text(t("记录并复习下一题", "Save and review next")) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { index = (index - 1 + nodes.size) % nodes.size }) { Text(t("上一题", "Previous")) }
                    OutlinedButton(onClick = { index = (index + 1) % nodes.size }) { Text(t("下一题", "Next")) }
                }
            }
        }
    }
}

@Composable
fun WeaknessReportCoach(vm: AppViewModel) {
    val zh = LocalStrings.current.isZh
    var data by remember(vm.profile.id) { mutableStateOf<List<DecisionAssessment>?>(null) }
    LaunchedEffect(vm.profile.id) { data = vm.studyAssessments() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (zh) "个人弱点：决策时模型偏离报告" else "Personal weaknesses: decision-time model deviation",
            style = MaterialTheme.typography.titleMedium, color = Palette.Gold)
        Text(if (zh) "根据已保存的决策，按位置和情景汇总模型偏离。"
            else "Review model deviations from saved decisions by position and situation.",
            color = Palette.TextDim)
        val rows = data
        if (rows == null) Text(if (zh) "分析历史中…" else "Analysing saved hands…", color = Palette.Text)
        else if (rows.isEmpty()) Text(if (zh) "暂无具有决策时证据的历史牌局。" else "No saved contemporaneous decisions.", color = Palette.Text)
        else {
            Text((if (zh) "有效决策：" else "Valid decisions: ") + rows.size +
                (if (zh) " · 待复习：" else " · Flagged for review: ") + rows.count { it.needsStudy },
                color = Palette.Text)
            for (category in StudyCoach.categories(rows).take(35)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(StudyPresentation.category(category.key, zh),
                            color = Palette.Text, fontWeight = FontWeight.Bold)
                        Text((if (zh) "样本 " else "Sample ") + category.hands +
                            (if (zh) " · 偏离 " else " · Flags ") + category.marked +
                            " (" + number(category.reviewRate * 100) + "%)", color = Palette.TextDim)
                        if (category.measuredEv > 0) Text(
                            (if (zh) "模型 EV 可计算：" else "Model EV measured: ") + category.measuredEv +
                                (if (zh) " · 平均差距 " else " · Mean model gap ") +
                                number(category.meanModelGapBB) + " BB", color = Palette.TextDim)
                    }
                }
            }
        }
    }
}

@Composable
fun PreflopRangeCoachPanel(vm: AppViewModel, nodes: List<HistoricalDecision>) {
    val zh = LocalStrings.current.isZh
    val relevant = remember(nodes) { nodes.filter { it.view.street == Street.PREFLOP } }
    var selected by remember { mutableIntStateOf(0) }
    val node = relevant.getOrNull(selected.coerceIn(0, (relevant.size - 1).coerceAtLeast(0)))
    val result = node?.let { PreflopStudy.forDecision(it.view) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(if (zh) "九人桌翻前范围教练" else "Nine-handed preflop range coach",
            style = MaterialTheme.typography.titleMedium, color = Palette.Gold)
        Text(if (zh) "13×13 启发式行动倾向矩阵，按位置、玩家数量和有效筹码计算。"
            else "13×13 heuristic action tendencies by position, table size and effective stack.",
            color = Palette.TextDim)
        if (node == null || result == null) {
            Text(if (zh) "没有已保存的翻前决策。" else "No saved preflop decision.", color = Palette.Text)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected = (selected - 1 + relevant.size) % relevant.size }) {
                    Text(if (zh) "上一决策" else "Previous")
                }
                Text((selected + 1).toString() + "/" + relevant.size, color = Palette.Text)
                OutlinedButton(onClick = { selected = (selected + 1) % relevant.size }) {
                    Text(if (zh) "下一决策" else "Next")
                }
            }
            Text(result.position + " · " + result.effectiveBB + "BB · " +
                (if (zh) "前序加注 " else "Prior raises ") + result.facingRaises +
                " · " + (if (zh) "范围宽度 ~" else "Width ~") + number(result.targetWidth * 100) + "%",
                color = Palette.Text)
            Text((if (zh) "你拿到：" else "Your hand: ") + result.ownClass +
                " · " + Cards.format(node.view.ownHole.toIntArray()), color = Palette.Gold)
            Text(StudyPresentation.modelNote(result.explanation, zh), color = Palette.TextDim)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (row in result.cells) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (cell in row) {
                            val bg = when {
                                cell.raiseFrequency >= .75 -> Palette.CallBtn
                                cell.raiseFrequency >= .25 -> Palette.GoldDim
                                else -> Palette.PanelHi
                            }
                            Box(Modifier.width(44.dp).height(30.dp).background(bg).padding(2.dp)) {
                                Text(cell.name + " " + (cell.raiseFrequency * 100).toInt() + "%",
                                    color = Palette.Text, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RiverWhatIfCoachPanel(vm: AppViewModel, nodes: List<HistoricalDecision>) {
    val zh = LocalStrings.current.isZh
    val candidates = remember(nodes) { nodes.filter {
        it.view.street == Street.RIVER && it.view.mode == GameMode.CASH && it.view.opponents.size == 1
    } }
    var index by remember { mutableIntStateOf(0) }
    val node = candidates.getOrNull(index.coerceIn(0, (candidates.size - 1).coerceAtLeast(0)))
    var size by remember(node?.trace?.contextHash) { mutableFloatStateOf(.75f) }
    var fold by remember(node?.trace?.contextHash) { mutableFloatStateOf(.4f) }
    var strength by remember(node?.trace?.contextHash) { mutableFloatStateOf(0f) }
    var stacks by remember(node?.trace?.contextHash) { mutableFloatStateOf(
        node?.view?.let { v -> min(v.actor.stack + v.actor.committed,
            v.opponents.single().stack + v.opponents.single().committed).toFloat() / v.bigBlind }?.coerceIn(20f, 300f) ?: 100f
    ) }
    var generation by remember(node?.trace?.contextHash) { mutableIntStateOf(0) }
    var running by remember(node?.trace?.contextHash) { mutableStateOf(false) }
    var outcome by remember(node?.trace?.contextHash) { mutableStateOf<RiverWhatIfResult?>(null) }
    LaunchedEffect(node?.trace?.contextHash, generation) {
        val current = node ?: return@LaunchedEffect
        running = true; outcome = null
        val input = RiverWhatIfInput(size.toDouble(), fold.toDouble(), strength.toDouble(), stacks.toInt())
        outcome = withContext(Dispatchers.Default) {
            val opponent = current.view.opponents.single()
            val model = current.trace.opponents.firstOrNull { it.seat == opponent.seat }
            runCatching {
                val range = RangeModel.infer(current.view, opponent.seat, model)
                RiverWhatIf.explore(current.view, range, input)
            }.getOrElse { RiverWhatIfResult(false, null, null, emptyList(), it.message ?: "Model unavailable") }
        }
        running = false
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(if (zh) "河牌条件情景推演" else "River what-if sensitivity",
            style = MaterialTheme.typography.titleMedium, color = Palette.Gold)
        Text(if (zh) "只分析支持的单挑现金局河牌，输入改变的是模型假设，不改变真实历史。无抽水之外的情况将拒绝计算。"
            else "Supported heads-up cash river only. Controls change assumptions, not actual history. Raked situations are refused.",
            color = Palette.TextDim)
        if (node == null) Text(if (zh) "历史中没有支持的单挑河牌节点。" else "No eligible heads-up river nodes.", color = Palette.Text)
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { index = (index - 1 + candidates.size) % candidates.size }) {
                    Text(if (zh) "上一节点" else "Previous")
                }
                Text((index + 1).toString() + "/" + candidates.size, color = Palette.Text)
                OutlinedButton(onClick = { index = (index + 1) % candidates.size }) {
                    Text(if (zh) "下一节点" else "Next")
                }
            }
            Text((if (zh) "下注比例：" else "Bet size: ") + number(size.toDouble()) + " pot", color = Palette.Text)
            Slider(value = size, onValueChange = { size = it; outcome = null }, valueRange = .25f..2f)
            Text((if (zh) "对手弃牌率：" else "Opponent fold rate: ") + number(fold * 100.0) + "%", color = Palette.Text)
            Slider(value = fold, onValueChange = { fold = it; outcome = null }, valueRange = 0f..0.8f)
            Text((if (zh) "对手范围强弱偏移：" else "Range strength shift: ") + number(strength.toDouble()), color = Palette.Text)
            Slider(value = strength, onValueChange = { strength = it; outcome = null }, valueRange = -1.5f..1.5f)
            Text((if (zh) "假设的有效筹码：" else "Hypothetical effective stacks: ") + stacks.toInt() + "BB", color = Palette.Text)
            Slider(value = stacks, onValueChange = { stacks = it; outcome = null }, valueRange = 20f..300f)
            Button(onClick = { generation++ }) { Text(if (zh) "重新计算情景" else "Recompute scenario") }
            if (running) Text(if (zh) "模型运算中…" else "Computing model…", color = Palette.Text)
            val result = outcome
            if (result != null) {
                if (!result.supported) Text((if (zh) "暂不支持：" else "Unsupported: ") + StudyPresentation.modelNote(result.note, zh),
                    color = Palette.TextDim)
                else {
                    Text((if (zh) "选定尺度模型 EV：" else "Selected size model EV: ") +
                        (result.chosenEv?.let { number(it.chips / node.view.bigBlind) + " BB" } ?: "-"),
                        color = Palette.Gold)
                    for (ev in result.comparison.take(12)) Text(
                        studyMoveLabel(ev.move, node.view.bigBlind, zh) + ": " +
                            number(ev.chips / node.view.bigBlind) + " BB [" +
                            number(ev.low / node.view.bigBlind) + ", " +
                            number(ev.high / node.view.bigBlind) + "]", color = Palette.Text)
                    Text(StudyPresentation.modelNote(result.note, zh), color = Palette.TextDim)
                }
            }
        }
    }
}
