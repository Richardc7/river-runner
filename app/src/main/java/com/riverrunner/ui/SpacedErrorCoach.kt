package com.riverrunner.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riverrunner.engine.Cards
import com.riverrunner.engine.MoveType
import com.riverrunner.engine.strategy.*
import java.util.Locale

/** No future public cards, hidden villain cards or realized outcomes enter the drills. */
@Composable
fun SpacedErrorCoach(vm: AppViewModel) {
    val zh = vm.prefs.chinese
    var loading by remember(vm.profile.id) { mutableStateOf(true) }
    var assessments by remember(vm.profile.id) { mutableStateOf<List<DecisionAssessment>>(emptyList()) }
    var progress by remember(vm.profile.id) { mutableStateOf(ReviewProgress()) }
    var choice by remember(vm.profile.id) { mutableStateOf<MoveType?>(null) }
    LaunchedEffect(vm.profile.id) {
        assessments = vm.studyAssessments()
        progress = vm.loadSpacedProgress()
        loading = false
    }
    val day = System.currentTimeMillis() / 86_400_000L
    val weak = assessments.filter { it.needsStudy }
    val current = progress.next(weak, day, onlyWeak = true)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (zh) "错题间隔复习 · 根据个人偏离自动生成"
            else "Spaced mistakes · automatically from flagged model deviations",
            color = Palette.Gold, style = MaterialTheme.typography.titleMedium)
        Text(if (zh) "先盲选，再揭示模型首选；下一次出现时间由本次练习表现决定。这里不是 GTO 正误评分。"
            else "Answer blindly, then see the model preference. Next review is scheduled by performance, not GTO certainty.",
            color = Palette.TextDim)
        val due = weak.count { progress.entries[it.id]?.dueDay?.let { it <= day } ?: true }
        Text((if (zh) "待复习 " else "Due ") + due + " / " + weak.size +
            (if (zh) " · 已练习 " else " · Practised ") + progress.entries.size, color = Palette.Text)
        if (loading) Text(if (zh) "整理历史中…" else "Loading historical decisions…", color = Palette.Text)
        else if (current == null) Text(
            if (zh) "目前没有到期错题。只有具有决策时证据且符合模型偏离条件的历史牌谱会生成题目。"
            else "No due flagged decisions. Only saved, fingerprint-matched model deviations become exercises.",
            color = Palette.TextDim
        ) else {
            val a = current
            Text(a.topic + " · " + a.position + " · " + a.street + " · " + a.stackGroup,
                color = Palette.Text)
            Text((if (zh) "自己的牌：" else "Hole: ") + Cards.format(a.ownHole.toIntArray()) +
                (if (zh) " · 当前公共牌：" else " · Current board: ") +
                (if (a.board.isEmpty()) "-" else Cards.format(a.board.toIntArray())), color = Palette.Text)
            Text((if (zh) "底池：" else "Pot: ") + "%.2f".format(Locale.US, a.potBB) +
                "BB · " + (if (zh) "跟注额：" else "Call: ") + "%.2f".format(Locale.US, a.toCallBB) + "BB",
                color = Palette.TextDim)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (move in a.legalTypes) OutlinedButton(onClick = { choice = move }, enabled = choice == null) {
                    Text(move.name)
                }
            }
            if (choice != null) {
                val picked = choice!!
                val score = if (picked == a.preferred) 1.0 else 0.0
                Text((if (zh) "模型偏好的行动：" else "Model-favoured action: ") + a.preferred.name +
                    (if (zh) " · 模型频率约 " else " · model freq ~") +
                    "%.1f%%".format(Locale.US, 100 * a.preferredFrequency),
                    color = Palette.Text)
                Text(if (picked == a.preferred) {
                    if (zh) "你选择了模型最高频行动。" else "You chose the model's most frequent action."
                } else {
                    if (zh) "与模型最常选行动不同，建议复习；不代表一定损失筹码。"
                    else "Different from the model's modal action; review it, but no certain chip loss is claimed."
                }, color = Palette.TextDim)
                if (a.modelGapBB != null) Text((if (zh) "历史条件模型 EV 差：" else "Historical conditional model gap: ") +
                    "%.2f".format(Locale.US, a.modelGapBB) + " BB", color = Palette.Gold)
                Button(onClick = {
                    progress = progress.record(a.id, score, day)
                    vm.saveSpacedProgress(progress)
                    choice = null
                }) {
                    Text(if (zh) "记录并安排下一次复习" else "Record and schedule next review")
                }
            }
        }
    }
}
