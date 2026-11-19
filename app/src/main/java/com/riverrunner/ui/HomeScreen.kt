package com.riverrunner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.data.Prefs
import com.riverrunner.engine.Cards
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.SessionConfig
import com.riverrunner.engine.TableType
import com.riverrunner.i18n.LocalStrings

private enum class HomeNavIcon { GUIDE, ACHIEVEMENTS, HISTORY, STATS, SAVES, SETTINGS }

@Composable
fun HomeScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    val p = vm.prefs
    val style = LocalTableStyle.current
    val accent = style.controlAccent
    Row(
        Modifier.fillMaxSize().background(Palette.Bg).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Left column: Brand header + frosted profile card + 2x3 minimalist icon grid
        Column(
            Modifier.width(276.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "RIVER RUNNER",
                    color = Palette.Text,
                    fontSize = 24.sp,
                    lineHeight = 26.sp,
                    fontFamily = Numeric,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Box(
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Palette.FeltCenter.copy(alpha = 0.35f))
                        .border(1.dp, Palette.Green.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "EE" + (vm.changelog.firstOrNull()?.version?.let { " $it" } ?: ""),
                        color = Palette.Green,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            PlayerCard(
                vm.profile, vm.profileList.size, vm.maxSlots,
                onSwitch = { vm.screen = Screen.Saves },
                modifier = Modifier.fillMaxWidth(),
            )
            val tiles = listOf(
                Triple(st.guide, HomeNavIcon.GUIDE) { vm.openGuide() },
                Triple(st.achievements, HomeNavIcon.ACHIEVEMENTS) { vm.screen = Screen.Achievements },
                Triple(st.history, HomeNavIcon.HISTORY) { vm.openHistory() },
                Triple(st.stats, HomeNavIcon.STATS) { vm.screen = Screen.Stats },
                Triple(st.saves, HomeNavIcon.SAVES) { vm.screen = Screen.Saves },
                Triple(st.settings, HomeNavIcon.SETTINGS) { vm.screen = Screen.Settings },
            )
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, icon, go) ->
                        NavItem(label, icon, accent, Modifier.weight(1f), onClick = go)
                    }
                }
            }
        }

        // Right column: Elevated emerald studio hero card + standalone Training Centre card
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val lobbyShape = RoundedCornerShape(18.dp)
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(lobbyShape)
                    .background(
                        Brush.radialGradient(
                            0.0f to Palette.FeltCenter.copy(alpha = 0.88f),
                            0.65f to Palette.FeltMid.copy(alpha = 0.78f),
                            1.0f to Palette.Panel,
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                style.art?.let {
                    Image(
                        painterResource(it), contentDescription = null, modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Box(
                    Modifier.matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Palette.Bg.copy(alpha = .76f),
                                    Palette.Bg.copy(alpha = .42f),
                                    Palette.Bg.copy(alpha = .22f),
                                )
                            )
                        )
                        .border(1.dp, accent.copy(alpha = .32f), lobbyShape),
                )
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Floating hero cards
                    Box(Modifier.height(92.dp).width(114.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.offset(x = (-20).dp, y = 2.dp).graphicsLayer { rotationZ = -10f }) {
                            PlayingCard(Cards.parse("As"), 54.dp)
                        }
                        Box(Modifier.offset(x = 20.dp, y = (-2).dp).graphicsLayer { rotationZ = 10f }) {
                            PlayingCard(Cards.parse("Kh"), 54.dp)
                        }
                    }
                    // Headline + Primary / Quick Start CTAs
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                if (st.isZh) "开一桌德州扑克" else "Master the Table",
                                color = Palette.Text,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (st.isZh) "自由配置筹码局、锦标赛、9 人桌与动态剥削对手"
                                else "Configure cash or tournament tables, 2–9 seats and adaptive opponents",
                                color = Palette.TextDim,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 2,
                            )
                        }
                        ActionButton(
                            st.startGame, accent, textColor = Palette.Ink,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) { vm.screen = Screen.Setup }
                        val summary = st.setupSummary(
                            p.lastMode == GameMode.TOURNAMENT, p.lastSize, p.lastStackBB,
                            p.lastTableType == TableType.MIXED, p.lastHandsPerLevel, p.lastAnte,
                        )
                        val quickShape = RoundedCornerShape(10.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(quickShape)
                                .background(Palette.Bg.copy(alpha = 0.62f))
                                .border(1.dp, Palette.Line.copy(alpha = 0.65f), quickShape)
                                .clickable(role = Role.Button) { vm.startGame(lastConfig(p)) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(st.quickStart, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                summary.take(2).joinToString(if (st.isZh) " · " else " · "),
                                color = Palette.TextDim,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("›", color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Dedicated modern Training Centre card
            Row(
                Modifier.fillMaxWidth()
                    .studioCard(accent = accent, radius = 14.dp, highlighted = true)
                    .clickable(role = Role.Button, onClick = vm::openTraining)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.width(4.dp).height(34.dp).clip(RoundedCornerShape(2.dp))
                        .background(Brush.verticalGradient(listOf(Palette.Green, accent)))
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (st.isZh) "专项训练" else "Training",
                        color = Palette.Text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (st.isZh) "用历史牌局练习翻牌 C-Bet、转牌与河牌决策，支持离线 Solver 批量桥接"
                        else "Practise flop C-bet, turn & river spots from your hands with offline Solver bridge",
                        color = Palette.TextDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text("›", color = accent, fontSize = 24.sp, lineHeight = 24.sp)
            }
        }
    }
}

internal fun lastConfig(p: Prefs) = SessionConfig(
    mode = p.lastMode, tableSize = p.lastSize, startingBB = p.lastStackBB, tableType = p.lastTableType,
    handsPerLevel = p.lastHandsPerLevel, cashAnteFraction = if (p.lastAnte) 0.125 else 0.0,
)

@Composable
private fun NavItem(text: String, icon: HomeNavIcon, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.height(62.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(Palette.PanelHi, accent, .04f), Palette.Panel)))
            .border(1.dp, Palette.Line.copy(alpha = 0.65f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        HomeIconGlyph(icon, Palette.TextDim)
        Text(text, color = Palette.Text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeIconGlyph(icon: HomeNavIcon, tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
        val w = size.width
        val h = size.height
        when (icon) {
            HomeNavIcon.GUIDE -> {
                drawRoundRect(tint, Offset(w * .16f, h * .14f), Size(w * .68f, h * .72f), CornerRadius(3f, 3f), style = stroke)
                drawLine(tint, Offset(w * .34f, h * .38f), Offset(w * .66f, h * .38f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .34f, h * .58f), Offset(w * .58f, h * .58f), stroke.width, StrokeCap.Round)
            }
            HomeNavIcon.ACHIEVEMENTS -> {
                val cup = Path().apply {
                    moveTo(w * .24f, h * .2f)
                    lineTo(w * .76f, h * .2f)
                    lineTo(w * .66f, h * .58f)
                    lineTo(w * .34f, h * .58f)
                    close()
                }
                drawPath(cup, tint, style = stroke)
                drawLine(tint, Offset(w * .5f, h * .58f), Offset(w * .5f, h * .8f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .32f, h * .82f), Offset(w * .68f, h * .82f), stroke.width, StrokeCap.Round)
            }
            HomeNavIcon.HISTORY -> {
                drawCircle(tint, w * .36f, Offset(w * .5f, h * .5f), style = stroke)
                drawLine(tint, Offset(w * .5f, h * .3f), Offset(w * .5f, h * .52f), stroke.width, StrokeCap.Round)
                drawLine(tint, Offset(w * .5f, h * .52f), Offset(w * .66f, h * .6f), stroke.width, StrokeCap.Round)
            }
            HomeNavIcon.STATS -> {
                drawLine(tint, Offset(w * .22f, h * .8f), Offset(w * .22f, h * .54f), stroke.width * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(w * .5f, h * .8f), Offset(w * .5f, h * .26f), stroke.width * 1.2f, StrokeCap.Round)
                drawLine(tint, Offset(w * .78f, h * .8f), Offset(w * .78f, h * .42f), stroke.width * 1.2f, StrokeCap.Round)
            }
            HomeNavIcon.SAVES -> {
                drawRoundRect(tint, Offset(w * .14f, h * .22f), Size(w * .72f, h * .58f), CornerRadius(3f, 3f), style = stroke)
                drawLine(tint, Offset(w * .14f, h * .42f), Offset(w * .86f, h * .42f), stroke.width)
            }
            HomeNavIcon.SETTINGS -> {
                drawCircle(tint, w * .34f, Offset(w * .5f, h * .5f), style = stroke)
                drawCircle(tint, w * .12f, Offset(w * .5f, h * .5f), style = stroke)
            }
        }
    }
}
