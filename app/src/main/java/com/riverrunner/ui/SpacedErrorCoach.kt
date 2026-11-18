package com.riverrunner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards
import com.riverrunner.engine.MoveType
import com.riverrunner.engine.strategy.*
import java.util.Locale

/** A genuine blind-question/answer-explanation flow; no opponent private cards or future boards. */
@Composable
fun SpacedErrorCoach(vm: AppViewModel) {
    val zh = vm.prefs.chinese
    fun t(cn: String, en: String) = if (zh) cn else en
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
    val due = weak.count { progress.entries[it.id]?.dueDay?.let { it <= day } ?: true }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(t("错题间隔复习", "Spaced error review"), color = Palette.Gold,
                    fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(t("从已保存的模型偏离中出题：盲选 → 解析 → 下次复习",
                    "Saved model deviations → blind answer → explain → schedule"),
                    color = Palette.TextDim, fontSize = 11.sp)
            }
            Text(t("待复习 ", "Due ") + due + " / " + weak.size,
                color = Palette.Gold, fontSize = 12.sp)
        }
        if (loading) {
            Text(t("正在整理决策记录…", "Loading decisions…"), color = Palette.Text)
        } else if (current == null) {
            Text(t("目前没有到期错题。只有经过决策指纹校验的历史模型偏离才会生成题目。",
                "No due questions. Only fingerprint-verified historical model deviations qualify."),
                color = Palette.TextDim)
        } else {
            val a = current
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(.95f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(12.dp)).background(Palette.PanelHi).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(StudyPresentation.topic(a.topic, zh) + " · " + a.position,
                        color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(StudyPresentation.street(a.street, zh) + " · " +
                        t("有效筹码：", "Effective stack: ") + a.stackGroup,
                        color = Palette.TextDim, fontSize = 12.sp)
                    Text(t("你的底牌：", "Your cards: "), color = Palette.TextDim, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        a.ownHole.forEach { PlayingCard(it, 31.dp) }
                    }
                    Text(t("当前公共牌：", "Board now: "), color = Palette.TextDim, fontSize = 12.sp)
                    if (a.board.isEmpty()) Text("-", color = Palette.Text)
                    else Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        a.board.forEach { PlayingCard(it, 26.dp) }
                    }
                    Text(t("底池：", "Pot: ") + "%.1f".format(Locale.US, a.potBB) +
                        " BB  ·  " + t("跟注额：", "To call: ") +
                        "%.1f".format(Locale.US, a.toCallBB) + " BB",
                        color = Palette.Text, fontSize = 12.sp)
                    Text(t("所有信息均来自当时的公开局面；不会泄露未来牌面。",
                        "Only information available at this decision; no future cards."),
                        color = Palette.TextDim, fontSize = 11.sp)
                }
                Column(Modifier.weight(1.05f).fillMaxHeight().verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(12.dp)).background(Palette.PanelHi).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (choice == null) {
                        Text(t("这一步你会怎么选？", "What is your move?"),
                            color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(t("答完才显示模型偏好；这里只比较模型行为，不判定真实 GTO 正误。",
                            "The model preference appears after answering; this is not verified GTO."),
                            color = Palette.TextDim, fontSize = 11.sp)
                        for ((index, type) in a.legalTypes.take(6).withIndex()) {
                            OutlinedButton(onClick = { choice = type }, modifier = Modifier.fillMaxWidth()
                                .heightIn(min = 39.dp), shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Palette.Line),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Palette.Panel, contentColor = Palette.Text)) {
                                Text(('A' + index).toString() + "   " + StudyPresentation.moveType(type, zh))
                            }
                        }
                    } else {
                        val selected = requireNotNull(choice)
                        val correct = selected == a.preferred
                        Text(t("答案解析", "Answer explanation"), color = Palette.Gold,
                            fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(if (correct) t("✓ 与模型偏好相同", "✓ Same as model favourite")
                            else t("△ 和模型首选不同", "△ Different from model favourite"),
                            color = if (correct) Palette.Green else Palette.Gold,
                            fontWeight = FontWeight.Bold)
                        Text(t("你选择：", "Your choice: ") + StudyPresentation.moveType(selected, zh),
                            color = Palette.Text)
                        Text(t("模型首选：", "Model favourite: ") +
                            StudyPresentation.moveType(a.preferred, zh) +
                            t("（频率约 ", " (~") +
                            "%.1f%%".format(Locale.US, 100 * a.preferredFrequency) + ")",
                            color = Palette.Text)
                        a.modelGapBB?.let {
                            Text(t("该历史行动的条件模型 EV 差距：", "Historical conditional model EV gap: ") +
                                "%.2f".format(Locale.US, it) + " BB",
                                color = Palette.Gold, fontSize = 12.sp)
                        }
                        Text(t("模型偏好不等于真实正确答案。下次遇到类似筹码、位置及赔率时，应重新判断具体范围。",
                            "A model preference is not proof of correctness. Re-check range, position and odds next time."),
                            color = Palette.TextDim, fontSize = 11.sp)
                        Button(onClick = {
                            progress = progress.record(a.id, if (correct) 1.0 else 0.0, day)
                            vm.saveSpacedProgress(progress)
                            choice = null
                        }, modifier = Modifier.fillMaxWidth().height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Palette.Gold, contentColor = Palette.Ink)) {
                            Text(t("保存并进入下一题 →", "Save & next question →"))
                        }
                    }
                }
            }
        }
    }
}
