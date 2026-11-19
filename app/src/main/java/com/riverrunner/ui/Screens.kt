package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import com.riverrunner.engine.Cards
import com.riverrunner.data.Prefs
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.GameSession
import com.riverrunner.engine.SessionConfig
import com.riverrunner.engine.Progress
import com.riverrunner.engine.TableType
import com.riverrunner.i18n.LocalStrings
import java.text.DecimalFormat
import kotlinx.coroutines.launch

// ============================================================================ Home

@Composable
fun HomeScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    val p = vm.prefs
    Row(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(270.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("River Runner", color = Palette.Text, fontSize = 30.sp, lineHeight = 30.sp, fontFamily = Numeric, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Text("Exploit " + (vm.changelog.firstOrNull()?.version ?: "Edition"),
                    color = Palette.Gold, fontSize = 12.sp, maxLines = 1,
                    modifier = Modifier.padding(bottom = 3.dp))
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
                    row.forEach { (label, go) -> NavItem(label, Modifier.weight(1f), onClick = go) }
                }
            }
        }
        // the "table" side of the lobby: the one place with felt
        Box(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                .background(Brush.radialGradient(listOf(Palette.FeltCenter, Palette.FeltEdge), radius = 900f))
                .border(6.dp, Palette.Rail, RoundedCornerShape(20.dp))
                .padding(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.height(72.dp).width(110.dp)) {
                        Box(Modifier.align(Alignment.Center).offset(x = (-19).dp).graphicsLayer { rotationZ = -9f }) {
                            PlayingCard(Cards.parse("As"), 49.dp)
                        }
                        Box(Modifier.align(Alignment.Center).offset(x = 19.dp).graphicsLayer { rotationZ = 9f }) {
                            PlayingCard(Cards.parse("Kh"), 49.dp)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (st.isZh) "开一桌德州扑克" else "Play Texas Hold’em",
                            color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(if (st.isZh) "选择筹码局或锦标赛，设置人数与规则"
                            else "Choose cash or tournament, then set table size and rules",
                            color = Palette.TextDim, fontSize = 11.sp, maxLines = 2)
                    }
                }
                ActionButton(st.startGame, Palette.Gold, textColor = Palette.Ink,
                    modifier = Modifier.fillMaxWidth().height(48.dp)) { vm.screen = Screen.Setup }
                val summary = st.setupSummary(
                    p.lastMode == GameMode.TOURNAMENT, p.lastSize, p.lastStackBB,
                    p.lastTableType == TableType.MIXED, p.lastHandsPerLevel, p.lastAnte,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { vm.startGame(lastConfig(p)) }) {
                        Text(st.quickStart, color = Palette.Text, fontSize = 13.sp)
                    }
                    Text(summary.take(2).joinToString(if (st.isZh) "，" else ", "),
                        color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.weight(1f),
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x66000000))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = vm::openTraining)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (st.isZh) "专项训练" else "Training",
                            color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(if (st.isZh) "用自己的历史牌局练习河牌决策"
                            else "Practise river decisions from your own hands",
                            color = Palette.TextDim, fontSize = 11.sp)
                    }
                    Text("›", color = Palette.Gold, fontSize = 24.sp, lineHeight = 24.sp)
                }
            }
        }
    }
}

private fun lastConfig(p: Prefs) = SessionConfig(
    mode = p.lastMode, tableSize = p.lastSize, startingBB = p.lastStackBB, tableType = p.lastTableType,
    handsPerLevel = p.lastHandsPerLevel, cashAnteFraction = if (p.lastAnte) 0.125 else 0.0,
)

@Composable
private fun NavItem(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.pressable(onClick = onClick).height(48.dp).clip(RoundedCornerShape(10.dp))
            .background(Palette.Panel).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.Text, fontSize = 14.sp, maxLines = 1) }
}

// ============================================================================ New game setup

@Composable
fun SetupScreen(vm: AppViewModel, forTraining: Boolean = false) {
    val onBack = { vm.screen = if (forTraining) Screen.Training else Screen.Home }
    BackHandler(onBack = onBack)
    val p = vm.prefs
    val initial = if (forTraining) SessionConfig(GameMode.CASH, 2, 100, p.lastTableType) else lastConfig(p)
    SetupContent(
        initial = initial,
        onBack = onBack,
        onCustomize = vm::openEditionSettings,
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
    initial: SessionConfig, onBack: () -> Unit, onStart: (SessionConfig) -> Unit,
    onCustomize: (() -> Unit)? = null, selectedEdition: String? = null,
    liveStrategyEnabled: Boolean = false, onLiveStrategyChange: ((Boolean) -> Unit)? = null,
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
                        Text(if (st.isZh) "⚙ 自定义对手及规则" else "⚙ Customize opponents / rules",
                            color = Palette.Gold, fontSize = 12.sp)
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
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .background(Palette.PanelHi).padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(if (st.isZh) "本局显示实时策略参考" else "Show live strategy during this game",
                                color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(if (st.isZh) "只在你行动时显示；默认是启发式模型，非已验证 GTO"
                                else "Only on your turn; heuristic model, not verified GTO",
                                color = Palette.TextDim, fontSize = 11.sp)
                        }
                        Switch(checked = liveStrategyEnabled, onCheckedChange = onLiveStrategyChange,
                            modifier = Modifier.testTag("live-strategy-switch"))
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
                Text((if (st.isZh) "Exploit 配置：" else "Exploit config: ") + selectedEdition,
                    color = Palette.Gold, fontSize = 11.sp, maxLines = 3)
            }
            if (onLiveStrategyChange != null) {
                Text(if (liveStrategyEnabled) {
                    if (st.isZh) "实时参考：开启（非已验证 GTO）" else "Live reference: ON (not verified GTO)"
                } else {
                    if (st.isZh) "实时参考：关闭" else "Live reference: OFF"
                }, color = if (liveStrategyEnabled) Palette.Gold else Palette.TextDim, fontSize = 11.sp)
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
private fun OptionRow(label: String, content: @Composable () -> Unit) {
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

// ============================================================================ Result

@Composable
fun ResultScreen(vm: AppViewModel, summary: com.riverrunner.engine.SessionSummary) {
    val st = LocalStrings.current
    val cash = summary.mode == GameMode.CASH
    val earlyTournament = !cash && summary.endedEarly
    val settlementAmount = remember { DecimalFormat("#,##0.0#") }
    val headline = when {
        cash -> if (st.isZh) "筹码局结算" else "Cash game results"
        earlyTournament -> if (st.isZh) "锦标赛提前结束" else "Tournament ended early"
        summary.heroPlace == 1 -> st.champion
        else -> st.placeN(summary.heroPlace)
    }
    BackHandler { vm.screen = Screen.Home }
    Row(Modifier.fillMaxSize().background(Palette.Bg).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(260.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!cash) Text(st.result, color = Palette.TextDim, fontSize = 14.sp)
            Text(headline, color = Palette.Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (earlyTournament) {
                Text(
                    if (st.isZh) "离桌时按筹码暂列 ${st.placeN(summary.heroPlace)}"
                    else "Chip standings at exit · ${st.placeN(summary.heroPlace)}",
                    color = Palette.Text, fontSize = 15.sp,
                )
            } else {
                if (summary.endedEarly) Text(st.endedEarly, color = Palette.Text, fontSize = 15.sp)
                val profit = summary.heroProfit
                val unit = if (cash) " BB" else if (st.isZh) " 积分" else " pts"
                val profitColor = when {
                    profit > 0 -> Palette.Green
                    profit < 0 -> Palette.Red
                    else -> Palette.Text
                }
                Text(
                    "${st.profit}: " + (if (profit > 0) "+" else "") + settlementAmount.format(profit) + unit,
                    color = profitColor, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
            }
            Text("${st.handsPlayed}: ${summary.handsPlayed}", color = Palette.Text, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            ActionButton(st.playAgain, Palette.RaiseBtn, modifier = Modifier.fillMaxWidth()) { vm.playAgain() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(st.history, Palette.PanelHi, modifier = Modifier.weight(1f)) { vm.openHistory() }
                ActionButton(st.home, Palette.PanelHi, modifier = Modifier.weight(1f)) { vm.screen = Screen.Home }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Palette.Panel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (cash) item {
                SectionTitle(if (st.isZh) "离桌筹码" else "Closing stacks")
            }
            items(if (cash) summary.standings.sortedBy { it.seat } else summary.standings) { s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (s.isHuman) Palette.AccentDim else Palette.PanelHi)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!cash) Text("#${s.place}", color = Palette.Text, fontSize = 20.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
                    Text(if (s.isHuman) st.you else s.name, color = Palette.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    val stackText = when {
                        cash && vm.showInBB -> settlementAmount.format(s.stack.toDouble() / GameSession.BASE_BB) + " BB"
                        cash || s.stack > 0 -> Format.amount(s.stack, GameSession.BASE_BB, false)
                        else -> st.busted
                    }
                    Text(stackText, color = Palette.TextDim, fontSize = 14.sp)
                }
            }
        }
    }
}

// ============================================================================ Settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    var practice by remember { mutableStateOf(vm.prefs.practiceDefault) }
    var speed by remember { mutableIntStateOf(vm.prefs.speed) }
    var haptics by remember { mutableStateOf(vm.prefs.actionHaptics) }
    var showAbout by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var resetStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("← " + st.back, { vm.screen = Screen.Home })
            Text(st.settings, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OptionRow(st.language) {
                    Pill("中文", st.isZh, { vm.setChinese(true) })
                    Pill("English", !st.isZh, { vm.setChinese(false) })
                }
                OptionRow(st.deckStyle) {
                    Pill(st.deckFour, vm.deckStyle == DeckStyle.FOUR_COLOR, { vm.setDeck(DeckStyle.FOUR_COLOR) })
                    Pill(st.deckFilled, vm.deckStyle == DeckStyle.FOUR_COLOR_FILLED, { vm.setDeck(DeckStyle.FOUR_COLOR_FILLED) })
                    Pill(st.deckClassic, vm.deckStyle == DeckStyle.CLASSIC, { vm.setDeck(DeckStyle.CLASSIC) })
                }
                Row(Modifier.padding(start = 92.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in listOf("As", "Kh", "Qd", "Jc")) PlayingCard(Cards.parse(c), 40.dp)
                }
                OptionRow(st.speed) {
                    Pill(st.slow, speed == 0, { speed = 0; vm.prefs.speed = 0 })
                    Pill(st.normal, speed == 1, { speed = 1; vm.prefs.speed = 1 })
                    Pill(st.fast, speed == 2, { speed = 2; vm.prefs.speed = 2 })
                }
                ToggleRow(st.practiceDefault, practice) { practice = it; vm.prefs.practiceDefault = it }
                ToggleRow(st.showInBB, vm.showInBB) { vm.setShowBB(it) }
                ToggleRow(st.sound, vm.soundOn) { vm.setSound(it) }
                ToggleRow(if (st.isZh) "行动时震动反馈" else "Haptic feedback on actions", haptics) {
                    haptics = it
                    vm.prefs.actionHaptics = it
                }
                ActionButton(if (st.isZh) "管理牌谱与导出" else "Manage and export hands", Palette.PanelHi,
                    modifier = Modifier.fillMaxWidth()) { vm.openHistory() }
                TextButton(enabled = !resetting, onClick = { confirmReset = true }) {
                    Text(if (st.isZh) "重置练习进度" else "Reset practice progress")
                }
                resetStatus?.let { Text(it, color = Palette.TextDim, fontSize = 12.sp) }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(Palette.Panel).padding(14.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (st.isZh) "扑克术语表" else "Poker glossary", { vm.openGuide(GuideTab.GLOSSARY) })
                    SmallButton(if (st.isZh) "关于与隐私" else "About & privacy", { showAbout = true })
                }
                SectionTitle("Exploit Edition")
                Text(if (st.isZh) "AI、对手、抽水与筹码设置已集成；保存后从新牌桌开始生效。" else
                    "AI, opponents, rake and stacks are configurable. Saved settings apply to the next table.",
                    color = Palette.TextDim, fontSize = 12.sp)
                Text(editionSummary(vm.editionConfig, st.isZh), color = Palette.Gold, fontSize = 13.sp)
                ActionButton(if (st.isZh) "⚙ 自定义 AI、对手与私人局" else "⚙ Customize AI, opponents & cash",
                    Palette.Gold, textColor = Palette.Ink, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    vm.openEditionSettings()
                }
                ActionButton(if (st.isZh) "专项训练中心" else "Training centre",
                    Palette.PanelHi, modifier = Modifier.fillMaxWidth().height(42.dp)) { vm.openTraining() }
                Text(if (st.isZh) "说明：默认是原版 AI；动态剥削需主动开启，且证据不足时会保留基准策略。" else
                    "Default AI is Legacy. Enable Adaptive explicitly; sparse evidence retains the baseline.",
                    color = Palette.TextDim, fontSize = 11.sp)
                SectionTitle(if (st.isZh) "复盘训练功能" else "Replay coach settings")
                Text(if (st.isZh) "全部设置独立保存；只分析当时可见的信息。"
                    else "Settings persist independently. No future cards or hidden opponent hands are used.",
                    color = Palette.TextDim, fontSize = 11.sp)
                ToggleRow(if (st.isZh) "1. 盲测复盘" else "1. Blind replay", vm.blindReviewEnabled) {
                    vm.setStudyToggle("blind", it)
                }
                ToggleRow(if (st.isZh) "2. 弱点分类报告" else "2. Weakness report", vm.weaknessReportEnabled) {
                    vm.setStudyToggle("weak", it)
                }
                ToggleRow(if (st.isZh) "3. 错题与间隔复习" else "3. Spaced error drills", vm.spacedDrillsEnabled) {
                    vm.setStudyToggle("spaced", it)
                }
                ToggleRow(if (st.isZh) "4. 翻前范围矩阵教练" else "4. Preflop range coach", vm.preflopCoachEnabled) {
                    vm.setStudyToggle("preflop", it)
                }
                ToggleRow(if (st.isZh) "5. 河牌 What-if 情景比较" else "5. River what-if scenarios", vm.riverWhatIfEnabled) {
                    vm.setStudyToggle("whatif", it)
                }
                ToggleRow(if (st.isZh) "新牌局默认显示策略参考" else "Default live strategy reference in new games", vm.realtimeGtoEnabled) {
                    vm.setStudyToggle("live", it)
                }
                Text(if (st.isZh) "也可在「开始游戏」的牌局配置中选择开启或关闭。未匹配已验证的求解器数据时，只显示「启发式基准」；仅你的回合出现。"
                    else "Choose ON/OFF directly in New Game as well. Without verified solver data, advice is heuristic only, shown on your turn.",
                    color = Palette.TextDim, fontSize = 10.sp)
                ActionButton(if (st.isZh) "打开错题间隔训练" else "Open spaced training",
                    Palette.PanelHi, modifier = Modifier.fillMaxWidth().height(38.dp)) { vm.openTraining() }
                SmallButton(st.whatsNew, { vm.showChangelog() })
            }
        }
    }
    if (showAbout) {
        AboutDialog { showAbout = false }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(if (st.isZh) "重置练习进度？" else "Reset practice progress?") },
            text = { Text(if (st.isZh) "将清除当前存档的答题统计和错题复习计划。"
                else "This clears quiz statistics and the spaced-review schedule for the active save.") },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(st.keep) } },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    resetting = true
                    scope.launch {
                        resetStatus = try {
                            vm.resetPracticeProgress()
                            if (st.isZh) "练习进度已重置" else "Practice progress reset"
                        } catch (_: java.io.IOException) {
                            if (st.isZh) "重置未完成" else "Could not finish resetting progress"
                        } catch (_: IllegalStateException) {
                            if (st.isZh) "重置未完成" else "Could not finish resetting progress"
                        } finally {
                            resetting = false
                        }
                    }
                }) { Text(if (st.isZh) "重置" else "Reset", color = Palette.Red) }
            },
        )
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    val st = LocalStrings.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val repository = "https://github.com/Richardc7/river-runner"
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var status by remember { mutableStateOf<String?>(null) }
    fun open(link: String) {
        status = runCatching { uriHandler.openUri(link) }.exceptionOrNull()?.let {
            if (st.isZh) "设备无法打开链接" else "This device could not open the link"
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (st.isZh) "关于与隐私" else "About & privacy") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("River Runner · " + st.currentVersion(version))
                Text(if (st.isZh) "牌局、练习进度和导入的策略保存在设备上。应用本身离线运行。Android 可能按照设备备份设置备份应用数据。导出时，由你在系统文件选择器中指定保存位置。"
                    else "Hands, practice progress, and imported strategies are stored on the device. The app runs offline. Android may back up app data according to your device settings. Exports go to the location you select in the system file picker.")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextButton(onClick = { open(repository) }) { Text("GitHub") }
                    TextButton(onClick = { open("$repository/blob/exploit-edition/LICENSE") }) {
                        Text(if (st.isZh) "MIT 协议" else "MIT license")
                    }
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(repository))
                        status = if (st.isZh) "已复制 GitHub 链接" else "GitHub link copied"
                    }) { Text(if (st.isZh) "复制地址" else "Copy URL") }
                }
                status?.let { Text(it, color = Palette.TextDim, fontSize = 11.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(st.back) } },
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.AccentDim, checkedThumbColor = Palette.Accent, uncheckedTrackColor = Palette.PanelHi2),
        )
    }
}

@Suppress("unused")
private val unusedColor = Color.Unspecified
