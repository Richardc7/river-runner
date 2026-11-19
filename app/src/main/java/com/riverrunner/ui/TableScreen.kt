package com.riverrunner.ui

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import com.riverrunner.engine.Cards
import com.riverrunner.engine.LegalActions
import com.riverrunner.engine.Move
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.i18n.Strings
import kotlin.math.roundToInt

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
        val sideWidth = (maxWidth * .28f).coerceIn(202.dp, 270.dp)
        Column(Modifier.fillMaxSize()) {
            TableToolbar(ui, onPractice, onReview, onSound, onStar, onHelp, onExit)
            Row(Modifier.weight(1f)) {
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 5.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val table = ui.table
                        if (table != null) {
                            PokerTable(table, fmt, st.pot, Modifier.fillMaxSize(),
                                animateDeal = animate, showTotalPot = true)
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            ui.notice != null,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 54.dp),
                            enter = androidx.compose.animation.slideInVertically { -it } + fadeIn(),
                            exit = fadeOut(tween(400)),
                        ) {
                            Text(
                                ui.notice.orEmpty(), color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 18.sp,
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Gold)
                                    .border(2.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    TableStatusBar(ui, fmt)
                }
                SidePanel(ui, fmt, sideWidth, onMove, onNext, onFastForward)
            }
        }
    }
}

@Composable
private fun TableToolbar(
    ui: TableScreenUi, onPractice: (Boolean) -> Unit, onReview: (Boolean) -> Unit,
    onSound: () -> Unit, onStar: () -> Unit, onHelp: () -> Unit, onExit: () -> Unit,
) {
    val st = LocalStrings.current
    val accent = LocalTableStyle.current.controlAccent
    Row(Modifier.fillMaxWidth()
        .background(Brush.horizontalGradient(listOf(lerp(Palette.Bg, accent, .11f), Palette.Panel, Palette.Bg)))
        .drawBehind {
            drawLine(Brush.horizontalGradient(listOf(accent.copy(alpha = .55f), accent.copy(alpha = .08f))),
                Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Row(Modifier.height(34.dp).clip(shape)
        .background(if (checked) lerp(Palette.Bg, accent, .1f) else Palette.Bg)
        .border(1.dp, accent.copy(alpha = if (checked) .45f else .14f), shape)
        .toggleable(checked, role = Role.Switch, onValueChange = onChange)
        .padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
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
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 4.dp).height(51.dp)
        .liveSurface(accent, shape)) {
        val wide = maxWidth >= (500 * LocalDensity.current.fontScale).dp
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.weight(1f).drawBehind {
                drawLine(accent.copy(alpha = .8f), Offset(0f, 2.dp.toPx()),
                    Offset(0f, size.height - 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }.padding(start = 8.dp)) {
                Text(if (st.isZh) "最近行动" else "Latest action", color = accent, fontSize = 11.sp, lineHeight = 13.sp)
                Text(ui.log.lastOrNull { it.kind == LogKind.ACTION }?.text ?: "—",
                    color = Palette.Text, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
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
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (practice != null) PracticeHud(practice, fmt)
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
private fun SidePanel(
    ui: TableScreenUi, fmt: (Int) -> String, width: Dp,
    onMove: (Move) -> Unit, onNext: () -> Unit, onFastForward: () -> Unit,
) {
    val st = LocalStrings.current
    val heroTurn = ui.legal != null
    val accent = LocalTableStyle.current.controlAccent
    val shape = RoundedCornerShape(11.dp)
    Column(
        Modifier.width(width).fillMaxHeight().padding(start = 2.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)
            .liveSurface(accent, shape, emphasized = heroTurn).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val over = ui.handOver
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.width(3.dp).height(13.dp).clip(CircleShape).background(accent))
            Text(
                if (over != null) { if (st.isZh) "本手结算" else "Hand settled" } else if (st.isZh) "行动记录" else "Action log",
                color = Palette.Text, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (over != null) HandOverPanel(over, fmt)
            else ActionLog(ui.log, Modifier.fillMaxSize(),
                highlightIndex = ui.log.indexOfLast { it.kind == LogKind.ACTION })
        }
        // bottom: controls
        AnimatedContent(
            targetState = when {
                ui.legal != null -> 0
                ui.handOver != null -> 1
                else -> 2
            },
            transitionSpec = {
                (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(120))
            },
            label = "controls",
        ) { mode ->
            when (mode) {
                0 -> ui.legal?.let { HeroControls(it, fmt, ui.table?.bigBlind ?: 100, onMove) }
                1 -> LiveActionButton(
                    if (ui.sessionOver) st.viewResults else st.nextHand + "  ›", accent,
                    modifier = Modifier.fillMaxWidth().height(46.dp), emphasized = true, onClick = onNext,
                )
                else -> WaitingBar(ui, onFastForward)
            }
        }
    }
}

private fun Modifier.liveSurface(accent: Color, shape: RoundedCornerShape, emphasized: Boolean = false): Modifier =
    clip(shape)
        .background(Brush.verticalGradient(listOf(lerp(Palette.Panel, accent, .075f), Palette.Bg)))
        .border(1.dp, accent.copy(alpha = if (emphasized) .57f else .24f), shape)

@Composable
private fun LiveActionButton(label: String, accent: Color, modifier: Modifier, sub: String? = null,
    emphasized: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val colors = if (emphasized) listOf(lerp(accent, Color.White, .18f), lerp(accent, Palette.Ink, .17f))
        else listOf(lerp(Palette.PanelHi, accent, .12f), Palette.Bg)
    val ink = if (emphasized) Palette.Ink else accent
    Column(modifier.clip(shape).background(Brush.verticalGradient(colors))
        .border(1.dp, accent.copy(alpha = if (emphasized) .9f else .45f), shape)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(label, color = ink, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) Text(sub, color = if (emphasized) ink else Palette.Text,
            fontSize = 14.sp, lineHeight = 16.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
        StrategyFrequencyBar(choices.groupBy { it.move.type }.map { (type, moves) ->
            type to moves.sumOf { it.probability }
        })
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
        TableMetric(st.spr + " ⓘ", "%.1f".format(info.spr),
            Modifier.clickable(onClickLabel = if (st.isZh) "查看筹码底池比的解释" else "Explain SPR") { showSpr = true })
        TableMetric(if (st.isZh) "牌型" else "Hand", info.handText)
    }
    if (showSpr) AlertDialog(
        onDismissRequest = { showSpr = false },
        title = { Text(if (st.isZh) "SPR · 筹码底池比" else "SPR · Stack-to-pot ratio") },
        text = { Text(if (st.isZh) "有效筹码除以底池。比值越小，剩余筹码越容易在接下来的下注中投入底池。"
            else "Effective stack divided by pot. A smaller ratio makes it easier to commit the remaining stack.") },
        confirmButton = { TextButton(onClick = { showSpr = false }) { Text(st.back) } },
    )
}

@Composable
private fun TableMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = Palette.TextDim, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1)
        Text(value, color = Palette.Text, fontSize = 15.sp, lineHeight = 17.sp,
            fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun pulse(): Float {
    val tr = rememberInfiniteTransition(label = "panelPulse")
    val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    return v
}

@Composable
private fun RoundIcon(text: String, label: String, onClick: () -> Unit) {
    TableToolbarIcon(label, onClick) { Text(text, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun TableToolbarIcon(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val accent = LocalTableStyle.current.controlAccent
    Box(
        Modifier.size(34.dp).pressable(onClick = onClick).clip(CircleShape)
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
        drawPath(Path().apply {
            moveTo(1f * d, 6f * d)
            lineTo(4f * d, 6f * d)
            lineTo(8f * d, 3f * d)
            lineTo(8f * d, 15f * d)
            lineTo(4f * d, 12f * d)
            lineTo(1f * d, 12f * d)
            close()
        }, c)
        if (enabled) {
            for (radius in listOf(4f, 7f)) {
                drawArc(c, -50f, 100f, false, Offset((9f - radius) * d, (9f - radius) * d),
                    Size(radius * 2 * d, radius * 2 * d), style = Stroke(1.1f * d, cap = StrokeCap.Round))
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

@Composable
fun ActionLog(lines: List<LogLine>, modifier: Modifier = Modifier, highlightIndex: Int = -1, onClick: ((Int) -> Unit)? = null) {
    val state = rememberLazyListState()
    val accent = LocalTableStyle.current.controlAccent
    var viewportHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(lines.size, highlightIndex, viewportHeight) {
        val target = if (onClick != null && highlightIndex in lines.indices) highlightIndex else lines.lastIndex
        if (viewportHeight > 0 && target >= 0) state.scrollToItem(target)
    }
    LazyColumn(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Palette.Bg, lerp(Palette.Bg, accent, .035f))))
            .border(1.dp, accent.copy(alpha = .12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 5.dp, vertical = 4.dp)
            .onSizeChanged { viewportHeight = it.height },
        state = state,
    ) {
        itemsIndexed(lines) { i, l ->
            val hl = i == highlightIndex
            val mod = Modifier.fillMaxWidth()
                .then(if (hl) Modifier.clip(RoundedCornerShape(5.dp))
                    .background(lerp(Palette.Bg, accent, .14f)).drawBehind {
                        drawLine(accent, Offset(0f, 3.dp.toPx()), Offset(0f, size.height - 3.dp.toPx()), 2.dp.toPx())
                    } else Modifier)
                .then(if (onClick != null) Modifier.pressable { onClick(i) } else Modifier)
                .padding(vertical = 4.dp, horizontal = 4.dp)
            when (l.kind) {
                LogKind.STREET -> Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Palette.TextDim, fontWeight = FontWeight.Bold)) { append("${l.text}  ") }
                        for (c in l.cards) {
                            withStyle(SpanStyle(color = logSuitColor(c), fontWeight = FontWeight.Bold)) { append(rankText(c) + suitText(c) + " ") }
                        }
                    },
                    fontSize = 13.sp, lineHeight = 17.sp, modifier = mod.padding(top = 4.dp),
                )
                LogKind.ACTION -> Text(l.text, color = if (hl) accent else Palette.Text, fontSize = 14.sp, lineHeight = 18.sp, modifier = mod.padding(start = 3.dp))
                LogKind.RESULT -> Text(l.text, color = Palette.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = mod)
            }
        }
    }
}

private fun logSuitColor(card: Int) = when (Cards.suit(card)) {
    0 -> Color(0xFF6FD08C); 1 -> Color(0xFF7DB2F0); 2 -> Color(0xFFF07C7C); else -> Color(0xFFE8E8E8)
}

@Composable
private fun HandOverPanel(over: HandOverUi, fmt: (Int) -> String) {
    val st = LocalStrings.current
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Palette.Bg).padding(8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(st.handResult, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            val net = over.heroNet
            Text(
                (if (net > 0) "+" else if (net < 0) "−" else "") + fmt(kotlin.math.abs(net)),
                color = if (net > 0) Palette.Green else if (net < 0) Palette.Red else Palette.TextDim,
                fontSize = 17.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold,
            )
        }
        for (w in over.winners) {
            Text("🏆 ${st.winsFor(w.name)} ${fmt(w.amount)}", color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        if (over.byFold) Text(st.othersFolded, color = Palette.TextDim, fontSize = 11.sp)
        if (over.showdown.isNotEmpty()) {
            Text(st.showdown, color = Palette.TextDim, fontSize = 11.sp)
            for (s in over.showdown) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.name, color = if (s.won > 0) Palette.Green else Palette.Text, fontSize = 12.sp,
                        fontWeight = if (s.won > 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                        modifier = Modifier.width(52.dp),
                    )
                    PlayingCard(s.cards[0], 18.dp)
                    Spacer(Modifier.width(2.dp))
                    PlayingCard(s.cards[1], 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(s.handName, color = if (s.won > 0) Palette.Green else Palette.TextDim, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WaitingBar(ui: TableScreenUi, onFastForward: () -> Unit) {
    val st = LocalStrings.current
    val accent = LocalTableStyle.current.controlAccent
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThinkingDots()
            Spacer(Modifier.width(6.dp))
            Text(
                ui.actingName?.let { st.thinking(it) } ?: st.dealing, color = Palette.TextDim, fontSize = 11.sp, maxLines = 1,
            )
        }
        LiveActionButton(
            (if (ui.heroInHand) st.fastForward else st.skipHand) + "  ››",
            accent, modifier = Modifier.fillMaxWidth().height(40.dp),
            emphasized = ui.fastForward, onClick = onFastForward,
        )
    }
}

@Composable
private fun ThinkingDots() {
    val tr = rememberInfiniteTransition(label = "dots")
    val p by tr.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "d")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until 3) {
            val on = p.toInt() == i
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (on) Palette.Accent else Palette.PanelHi2))
        }
    }
}

// ------------------------------------------------------------------ betting controls

private fun presets(st: Strings, la: LegalActions, bb: Int): List<Pair<String, Int>> {
    val out = ArrayList<Pair<String, Int>>()
    when {
        la.isOpeningBet -> {
            out += "1/3" to (la.pot / 3.0).roundToInt()
            out += "1/2" to (la.pot / 2.0).roundToInt()
            out += "3/4" to (la.pot * 0.75).roundToInt()
            out += st.potShort to la.pot
        }
        la.currentBet <= bb -> {
            // unopened or limped pot preflop
            val limps = ((la.pot - la.currentBet * 2) / bb).coerceAtLeast(0)
            out += "2.5x" to (bb * 2.5).roundToInt() + limps * bb
            out += "3x" to bb * 3 + limps * bb
            out += "4x" to bb * 4 + limps * bb
        }
        else -> {
            out += "2.5x" to (la.currentBet * 2.5).roundToInt()
            out += "3x" to la.currentBet * 3
            out += st.potShort to la.currentBet + la.pot + la.toCall
        }
    }
    val cleaned = out.map { it.first to it.second.coerceIn(la.minRaiseTo, la.maxRaiseTo) }
        .filter { it.second < la.maxRaiseTo }
        .distinctBy { it.second }
    return cleaned + (st.allIn to la.maxRaiseTo)
}

@Composable
private fun HeroControls(la: LegalActions, fmt: (Int) -> String, bb: Int, onMove: (Move) -> Unit) {
    val st = LocalStrings.current
    val accent = LocalTableStyle.current.controlAccent
    var target by remember(la) { mutableIntStateOf(defaultTarget(la, bb)) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val p = pulse()
            Box(Modifier.size(7.dp).clip(CircleShape).background(accent.copy(alpha = 0.4f + 0.6f * p)))
            Spacer(Modifier.width(6.dp))
            Text(st.yourTurn, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (la.canRaise) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                for ((label, v) in presets(st, la, bb)) {
                    val sel = v == target
                    Box(
                        Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(7.dp))
                            .background(lerp(Palette.Bg, accent, if (sel) .14f else .035f))
                            .border(1.dp, accent.copy(alpha = if (sel) .7f else .18f), RoundedCornerShape(7.dp))
                            .selectable(sel, role = Role.RadioButton) { target = v },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (sel) accent else Palette.Text, fontSize = 11.sp, maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                }
            }
            if (la.maxRaiseTo > la.minRaiseTo) {
                val step = (bb / 2).coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton("−") { target = (target - step).coerceIn(la.minRaiseTo, la.maxRaiseTo) }
                    BetSlider(
                        fraction = (target - la.minRaiseTo).toFloat() / (la.maxRaiseTo - la.minRaiseTo),
                        onChange = { f ->
                            val raw = la.minRaiseTo + f * (la.maxRaiseTo - la.minRaiseTo)
                            val snapped = if (f >= 0.995f) la.maxRaiseTo else ((raw / step).roundToInt() * step)
                            target = snapped.coerceIn(la.minRaiseTo, la.maxRaiseTo)
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    StepButton("+") { target = (target + step).coerceIn(la.minRaiseTo, la.maxRaiseTo) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().height(46.dp)) {
            if (la.canCheck) {
                LiveActionButton(st.check, Palette.Green, Modifier.weight(1f).fillMaxHeight()) { onMove(Move.CHECK) }
            } else {
                LiveActionButton(st.fold, Palette.Red, Modifier.weight(1f).fillMaxHeight()) { onMove(Move.FOLD) }
                val callAllIn = la.toCall >= la.maxRaiseTo - la.myStreetBet
                LiveActionButton(
                    if (callAllIn) st.allIn else st.call, if (callAllIn) Palette.Red else Palette.Green,
                    sub = fmt(la.toCall), modifier = Modifier.weight(1f).fillMaxHeight(),
                ) { onMove(Move.CALL) }
            }
            if (la.canRaise) {
                val isAllIn = target >= la.maxRaiseTo
                LiveActionButton(
                    when {
                        isAllIn -> st.allIn
                        la.isOpeningBet -> st.bet
                        else -> st.raiseTo
                    },
                    if (isAllIn) Palette.Red else accent,
                    sub = fmt(target),
                    emphasized = !isAllIn,
                    modifier = Modifier.weight(if (la.canCheck) 1.4f else 1.2f).fillMaxHeight(),
                ) { onMove(Move.raiseTo(target)) }
            }
        }
    }
}

private fun defaultTarget(la: LegalActions, bb: Int): Int {
    val t = when {
        la.isOpeningBet -> (la.pot * 0.5).roundToInt()
        la.currentBet <= bb -> (bb * 2.5).roundToInt()
        else -> la.currentBet * 3
    }
    return t.coerceIn(la.minRaiseTo, la.maxRaiseTo)
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    val accent = LocalTableStyle.current.controlAccent
    Box(
        Modifier.size(34.dp).pressable(onClick = onClick).clip(RoundedCornerShape(7.dp)).background(Palette.Bg)
            .border(1.dp, accent.copy(alpha = .22f), RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BetSlider(fraction: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    val accent = LocalTableStyle.current.controlAccent
    Box(
        modifier.height(34.dp)
            .pointerInput(Unit) { detectTapGestures { cb((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> cb((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val f = fraction.coerceIn(0f, 1f)
            val r = 8.dp.toPx()
            val trackH = 4.dp.toPx()
            val y = size.height / 2
            val left = r
            val right = size.width - r
            drawRoundRect(Palette.PanelHi2, Offset(left, y - trackH / 2), Size(right - left, trackH), CornerRadius(trackH / 2))
            val x = left + (right - left) * f
            drawRoundRect(accent, Offset(left, y - trackH / 2), Size(x - left, trackH), CornerRadius(trackH / 2))
            drawCircle(accent.copy(alpha = .15f), r + 4.dp.toPx(), Offset(x, y))
            drawCircle(accent, r, Offset(x, y))
            drawCircle(lerp(accent, Color.White, .5f), r, Offset(x, y), style = Stroke(1.dp.toPx()))
        }
    }
}
