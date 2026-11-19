package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.engine.*
import com.riverrunner.engine.strategy.*
import java.util.Locale
import kotlin.math.roundToInt

data class TrainingItem(
    val drill: TrainingDrill,
    val record: HandRecord,
    val view: DecisionView,
    val baseline: PolicySnapshot,
    val notes: List<String>,
    val modelVersion: String? = null,
)

/** Separate questions, answers and offline data management. Never show unseen cards. */
@Composable
fun TrainingScreen(vm: AppViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val zh = LocalStrings.current.isZh
    fun t(cn: String, en: String) = if (zh) cn else en
    var items by remember(vm.profile.id) { mutableStateOf<List<TrainingItem>>(emptyList()) }
    var progress by remember(vm.profile.id) { mutableStateOf(TrainingProgress()) }
    var reference by remember(vm.profile.id) { mutableStateOf<ReferenceBook?>(null) }
    var loading by remember(vm.profile.id) { mutableStateOf(true) }
    var tab by remember(vm.profile.id) { mutableIntStateOf(0) }
    var answer by remember(vm.profile.id) { mutableStateOf<Move?>(null) }

    LaunchedEffect(vm.profile.id) {
        reference = vm.referenceForTraining()
        items = vm.trainingItems()
        progress = vm.loadTrainingProgress()
        loading = false
    }
    val completed = progress.categories.values.sumOf { it.attempts }
    val exact = progress.categories.values.sumOf { it.exact }

    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text(t("← 返回", "← Back")) }
            Text(t("专项训练中心", "Training centre"), modifier = Modifier.weight(1f), fontSize = 20.sp,
                fontWeight = FontWeight.Bold, color = Palette.Text)
            if (completed > 0) {
                val accuracy = exact.toFloat() / completed
                CircularProgressIndicator(progress = { accuracy }, modifier = Modifier.size(24.dp),
                    color = Palette.Gold, trackColor = Palette.Line, strokeWidth = 3.dp)
                Column {
                    Text(t("模型首选一致率", "Model agreement") + " ${(accuracy * 100).roundToInt()}%",
                        color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(t("累计练习 $completed 次", "$completed total attempts"),
                        color = Palette.TextDim, fontSize = 11.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                t("决策答题", "Decision quiz"),
                t("错题复习", "Spaced review"),
                t("求解器参考", "Solver reference"),
            ).forEachIndexed { index, title ->
                FilterChip(selected = tab == index, onClick = { tab = index },
                    label = { Text(title, fontSize = 12.sp) })
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                0 -> {
                    val current = TrainingCurriculum.next(items.map { it.drill }, progress)
                    val item = items.firstOrNull { it.drill.id == current?.id }
                    if (loading) TrainingEmpty(t("正在准备练习…", "Preparing questions…"))
                    else if (item == null) RiverTrainingEmpty(vm)
                    else DecisionQuestion(item, progress.categories[item.drill.category], reference, answer, onSelect = { answer = it }, onNext = {
                        val selected = answer ?: return@DecisionQuestion
                        progress = progress.record(item.drill.grade(selected))
                        vm.saveTrainingProgress(progress)
                        answer = null
                    })
                }
                1 -> if (vm.spacedDrillsEnabled) {
                    Column(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                        .background(Palette.Panel).padding(10.dp)) {
                        SpacedErrorCoach(vm)
                    }
                } else TrainingEmpty(t("请在设置中开启「错题与间隔复习」。",
                    "Enable spaced error drills in Settings."))
                else -> SolverReferenceLibrary(vm, reference) { reference = it }
            }
        }
    }
}

@Composable
private fun RiverTrainingEmpty(vm: AppViewModel) {
    val zh = LocalStrings.current.isZh
    fun t(cn: String, en: String) = if (zh) cn else en
    val config = vm.editionConfig
    val (guidance, action) = when {
        !config.recordAdvice -> t(
            "请在「自定义 AI、对手与规则」中打开「保存决策时的证据与双轨建议」并保存。",
            "Open Customize AI, opponents and rules, enable recording of decision evidence, and save.",
        ) to t("去开启记录", "Enable recording")
        config.cashRules.hasRake -> t(
            "决策记录已开启。河牌题需要无抽水的筹码局，请在规则中将抽水设为 0 并保存。",
            "Recording is on. River drills need an unraked cash game. Set rake to 0 in the rules and save.",
        ) to t("前往规则设置", "Open rules")
        else -> t(
            "已开启决策记录。开一桌无抽水的单挑筹码局，打完一手河牌再回来。面对下注或在有利位置行动，更容易生成题目。",
            "Recording is on. Play an unraked heads-up cash hand through the river. Acting in position " +
                "or facing a bet is more likely to yield a scored question. Return here after the hand is saved.",
        ) to t("配置单挑筹码局", "Set up heads-up cash")
    }
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Palette.Panel)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(t("还没有河牌练习", "No river drills yet"), color = Palette.Text,
            fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(guidance, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.widthIn(max = 490.dp))
        Button(onClick = {
            when {
                !config.recordAdvice -> vm.openEditionSettings()
                config.cashRules.hasRake -> vm.openEditionSettings(showCashRules = true)
                else -> vm.screen = Screen.TrainingSetup
            }
        }) { Text(action) }
    }
}

@Composable
private fun TrainingEmpty(message: String) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(Palette.Panel)
        .padding(22.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Palette.TextDim, fontSize = 14.sp)
    }
}

@Composable
internal fun DecisionQuestion(
    item: TrainingItem,
    categoryProgress: CategoryProgress?,
    reference: ReferenceBook?,
    picked: Move?,
    onSelect: (Move) -> Unit,
    onNext: () -> Unit,
) {
    val zh = LocalStrings.current.isZh
    fun t(cn: String, en: String) = if (zh) cn else en
    val d = item.drill
    val view = item.view
    val options = remember(d) { StudyPresentation.choices(d) }
    val explanation = picked != null
    val bb = item.record.bigBlind.coerceAtLeast(1)
    val toCall = view.legal.toCall
    val potOdds = if (toCall > 0) toCall.toDouble() / (view.pot + toCall) else null
    var showDetails by remember(d.id) { mutableStateOf(false) }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(.95f).fillMaxHeight().verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(14.dp)).background(Palette.Panel).padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (explanation) t("本题回顾", "Question review") else t("决策情景", "Decision scenario"),
                color = Palette.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(t("历史牌局", "Saved hand") + " #" + item.record.handNumber +
                "  ·  " + StudyPresentation.street(view.street, zh),
                color = Palette.TextDim, fontSize = 12.sp)
            HorizontalDivider(color = Palette.Line)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuestionMetric(t("底池", "Pot"), "%.1f BB".format(Locale.US, view.pot.toDouble() / bb), Modifier.weight(1f))
                QuestionMetric(t("跟注额", "To call"), "%.1f BB".format(Locale.US, toCall.toDouble() / bb), Modifier.weight(1f))
                if (potOdds != null) {
                    QuestionMetric(t("跟注所需胜率", "Equity needed"), "%.0f%%".format(Locale.US, 100 * potOdds), Modifier.weight(1f))
                }
            }
            DecisionCards(view)
            val position = HandEngine.positionName(view.order.indexOf(view.actorSeat), view.order.size)
            Text(t("位置：", "Position: ") + position +
                "  ·  " + t("有效人数：", "Players: ") + view.seats.size,
                color = Palette.Text, fontSize = 12.sp)
            val scenario = if (toCall > 0) t("河牌面对下注", "facing a river bet")
                else t("河牌可过牌或下注", "checking or betting the river")
            val attempts = categoryProgress?.attempts ?: 0
            Text(if (attempts == 0) t("选中这手：$scenario，你还没练过这类题。",
                "Why this hand: $scenario, from a category you haven’t practised.")
            else t(
                "选中这手：$scenario，此类题的模型首选一致次数为 ${categoryProgress?.exact}/$attempts。",
                "Why this hand: $scenario. You matched the model on ${categoryProgress?.exact}/$attempts attempts in this category.",
            ), color = Palette.TextDim, fontSize = 12.sp)
        }
        val answerScroll = key(d.id, explanation) { rememberScrollState() }
        Column(Modifier.weight(1.05f).fillMaxHeight()
            .clip(RoundedCornerShape(14.dp)).background(Palette.Panel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.weight(1f).verticalScroll(answerScroll),
                verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (!explanation) {
                    Text(t("你会选择哪个行动？", "What would you do?"),
                        color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    val scrollHint = if (answerScroll.maxValue > 0 && answerScroll.maxValue < Int.MAX_VALUE)
                        t(" · 向下滑动查看其余选项", " · Scroll for more choices") else ""
                    Text(t("${options.size} 个选项", "${options.size} choices") + scrollHint,
                        color = Palette.TextDim, fontSize = 12.sp)
                    for (pair in options.chunked(2)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (option in pair) {
                                OutlinedButton(
                                    onClick = { onSelect(option.move) },
                                    modifier = Modifier.weight(1f).heightIn(min = 45.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text,
                                        containerColor = Palette.PanelHi),
                                ) {
                                    Text(StudyPresentation.move(option.move, bb, zh, view.legal.isOpeningBet), fontSize = 14.sp)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                } else {
                    val selected = requireNotNull(picked)
                    val score = d.grade(selected)
                    val best = d.best
                    Text(t("答案解析", "Answer explanation"),
                        color = Palette.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(if (score.exact) t("✓ 与模型首选一致", "✓ Matches model favourite")
                         else t("△ 可进一步研究的行动", "△ Review this decision"),
                        color = if (score.exact) Palette.Green else Palette.Gold,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(t("你的选择：", "Your answer: ") + StudyPresentation.move(selected, bb, zh, view.legal.isOpeningBet),
                        color = Palette.Text, fontSize = 12.sp)
                    Text(t("模型首选：", "Model-favoured: ") + StudyPresentation.move(best.move, bb, zh, view.legal.isOpeningBet),
                        color = Palette.Text, fontSize = 12.sp)
                    Text(t("条件模型 EV 差距：", "Conditional model EV gap: ") +
                        "%.2f".format(Locale.US, score.modelLossChips / bb) + " BB",
                        color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    SolverReferencePanel(view, reference)
                    ExportSolverSpot(view)
                    HorizontalDivider(color = Palette.Line)
                    for (option in options.sortedByDescending { it.chips }) {
                        Text(StudyPresentation.move(option.move, bb, zh, view.legal.isOpeningBet) + "  ·  " +
                            "%.2f".format(Locale.US, option.chips / bb) + " BB",
                            color = if (option.move == best.move) Palette.Green else Palette.TextDim,
                            fontSize = 12.sp)
                    }
                    TextButton(onClick = { showDetails = !showDetails }) {
                        Text(if (showDetails) t("收起详情", "Hide details") else t("模型与牌谱详情", "Model and hand details"))
                    }
                    if (showDetails) {
                        Text(t("牌谱 ID", "Hand ID") + " · " + item.record.id, color = Palette.TextDim, fontSize = 11.sp)
                        item.modelVersion?.let { Text(it, color = Palette.TextDim, fontSize = 11.sp) }
                        for (note in item.notes) {
                            Text(StudyPresentation.modelNote(note, zh), color = Palette.TextDim, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (explanation) {
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold,
                        contentColor = Palette.Ink)) {
                    Text(t("保存练习记录 · 下一题 →", "Save answer · Next question →"))
                }
            }
        }
    }
}

@Composable
private fun QuestionMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 11.sp)
        Text(value, color = Palette.Gold, fontFamily = Numeric, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}
