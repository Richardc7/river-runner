package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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

data class TrainingItem(
    val drill: TrainingDrill,
    val record: HandRecord,
    val view: DecisionView,
    val baseline: PolicySnapshot,
    val notes: List<String>,
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

    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack) { Text(t("← 返回", "← Back")) }
            Column(Modifier.weight(1f)) {
                Text(t("专项训练中心", "Training centre"), fontSize = 20.sp,
                    fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(t("情景判断 → 选择行动 → 解析 → 下一题",
                    "Scenario → Choose → Explain → Next"), color = Palette.TextDim, fontSize = 11.sp)
            }
            Text(t("已练习 ", "Completed ") + completed, color = Palette.Gold,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                t("决策答题", "Decision quiz"),
                t("错题复习", "Spaced review"),
                t("GTO 策略参考", "GTO strategy reference"),
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
                    else DecisionQuestion(vm, item, reference, answer, onSelect = { answer = it }, onNext = {
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
            "请在「自定义对手及规则」中打开「保存决策时的证据与双轨建议」并保存。",
            "Open Customize opponents / rules, enable recording of decision evidence, and save.",
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
            if (!config.recordAdvice || config.cashRules.hasRake) vm.openEditionSettings()
            else vm.screen = Screen.TrainingSetup
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
    vm: AppViewModel,
    item: TrainingItem,
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

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(.95f).fillMaxHeight().verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(14.dp)).background(Palette.Panel).padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (explanation) t("本题回顾", "Question review") else t("决策情景", "Decision scenario"),
                color = Palette.Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(t("历史牌局", "Saved hand") + " #" + item.record.handNumber +
                "  ·  " + StudyPresentation.street(view.street, zh),
                color = Palette.TextDim, fontSize = 12.sp)
            HorizontalDivider(color = Palette.Line)
            Text(t("你的底牌", "Your cards"), color = Palette.TextDim, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                view.ownHole.forEach { PlayingCard(it, 32.dp) }
            }
            Text(t("当时可见的公共牌", "Board at decision time"),
                color = Palette.TextDim, fontSize = 12.sp)
            if (view.board.isEmpty()) Text(t("翻牌前尚无公共牌", "No public cards yet"), color = Palette.Text)
            else Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                view.board.forEach { PlayingCard(it, 27.dp) }
            }
            val position = HandEngine.positionName(view.order.indexOf(view.actorSeat), view.order.size)
            Text(t("位置：", "Position: ") + position +
                "  ·  " + t("有效人数：", "Players: ") + view.seats.size,
                color = Palette.Text, fontSize = 12.sp)
            Text(t("底池：", "Pot: ") + "%.1f".format(Locale.US, view.pot.toDouble() / bb) +
                " BB  ·  " + t("跟注额：", "To call: ") +
                "%.1f".format(Locale.US, view.legal.toCall.toDouble() / bb) + " BB",
                color = Palette.Text, fontSize = 13.sp)
            Text(t("只使用决策时的信息，不泄露对手底牌或未来公共牌。",
                "Only decision-time information; no hidden opponent cards or future board."),
                color = Palette.TextDim, fontSize = 11.sp)
        }
        Column(Modifier.weight(1.05f).fillMaxHeight().verticalScroll(rememberScrollState())
            .clip(RoundedCornerShape(14.dp)).background(Palette.Panel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            if (!explanation) {
                Text(t("你会选择哪个行动？", "What would you do?"),
                    color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(t("先作答，下一屏再看模型解析。选项来自真实记录，不会伪造行动 EV。",
                    "Choose first; the model explanation follows. Options are saved, not invented."),
                    color = Palette.TextDim, fontSize = 11.sp)
                for ((index, option) in options.withIndex()) {
                    OutlinedButton(
                        onClick = { onSelect(option.move) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Palette.Line),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Text,
                            containerColor = Palette.PanelHi),
                    ) {
                        Text(('A' + index).toString() + "   " + StudyPresentation.move(option.move, bb, zh),
                            fontSize = 13.sp)
                    }
                }
                Text(t("此题有 ", "Available answers: ") + options.size +
                    t(" 个已建模的行动", " model-valued moves"), color = Palette.TextDim, fontSize = 11.sp)
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
                Text(t("你的选择：", "Your answer: ") + StudyPresentation.move(selected, bb, zh),
                    color = Palette.Text, fontSize = 12.sp)
                Text(t("模型首选：", "Model-favoured: ") + StudyPresentation.move(best.move, bb, zh),
                    color = Palette.Text, fontSize = 12.sp)
                Text(t("条件模型 EV 差距：", "Conditional model EV gap: ") +
                    "%.2f".format(Locale.US, score.modelLossChips / bb) + " BB",
                    color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = Palette.Line)
                for (option in options.sortedByDescending { it.chips }) {
                    Text(StudyPresentation.move(option.move, bb, zh) + "  ·  " +
                        "%.2f".format(Locale.US, option.chips / bb) + " BB",
                        color = if (option.move == best.move) Palette.Green else Palette.TextDim,
                        fontSize = 12.sp)
                }
                for (note in item.notes.take(3)) {
                    Text(StudyPresentation.modelNote(note, zh), color = Palette.TextDim, fontSize = 11.sp)
                }
                SolverReferencePanel(view, reference)
                ExportSolverSpot(view)
                Text(t("这里比较的是模型假设下的 EV，并非真实盈亏或求解器 GTO。",
                    "Model-assumption EV, not realised profit or verified solver GTO."),
                    color = Palette.TextDim, fontSize = 11.sp)
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Gold,
                        contentColor = Palette.Ink)) {
                    Text(t("保存练习记录 · 下一题 →", "Save answer · Next question →"))
                }
            }
        }
    }
}
