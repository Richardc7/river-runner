package com.riverrunner.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.LegalActions
import com.riverrunner.engine.Move
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.i18n.Strings
import kotlin.math.roundToInt

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
internal fun HeroControls(la: LegalActions, fmt: (Int) -> String, bb: Int, onMove: (Move) -> Unit) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                for ((label, v) in presets(st, la, bb)) {
                    val sel = v == target
                    Box(
                        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp))
                            .background(lerp(Palette.Bg, accent, if (sel) .16f else .04f))
                            .border(1.dp, accent.copy(alpha = if (sel) .75f else .22f), RoundedCornerShape(8.dp))
                            .selectable(sel, role = Role.RadioButton) { target = v },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, color = if (sel) accent else Palette.Text, fontSize = 11.sp, maxLines = 1,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
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
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
internal fun pulse(): Float {
    val tr = rememberInfiniteTransition(label = "panelPulse")
    val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    return v
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    val accent = LocalTableStyle.current.controlAccent
    Box(
        Modifier.size(38.dp).pressable(onClick = onClick).clip(RoundedCornerShape(8.dp)).background(Palette.Bg)
            .border(1.dp, accent.copy(alpha = .26f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun BetSlider(fraction: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val cb by rememberUpdatedState(onChange)
    val accent = LocalTableStyle.current.controlAccent
    Box(
        modifier.height(38.dp)
            .pointerInput(Unit) { detectTapGestures { cb((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> cb((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val f = fraction.coerceIn(0f, 1f)
            val r = 9.dp.toPx()
            val trackH = 5.dp.toPx()
            val y = size.height / 2
            val left = r
            val right = size.width - r
            drawRoundRect(Palette.PanelHi2, Offset(left, y - trackH / 2), Size(right - left, trackH), CornerRadius(trackH / 2))
            val x = left + (right - left) * f
            drawRoundRect(accent, Offset(left, y - trackH / 2), Size(x - left, trackH), CornerRadius(trackH / 2))
            drawCircle(accent.copy(alpha = .16f), r + 4.dp.toPx(), Offset(x, y))
            drawCircle(accent, r, Offset(x, y))
            drawCircle(lerp(accent, Color.White, .5f), r, Offset(x, y), style = Stroke(1.dp.toPx()))
        }
    }
}
