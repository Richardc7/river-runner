package com.riverrunner.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@Composable
fun HomeScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    val p = vm.prefs
    val style = LocalTableStyle.current
    val accent = style.controlAccent
    Row(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(270.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("River Runner", color = Palette.Text, fontSize = 30.sp, lineHeight = 30.sp, fontFamily = Numeric, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text(
                    "EE" + (vm.changelog.firstOrNull()?.version?.let { " $it" } ?: ""),
                    color = accent, fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            PlayerCard(vm.profile, vm.profileList.size, vm.maxSlots, onSwitch = { vm.screen = Screen.Saves }, modifier = Modifier.fillMaxWidth())
            val tiles = listOf(
                st.guide to { vm.openGuide() },
                st.achievements to { vm.screen = Screen.Achievements },
                st.history to { vm.openHistory() },
                st.stats to { vm.screen = Screen.Stats },
                st.saves to { vm.screen = Screen.Saves },
                st.settings to { vm.screen = Screen.Settings },
            )
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, go) -> NavItem(label, accent, Modifier.weight(1f), onClick = go) }
                }
            }
        }
        val lobbyShape = RoundedCornerShape(20.dp)
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(lobbyShape)
                .background(Brush.horizontalGradient(listOf(Palette.Bg, Palette.FeltEdge))),
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
                                Palette.Bg.copy(alpha = .95f),
                                Palette.Bg.copy(alpha = .78f),
                                Palette.Bg.copy(alpha = .25f),
                            )
                        )
                    )
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Palette.Bg.copy(alpha = .72f))))
                    .border(1.dp, accent.copy(alpha = .38f), lobbyShape)
            )
            Box(
                Modifier.matchParentSize().padding(5.dp)
                    .border(1.dp, accent.copy(alpha = .16f), RoundedCornerShape(15.dp))
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.height(72.dp).width(110.dp)) {
                        Box(Modifier.align(Alignment.Center).offset(x = (-19).dp).graphicsLayer { rotationZ = -9f }) {
                            PlayingCard(Cards.parse("As"), 49.dp)
                        }
                        Box(Modifier.align(Alignment.Center).offset(x = 19.dp).graphicsLayer { rotationZ = 9f }) {
                            PlayingCard(Cards.parse("Kh"), 49.dp)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (st.isZh) "开一桌德州扑克" else "Play Texas Hold’em",
                            color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (st.isZh) "选择筹码局或锦标赛，设置人数与规则"
                            else "Choose cash or tournament, then set table size and rules",
                            color = Palette.TextDim, fontSize = 11.sp, maxLines = 2,
                        )
                    }
                }
                ActionButton(
                    st.startGame, accent, textColor = Palette.Ink,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { vm.screen = Screen.Setup }
                val summary = st.setupSummary(
                    p.lastMode == GameMode.TOURNAMENT, p.lastSize, p.lastStackBB,
                    p.lastTableType == TableType.MIXED, p.lastHandsPerLevel, p.lastAnte,
                )
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = { vm.startGame(lastConfig(p)) }) {
                        Text(st.quickStart, color = Palette.Text, fontSize = 13.sp)
                    }
                    Text(
                        summary.take(2).joinToString(if (st.isZh) "，" else ", "),
                        color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                val trainingShape = RoundedCornerShape(12.dp)
                Row(
                    Modifier.fillMaxWidth().clip(trainingShape)
                        .background(Brush.horizontalGradient(listOf(lerp(Palette.Panel, accent, .1f), Palette.Bg)))
                        .border(1.dp, accent.copy(alpha = .34f), trainingShape)
                        .clickable(role = Role.Button, onClick = vm::openTraining)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (st.isZh) "专项训练" else "Training",
                            color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (st.isZh) "用自己的历史牌局练习河牌决策"
                            else "Practise river decisions from your own hands",
                            color = Palette.TextDim, fontSize = 11.sp,
                        )
                    }
                    Text("›", color = accent, fontSize = 24.sp, lineHeight = 24.sp)
                }
            }
        }
    }
}

internal fun lastConfig(p: Prefs) = SessionConfig(
    mode = p.lastMode, tableSize = p.lastSize, startingBB = p.lastStackBB, tableType = p.lastTableType,
    handsPerLevel = p.lastHandsPerLevel, cashAnteFraction = if (p.lastAnte) 0.125 else 0.0,
)

@Composable
private fun NavItem(text: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.height(48.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(Palette.Panel, accent, .06f), Palette.Bg)))
            .border(1.dp, accent.copy(alpha = .2f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.Text, fontSize = 14.sp, maxLines = 1) }
}
