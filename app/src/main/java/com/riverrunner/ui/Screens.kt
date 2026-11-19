package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import com.riverrunner.engine.Cards
import com.riverrunner.data.Prefs
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.HeroStats
import com.riverrunner.engine.SessionConfig
import com.riverrunner.engine.Stats
import com.riverrunner.engine.Progress
import com.riverrunner.engine.Achievements
import com.riverrunner.engine.TableType
import com.riverrunner.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                st.history to { vm.screen = Screen.History },
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
                        Text(if (st.isZh) "牌桌 · 训练中心" else "Play · Training",
                            color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(if (st.isZh) "开始牌局，或针对弱项专项练习"
                            else "Play a table or practise your weakest spots",
                            color = Palette.TextDim, fontSize = 11.sp, maxLines = 2)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(st.startGame, Palette.Gold, textColor = Palette.Ink,
                        modifier = Modifier.weight(1f).height(48.dp)) { vm.screen = Screen.Setup }
                    ActionButton(if (st.isZh) "专项训练" else "Training", Palette.CallBtn,
                        modifier = Modifier.weight(1f).height(48.dp)) { vm.openTraining() }
                }
                val summary = st.setupSummary(
                    p.lastMode == GameMode.TOURNAMENT, p.lastSize, p.lastStackBB,
                    p.lastTableType == TableType.MIXED, p.lastHandsPerLevel, p.lastAnte,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        Modifier.weight(1f).pressable { vm.startGame(lastConfig(p)) }
                            .clip(RoundedCornerShape(12.dp)).background(Color(0x66000000))
                            .border(1.dp, Palette.Gold.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(st.quickStart + "  ▶", color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(summary.take(2).joinToString(if (st.isZh) "，" else ", "),
                            color = Palette.Text.copy(alpha = 0.85f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = vm::openEditionSettings) {
                        Text(if (st.isZh) "自定义" else "Customize",
                            color = Palette.Gold, fontSize = 12.sp)
                    }
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
                ActionButton(st.history, Palette.PanelHi, modifier = Modifier.weight(1f)) { vm.screen = Screen.History }
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

// ============================================================================ History

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    var favOnly by remember { mutableStateOf(false) }
    var showResults by remember(vm.blindReviewEnabled) { mutableStateOf(!vm.blindReviewEnabled) }
    val records = remember { mutableStateListOf<HandRecord>() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val all = withContext(Dispatchers.IO) { vm.history.all() }
        records.clear(); records.addAll(all.asReversed())
        loaded = true
    }
    val shown = if (favOnly) records.filter { it.favorite } else records
    val df = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("← " + st.back, { vm.screen = Screen.Home })
            Text(st.history, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (vm.blindReviewEnabled) {
                Pill(
                    if (showResults) {
                        if (st.isZh) "隐藏结果" else "Hide results"
                    } else if (st.isZh) "显示结果" else "Show results",
                    showResults, { showResults = !showResults },
                )
            }
            Pill(st.all, !favOnly, { favOnly = false })
            Pill("★ " + st.favorites, favOnly, { favOnly = true })
        }
        Text(st.keepHandsNote, color = Palette.TextDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 6.dp))
        if (loaded && shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(st.noHandsHint, color = Palette.TextDim, fontSize = 14.sp, modifier = Modifier.width(360.dp))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.id }) { r ->
                val hero = r.hero
                val net = r.heroNet.toDouble() / r.bigBlind
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Palette.Panel)
                        .clickable { vm.openReplay(r) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.width(92.dp)) {
                        Text(df.format(Date(r.time)), color = Palette.TextDim, fontSize = 11.sp)
                        Text(
                            (if (r.mode == GameMode.CASH) st.cash else st.tournament) + " #${r.handNumber}",
                            color = Palette.Text, fontSize = 12.sp,
                        )
                        Text(Stats.positionOf(r), color = Palette.TextDim, fontSize = 11.sp)
                    }
                    if (hero != null) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        PlayingCard(hero.hole[0], 26.dp); PlayingCard(hero.hole[1], 26.dp)
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (showResults) for (c in r.board) PlayingCard(c, 22.dp)
                    }
                    if (showResults) {
                        Text(
                            (if (net > 0) "+" else "") + "%.1f BB".format(net),
                            color = if (net > 0) Palette.Green else if (net < 0) Palette.Red else Palette.TextDim,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp),
                        )
                    }
                    if (vm.blindReviewEnabled) {
                        SmallButton(if (st.isZh) "盲测" else "Blind quiz", { vm.openReplay(r, blind = true) })
                        SmallButton(if (st.isZh) "回放" else "Replay", { vm.openReplay(r) })
                    }
                    Text(
                        if (r.favorite) "★" else "☆", color = Palette.Gold, fontSize = 22.sp,
                        modifier = Modifier.clickable {
                            val upd = vm.toggleFavorite(r.id)
                            if (upd != null) {
                                val i = records.indexOfFirst { it.id == r.id }
                                if (i >= 0) records[i] = upd
                            }
                        }.padding(6.dp),
                    )
                }
            }
        }
    }
}

// ============================================================================ Stats

@Composable
fun StatsScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    var stats by remember { mutableStateOf<HeroStats?>(null) }
    LaunchedEffect(Unit) {
        stats = withContext(Dispatchers.Default) { Stats.compute(vm.history.all()) }
    }
    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("← " + st.back, { vm.screen = Screen.Home })
            Text(st.stats, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val s = stats
                SectionTitle(st.heroStatsTitle + (if (s != null) " (${s.hands})" else ""))
                if (s == null) Text(st.computing, color = Palette.TextDim)
                else {
                    StatLine(st.vpip, Format.pct(s.vpip))
                    StatLine(st.pfr, Format.pct(s.pfr))
                    StatLine(st.threeBet, Format.pct(s.threeBet))
                    StatLine(st.wtsd, Format.pct(s.wtsd))
                    StatLine(st.wsd, Format.pct(s.wsd))
                    StatLine(st.bbPer100, "%.1f".format(s.bbPer100), s.bbPer100)
                    StatLine(st.netBB, "%.1f".format(s.netBB), s.netBB)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionTitle(st.byPosition)
                val s = stats
                if (s != null) {
                    val order = listOf("UTG", "UTG+1", "UTG+2", "HJ", "CO", "BTN", "SB", "BB")
                    for (pos in order) s.byPosition[pos]?.let { StatLine(pos, "%.1f".format(it), it) }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel).padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                val pr = vm.profile
                fun c(k: String) = pr.counter(k)
                val cashBB = c(Progress.CASH_PROFIT_CENTI_BB) / 100.0
                SectionTitle(st.careerTitleFor(pr.name))
                StatLine(st.handsLabel, "%,d".format(c(Progress.HANDS)))
                StatLine(st.sessions, "${c(Progress.CASH_SESSIONS)}")
                StatLine(st.cashProfitBB, "%.1f".format(cashBB), cashBB)
                StatLine(st.tournamentsPlayed, "${c(Progress.TOURNEYS)}")
                StatLine(st.tournamentWins, "${c(Progress.TOURNEY_WINS)}")
                StatLine(st.itm, "${c(Progress.TOURNEY_ITM)}")
                StatLine(st.tourneyPoints, "${c(Progress.TOURNEY_POINTS)}", c(Progress.TOURNEY_POINTS).toDouble())
                StatLine(st.achievements, "${pr.unlocked.size} / ${Achievements.all.size}")
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, signed: Double? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when { signed == null -> Palette.Text; signed > 0 -> Palette.Green; signed < 0 -> Palette.Red; else -> Palette.Text },
        )
    }
}

// ============================================================================ Settings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    var practice by remember { mutableStateOf(vm.prefs.practiceDefault) }
    var speed by remember { mutableIntStateOf(vm.prefs.speed) }
    var confirmClear by remember { mutableStateOf(false) }
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
                ActionButton(st.clearHistory, Palette.FoldBtn, modifier = Modifier.fillMaxWidth()) { confirmClear = true }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .background(Palette.Panel).padding(14.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle("Exploit Edition V0.5.1")
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
                ActionButton(if (st.isZh) "打开历史复盘与盲测" else "Open history & blind replay",
                    Palette.PanelHi, modifier = Modifier.fillMaxWidth().height(38.dp)) { vm.screen = Screen.History }
                ActionButton(if (st.isZh) "打开错题间隔训练" else "Open spaced training",
                    Palette.PanelHi, modifier = Modifier.fillMaxWidth().height(38.dp)) { vm.openTraining() }
                Spacer(Modifier.height(6.dp))
                SectionTitle(st.about)
                Text(st.aboutText, color = Palette.Text, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(8.dp))
                Text("github.com/Richardc7/river-runner (Fork of shunzhe1121/river-runner)",
                    color = Palette.TextDim, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val version = remember {
                    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
                }
                Text(st.currentVersion(version), color = Palette.TextDim, fontSize = 12.sp, fontFamily = Numeric)
                Spacer(Modifier.height(6.dp))
                SmallButton(st.whatsNew, { vm.showChangelog() })
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            confirmButton = { TextButton({ confirmClear = false; vm.history.clearNonFavorites() }) { Text(st.clearAction, color = Palette.Red) } },
            dismissButton = { TextButton({ confirmClear = false }) { Text(st.keep) } },
            text = { Text(st.clearHistoryConfirm) },
        )
    }
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
