package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.SessionConfig
import com.riverrunner.engine.TableType
import com.riverrunner.i18n.LocalStrings

@Composable
fun SetupScreen(vm: AppViewModel, forTraining: Boolean = false) {
    val onBack = { vm.screen = if (forTraining) Screen.Training else Screen.Home }
    BackHandler(onBack = onBack)
    val p = vm.prefs
    val initial = if (forTraining) SessionConfig(GameMode.CASH, 2, 100, p.lastTableType) else lastConfig(p)
    SetupContent(
        initial = initial,
        onBack = onBack,
        onCustomize = { vm.openEditionSettings() },
        selectedEdition = editionSummary(vm.editionConfig, vm.prefs.chinese),
        liveStrategyEnabled = vm.realtimeGtoEnabled,
        onLiveStrategyChange = { vm.setStudyToggle("live", it) },
        onStart = { c ->
            p.lastMode = c.mode; p.lastSize = c.tableSize; p.lastStackBB = c.startingBB
            p.lastTableType = c.tableType; p.lastHandsPerLevel = c.handsPerLevel; p.lastAnte = c.cashAnteFraction > 0
            vm.startGame(c)
        },
    )
}

@Composable
fun SetupContent(
    initial: SessionConfig,
    onBack: () -> Unit,
    onStart: (SessionConfig) -> Unit,
    onCustomize: (() -> Unit)? = null,
    selectedEdition: String? = null,
    liveStrategyEnabled: Boolean = false,
    onLiveStrategyChange: ((Boolean) -> Unit)? = null,
) {
    val st = LocalStrings.current
    var mode by remember { mutableStateOf(initial.mode) }
    var size by remember { mutableIntStateOf(initial.tableSize) }
    var stackBB by remember { mutableIntStateOf(initial.startingBB) }
    var table by remember { mutableStateOf(initial.tableType) }
    var hpl by remember { mutableIntStateOf(initial.handsPerLevel) }
    var ante by remember { mutableStateOf(initial.cashAnteFraction > 0) }
    val cashStacks = listOf(50, 100, 150, 200)
    val tourneyStacks = listOf(20, 30, 50, 75, 100)
    val stackOptions = if (mode == GameMode.CASH) cashStacks else tourneyStacks
    Row(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton("←", onBack)
                Text(st.newGame, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (onCustomize != null) {
                    TextButton(onClick = onCustomize) {
                        Text(
                            if (st.isZh) "⚙ 自定义对手及规则" else "⚙ Customize opponents / rules",
                            color = Palette.Gold, fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Panel)
                    .padding(horizontal = 14.dp, vertical = 10.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OptionRow(st.mode) {
                    Pill(st.cash, mode == GameMode.CASH, { mode = GameMode.CASH; if (stackBB !in cashStacks) stackBB = 100 })
                    Pill(st.tournament, mode == GameMode.TOURNAMENT, { mode = GameMode.TOURNAMENT; if (stackBB !in tourneyStacks) stackBB = 50 })
                }
                OptionRow(st.players) {
                    for (n in listOf(2, 5, 6, 7, 8, 9)) Pill("$n", size == n, { size = n }, Modifier.width(52.dp))
                }
                OptionRow(st.stackLabelBB) {
                    for (b in stackOptions) Pill("$b", stackBB == b, { stackBB = b }, Modifier.width(60.dp))
                }
                OptionRow(st.tableType) {
                    Pill(st.gtoTable, table == TableType.GTO, { table = TableType.GTO })
                    Pill(st.mixedTable, table == TableType.MIXED, { table = TableType.MIXED })
                }
                if (onLiveStrategyChange != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Palette.PanelHi).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                if (st.isZh) "本局显示实时策略参考" else "Show live strategy during this game",
                                color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (st.isZh) "在你行动时可查看" else "Available on your turn",
                                color = Palette.TextDim, fontSize = 11.sp,
                            )
                        }
                        Switch(
                            checked = liveStrategyEnabled, onCheckedChange = onLiveStrategyChange,
                            modifier = Modifier.testTag("live-strategy-switch"),
                        )
                    }
                }
                if (mode == GameMode.TOURNAMENT) {
                    OptionRow(st.handsPerLevel) {
                        for (h in listOf(5, 8, 10, 15, 20)) Pill("$h", hpl == h, { hpl = h }, Modifier.width(52.dp))
                    }
                } else {
                    OptionRow(st.ante) {
                        Pill(st.none, !ante, { ante = false })
                        Pill("12.5%", ante, { ante = true })
                    }
                }
            }
        }
        // what you are about to play, in words, next to the start button
        Column(
            Modifier.width(230.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                .background(Brush.radialGradient(listOf(Palette.FeltCenter, Palette.FeltEdge), radius = 600f))
                .border(5.dp, Palette.Rail, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val lines = st.setupSummary(mode == GameMode.TOURNAMENT, size, stackBB, table == TableType.MIXED, hpl, ante)
            Text(lines[0], color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            for (l in lines.drop(1)) Text(l, color = Palette.Text.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp)
            if (selectedEdition != null) {
                Text(
                    (if (st.isZh) "EE 配置：" else "EE settings: ") + selectedEdition,
                    color = Palette.Gold, fontSize = 11.sp, maxLines = 3,
                )
            }
            if (onLiveStrategyChange != null) {
                Text(
                    if (liveStrategyEnabled) {
                        if (st.isZh) "实时参考：开启" else "Live reference: ON"
                    } else {
                        if (st.isZh) "实时参考：关闭" else "Live reference: OFF"
                    },
                    color = if (liveStrategyEnabled) Palette.Gold else Palette.TextDim, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            ActionButton(st.start, Palette.Gold, textColor = Palette.Ink, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                onStart(
                    SessionConfig(
                        mode = mode, tableSize = size, startingBB = stackBB, tableType = table,
                        handsPerLevel = hpl, cashAnteFraction = if (ante) 0.125 else 0.0,
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OptionRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label, color = Palette.TextDim, fontSize = 13.sp, maxLines = 2,
            modifier = Modifier.width(92.dp).padding(top = 10.dp),
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}
