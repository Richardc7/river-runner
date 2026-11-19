package com.riverrunner.ui

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.riverrunner.engine.Move
import com.riverrunner.i18n.LocalStrings

/** Live game screen, wired to the view model. */
@Composable
fun TableScreen(vm: AppViewModel) {
    var confirmExit by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }
    BackHandler { if (showGuide) showGuide = false else confirmExit = true }
    val st = LocalStrings.current
    val ui = vm.tableScreen
    val bb = ui.table?.bigBlind ?: 100
    val view = LocalView.current
    TableContent(
        ui = ui,
        fmt = { vm.fmt(it, bb) },
        onMove = { move ->
            if (vm.heroAct(move) && vm.prefs.actionHaptics) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        },
        onNext = vm::continueToNextHand,
        onFastForward = { vm.fastForward = true },
        onPractice = { vm.practice = it },
        onExit = { confirmExit = true },
        onHelp = { showGuide = true; vm.guideOpenedInGame() },
        onSound = { vm.setSound(!vm.soundOn) },
        onStar = vm::toggleStarHand,
        onReview = vm::setReviewEach,
    )
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            confirmButton = { TextButton({ confirmExit = false; vm.requestQuit() }) { Text(st.leaveTable, color = Palette.Red) } },
            dismissButton = { TextButton({ confirmExit = false }) { Text(st.keepPlaying) } },
            title = { Text(st.exitConfirmTitle) },
            text = { Text(st.exitConfirmText) },
        )
    }
    if (showGuide) {
        Dialog(onDismissRequest = { showGuide = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            GuideContent(onClose = { showGuide = false }, startTab = GuideTab.GLOSSARY)
        }
    }
}

@Composable
fun TableContent(
    ui: TableScreenUi,
    fmt: (Int) -> String,
    onMove: (Move) -> Unit,
    onNext: () -> Unit,
    onFastForward: () -> Unit,
    onPractice: (Boolean) -> Unit,
    onExit: () -> Unit,
    onHelp: () -> Unit,
    animate: Boolean = true,
    onSound: () -> Unit = {},
    onStar: () -> Unit = {},
    onReview: (Boolean) -> Unit = {},
) {
    val st = LocalStrings.current
    val haptic = LocalHapticFeedback.current
    val turnKey = ui.legal?.let { "${ui.handNumber}:${ui.log.size}" }
    LaunchedEffect(turnKey) {
        if (turnKey != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Bg)) {
        val sideWidth = (maxWidth * .29f).coerceIn(208.dp, 278.dp)
        Column(Modifier.fillMaxSize()) {
            TableToolbar(ui, onPractice, onReview, onSound, onStar, onHelp, onExit)
            Row(Modifier.weight(1f)) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 5.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val table = ui.table
                        if (table != null) {
                            PokerTable(
                                table, fmt, st.pot, Modifier.fillMaxSize(),
                                animateDeal = animate, showTotalPot = true,
                            )
                        }
                        TableNotice(
                            ui.notice,
                            Modifier.align(Alignment.TopCenter).padding(top = 54.dp),
                        )
                    }
                    TableStatusBar(ui, fmt)
                }
                SidePanel(ui, fmt, sideWidth, onMove, onNext, onFastForward)
            }
        }
    }
}

@Composable
private fun TableNotice(notice: String?, modifier: Modifier) {
    AnimatedVisibility(
        notice != null,
        modifier = modifier,
        enter = slideInVertically { -it } + fadeIn(),
        exit = fadeOut(tween(400)),
    ) {
        Text(
            notice.orEmpty(), color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, lineHeight = 18.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Gold)
                .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun TableToolbar(
    ui: TableScreenUi,
    onPractice: (Boolean) -> Unit,
    onReview: (Boolean) -> Unit,
    onSound: () -> Unit,
    onStar: () -> Unit,
    onHelp: () -> Unit,
    onExit: () -> Unit,
) {
    val st = LocalStrings.current
    val accent = LocalTableStyle.current.controlAccent
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(lerp(Palette.Bg, accent, .11f), Palette.Panel, Palette.Bg)))
            .drawBehind {
                drawLine(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = .55f), accent.copy(alpha = .08f))),
                    Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx(),
                )
            }.padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(st.handNo(ui.handNumber), color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(ui.blindsText, color = Palette.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ui.levelText?.let { Text(it, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1) }
        }
        TableToggle(if (st.isZh) "练习" else "Coach", ui.practice, onPractice)
        TableToggle(if (st.isZh) "每手复盘" else "Review each", ui.reviewEach, onReview)
        RoundIcon(if (ui.starred) "★" else "☆", st.starHand, onStar)
        TableToolbarIcon(st.sound, onSound) { TableSpeakerIcon(ui.soundOn) }
        RoundIcon("?", st.guide, onHelp)
        RoundIcon("✕", st.exit, onExit)
    }
}

@Composable
private fun TableToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val accent = LocalTableStyle.current.controlAccent
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.height(36.dp).clip(shape)
            .background(if (checked) lerp(Palette.Bg, accent, .12f) else Palette.Bg)
            .border(1.dp, accent.copy(alpha = if (checked) .48f else .16f), shape)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(if (checked) accent else Palette.TextDim.copy(alpha = .55f)))
        Text(label, color = Palette.Text, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun TableStatusBar(ui: TableScreenUi, fmt: (Int) -> String) {
    val st = LocalStrings.current
    val practice = ui.practiceInfo?.takeIf { ui.practice && ui.legal != null }
    val live = ui.liveStrategy?.takeIf { ui.legal != null }
    val accent = LocalTableStyle.current.controlAccent
    var showDetails by remember(ui.table?.handKey, ui.legal) { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(bottom = 4.dp).heightIn(min = 52.dp)
            .liveSurface(accent, shape)
    ) {
        val wide = maxWidth >= (500 * LocalDensity.current.fontScale).dp
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Column(
                Modifier.weight(1f).drawBehind {
                    drawLine(
                        accent.copy(alpha = .8f), Offset(0f, 2.dp.toPx()),
                        Offset(0f, size.height - 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round,
                    )
                }.padding(start = 8.dp)
            ) {
                Text(if (st.isZh) "最近行动" else "Latest action", color = accent, fontSize = 11.sp, lineHeight = 13.sp)
                Text(
                    ui.log.lastOrNull { it.kind == LogKind.ACTION }?.text ?: "—",
                    color = Palette.Text, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (wide && practice != null) {
                Spacer(Modifier.width(1.dp).height(26.dp).background(accent.copy(alpha = .2f)))
                PracticeMetrics(practice, fmt)
            }
            if (live != null || (!wide && practice != null)) {
                val label = if (live != null) {
                    if (st.isZh) "策略参考" else "Strategy"
                } else if (st.isZh) "练习数据" else "Coaching"
                LiveActionButton(label, accent, Modifier.height(38.dp).defaultMinSize(minWidth = 70.dp)) {
                    showDetails = true
                }
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(if (st.isZh) "策略与数据" else "Strategy & odds") },
            modifier = Modifier.widthIn(max = 500.dp).fillMaxWidth(.94f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (practice != null && live?.view == null) PracticeHud(practice, fmt)
                    if (live != null) {
                        LiveStrategyDetails(live, ui.table?.bigBlind ?: 100, ui.legal?.isOpeningBet == true)
                    }
                }
            },
            confirmButton = { TextButton({ showDetails = false }) { Text(st.back) } },
        )
    }
}

@Composable
private fun LiveStrategyDetails(live: LiveStrategyHint, bb: Int, openingBet: Boolean) {
    val zh = LocalStrings.current.isZh
    val external = live.label == "declared-reference"
    val title = if (external) {
        if (zh) "导入策略" else "Imported strategy"
    } else if (zh) "启发式模型" else "Heuristic model"
    val choices = live.choices.filter { it.probability > 0 }.sortedByDescending { it.probability }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Palette.Gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (external) Text(live.source, color = Palette.TextDim, fontSize = 12.sp)
        live.view?.let { DecisionContextDetails(it, modelEquity = live.modelEquity) }
        StrategyFrequencyBar(
            choices.groupBy { it.move.type }.map { (type, moves) ->
                type to moves.sumOf { it.probability }
            }
        )
        for (choice in choices) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(StudyPresentation.move(choice.move, bb, zh, openingBet), color = Palette.Text, fontSize = 14.sp)
                Text(Format.pct(choice.probability), color = strategyActionColor(choice.move.type), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun PracticeMetrics(info: PracticeInfo, fmt: (Int) -> String) {
    val st = LocalStrings.current
    var showSpr by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TableMetric(st.toCall, fmt(info.toCall))
        if (info.toCall > 0) {
            TableMetric(st.potOdds, "1 : %.1f".format(info.pot.toDouble() / info.toCall))
            TableMetric(if (st.isZh) "所需胜率" else "Needed", Format.pct(info.potOdds))
        }
        TableMetric(
            st.spr + " ⓘ", "%.1f".format(info.spr),
            Modifier.clickable(onClickLabel = if (st.isZh) "查看筹码底池比的解释" else "Explain SPR") { showSpr = true },
        )
        TableMetric(if (st.isZh) "牌型" else "Hand", info.handText)
    }
    if (showSpr) {
        AlertDialog(
            onDismissRequest = { showSpr = false },
            title = { Text(if (st.isZh) "SPR · 筹码底池比" else "SPR · Stack-to-pot ratio") },
            text = {
                Text(
                    if (st.isZh) "常用算法是有效筹码除以底池。桌面读数用你的剩余筹码除以当前底池。如果单挑对手更短，用双方较小的剩余筹码手算。"
                    else "Effective SPR is the effective stack divided by the pot. This live readout uses your own remaining stack. In heads-up play, use the smaller stack if your opponent is shorter."
                )
            },
            confirmButton = { TextButton(onClick = { showSpr = false }) { Text(st.back) } },
        )
    }
}

@Composable
private fun TableMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Palette.TextDim, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1)
        Text(
            value, color = Palette.Text, fontSize = 15.sp, lineHeight = 17.sp,
            fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}

@Composable
private fun RoundIcon(text: String, label: String, onClick: () -> Unit) {
    TableToolbarIcon(label, onClick) { Text(text, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun TableToolbarIcon(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val accent = LocalTableStyle.current.controlAccent
    Box(
        Modifier.size(36.dp).pressable(onClick = onClick).clip(CircleShape)
            .background(lerp(Palette.Bg, accent, .05f)).border(1.dp, accent.copy(alpha = .25f), CircleShape)
            .semantics { contentDescription = label; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun TableSpeakerIcon(enabled: Boolean) {
    Canvas(Modifier.size(17.dp)) {
        val d = size.width / 18f
        val c = Palette.Text
        drawPath(
            Path().apply {
                moveTo(1f * d, 6f * d)
                lineTo(4f * d, 6f * d)
                lineTo(8f * d, 3f * d)
                lineTo(8f * d, 15f * d)
                lineTo(4f * d, 12f * d)
                lineTo(1f * d, 12f * d)
                close()
            },
            c,
        )
        if (enabled) {
            for (radius in listOf(4f, 7f)) {
                drawArc(
                    c, -50f, 100f, false, Offset((9f - radius) * d, (9f - radius) * d),
                    Size(radius * 2 * d, radius * 2 * d), style = Stroke(1.1f * d, cap = StrokeCap.Round),
                )
            }
        } else {
            drawLine(c, Offset(11f * d, 6f * d), Offset(16f * d, 12f * d), 1.4f * d, StrokeCap.Round)
            drawLine(c, Offset(16f * d, 6f * d), Offset(11f * d, 12f * d), 1.4f * d, StrokeCap.Round)
        }
    }
}

@Composable
private fun PracticeHud(info: PracticeInfo, fmt: (Int) -> String) {
    val st = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        HudRow(st.toCall, fmt(info.toCall), null, null)
        if (info.toCall > 0) {
            HudRow(st.potOdds, "1 : %.1f".format(info.pot.toDouble() / info.toCall), st.needEquity, Format.pct(info.potOdds))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(info.handText, color = Palette.Gold, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${st.spr} ", color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
            Text("%.1f".format(info.spr), color = Palette.Text, fontSize = 14.sp, lineHeight = 15.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HudRow(l1: String, v1: String, l2: String?, v2: String?) {
    Row(Modifier.fillMaxWidth()) {
        Row(Modifier.weight(1f)) {
            Text(l1, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
            Text(v1, color = Palette.Text, fontSize = 14.sp, lineHeight = 15.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f)) {
            if (l2 != null && v2 != null) {
                Text(l2, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text(v2, color = Palette.Text, fontSize = 14.sp, lineHeight = 15.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
