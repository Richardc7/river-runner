package com.riverrunner.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards
import com.riverrunner.engine.Move
import com.riverrunner.i18n.LocalStrings

@Composable
internal fun SidePanel(
    ui: TableScreenUi,
    fmt: (Int) -> String,
    width: Dp,
    onMove: (Move) -> Unit,
    onNext: () -> Unit,
    onFastForward: () -> Unit,
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
            else ActionLog(
                ui.log, Modifier.fillMaxSize(),
                highlightIndex = ui.log.indexOfLast { it.kind == LogKind.ACTION },
            )
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

internal fun Modifier.liveSurface(accent: Color, shape: RoundedCornerShape, emphasized: Boolean = false): Modifier =
    clip(shape)
        .background(Brush.verticalGradient(listOf(lerp(Palette.Panel, accent, .075f), Palette.Bg)))
        .border(1.dp, accent.copy(alpha = if (emphasized) .57f else .24f), shape)

@Composable
internal fun LiveActionButton(
    label: String,
    accent: Color,
    modifier: Modifier,
    sub: String? = null,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    val colors = if (emphasized) listOf(lerp(accent, Color.White, .18f), lerp(accent, Palette.Ink, .17f))
    else listOf(lerp(Palette.PanelHi, accent, .12f), Palette.Bg)
    val ink = if (emphasized) Palette.Ink else accent
    Column(
        modifier.clip(shape).background(Brush.verticalGradient(colors))
            .border(1.dp, accent.copy(alpha = if (emphasized) .9f else .45f), shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = ink, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        if (sub != null) {
            Text(
                sub, color = if (emphasized) ink else Palette.Text,
                fontSize = 14.sp, lineHeight = 16.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1,
            )
        }
    }
}

@Composable
fun ActionLog(
    lines: List<LogLine>,
    modifier: Modifier = Modifier,
    highlightIndex: Int = -1,
    onClick: ((Int) -> Unit)? = null,
) {
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
                .then(
                    if (hl) Modifier.clip(RoundedCornerShape(5.dp))
                        .background(lerp(Palette.Bg, accent, .14f)).drawBehind {
                            drawLine(accent, Offset(0f, 3.dp.toPx()), Offset(0f, size.height - 3.dp.toPx()), 2.dp.toPx())
                        } else Modifier
                )
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
