package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Action
import com.riverrunner.engine.ActionType
import com.riverrunner.engine.Bot
import com.riverrunner.engine.BotStyle
import com.riverrunner.engine.Cards
import com.riverrunner.engine.Equity
import com.riverrunner.engine.HandEngine
import com.riverrunner.engine.HandEvaluator
import com.riverrunner.engine.HandRecord
import com.riverrunner.engine.MoveType
import com.riverrunner.engine.ReplayFrame
import com.riverrunner.engine.Replayer
import com.riverrunner.engine.Street
import com.riverrunner.engine.strategy.PolicySnapshot
import com.riverrunner.engine.strategy.StudyCoach
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun ReplayScreen(vm: AppViewModel, initial: HandRecord) {
    // during review mode the replay sits between hands: leaving it deals the next one
    val leave: () -> Unit = {
        if (vm.reviewing) {
            vm.continueFromReview()
        } else {
            vm.screen = Screen.History
        }
    }
    BackHandler(onBack = leave)
    var record by remember { mutableStateOf(initial) }
    ReplayContent(
        record = record,
        fmt = { vm.fmt(it, record.bigBlind) },
        onBack = leave,
        onStar = { vm.toggleFavorite(record.id)?.let { record = it } },
        onContinue = if (vm.reviewing) vm::continueFromReview else null,
    )
}

/** Builds the table picture for one replay frame. */
fun replayTable(st: Strings, record: HandRecord, frames: List<ReplayFrame>, idx: Int, equities: Map<Int, Double>?, fmt: (Int) -> String): TableUi {
    val frame = frames[idx]
    val names = record.players.associate { it.seat to if (it.seat == record.heroSeat) st.you else it.name }
    val styles = record.players.associate { it.seat to it.style }
    val seatsInOrder = record.players.map { it.seat }.sorted()
    val btnIdx = seatsInOrder.indexOf(record.buttonSeat)
    val order = List(seatsInOrder.size) { seatsInOrder[(btnIdx + 1 + it) % seatsInOrder.size] }
    // latest action of each seat on the current street
    val last = HashMap<Int, Action>()
    for (i in 1..idx) {
        val a = frames[i].action
        if (a == null) last.clear() else last[a.seat] = a
    }
    val live = frame.players.filter { !it.folded }
    val atShowdown = frame.isEnd && live.size >= 2 && frame.board.size == 5
    val boardMask = Cards.mask(frame.board.toIntArray())
    var highlight = emptySet<Int>()
    var banner: String? = null
    if (frame.isEnd) {
        val winners = frame.players.filter { it.stack > it.startStack }
        if (atShowdown && winners.isNotEmpty()) {
            val w = winners.maxBy { it.stack - it.startStack }
            highlight = HandEvaluator.bestFive((frame.board + w.hole.toList()).toIntArray()).toSet()
        }
        banner = winners.joinToString(" / ") { "${st.winsFor(names.getValue(it.seat))} ${fmt(it.stack - it.startStack)}" }
            .ifEmpty { null }
    }
    val seats = frame.players.sortedBy { it.seat }.map { p ->
        val a = if (frame.isEnd) null else last[p.seat]
        SeatUi(
            seat = p.seat, name = names.getValue(p.seat), stack = p.stack,
            streetBet = if (frame.isEnd) 0 else p.streetBet,
            folded = p.folded, allIn = p.allIn, isHero = p.seat == record.heroSeat,
            isButton = p.seat == record.buttonSeat, isActing = !frame.isEnd && frame.toAct == p.seat,
            hole = p.hole, faceUp = true,
            position = HandEngine.positionName(order.indexOf(p.seat), order.size),
            action = a?.let { TableMapper.actionLabel(st, it, fmt) },
            actKind = a?.let { TableMapper.actKind(it) },
            won = if (frame.isEnd) maxOf(0, p.stack - p.startStack) else 0,
            handName = if (atShowdown && !p.folded) HandEvaluator.categoryName(HandEvaluator.evaluate(boardMask or Cards.mask(p.hole)), st.isZh) else null,
            equity = if (!p.folded && !frame.isEnd) equities?.get(p.seat) else null,
            tag = if (p.seat != record.heroSeat && styles[p.seat] != BotStyle.GTO) styles[p.seat]?.let { st.styleName(it) } else null,
        )
    }
    val pot = if (frame.isEnd) 0 else frame.pot - frame.players.sumOf { it.streetBet }
    return TableUi(seats, frame.board, pot, record.bigBlind, record.id, idx, highlight, banner)
}

fun replayLog(st: Strings, record: HandRecord, frames: List<ReplayFrame>, fmt: (Int) -> String): List<LogLine> {
    val names = record.players.associate { it.seat to if (it.seat == record.heroSeat) st.you else it.name }
    return frames.mapIndexed { i, f ->
        val a = f.action
        when {
            a != null -> LogLine(LogKind.ACTION, "${names.getValue(a.seat)} ${TableMapper.actionLabel(st, a, fmt)}", a.seat)
            i == 0 -> LogLine(LogKind.STREET, streetName(st, f.street))
            else -> TableMapper.streetLine(st, f.street, f.board)
        }
    }
}

@Composable
fun ReplayContent(
    record: HandRecord,
    fmt: (Int) -> String,
    onBack: (() -> Unit)?,
    onStar: () -> Unit,
    startIndex: Int = 0,
    onContinue: (() -> Unit)? = null,
    decisions: List<HistoricalDecision> = emptyList(),
    onFrameChanged: (Int) -> Unit = {},
) {
    val st = LocalStrings.current
    val replayer = remember(record.id) { Replayer(record) }
    val frames = replayer.frames
    var idx by remember(record.id) { mutableIntStateOf(startIndex.coerceIn(0, frames.size - 1)) }
    val jumpTo: (Int) -> Unit = { index ->
        idx = index.coerceIn(0, frames.lastIndex)
        onFrameChanged(idx)
    }
    val frame = frames[idx]

    // Equity for the players still in, cached by board and live seats.
    val equityCache = remember(record.id) { mutableStateMapOf<String, Map<Int, Double>>() }
    val live = frame.players.filter { !it.folded }
    val eqKey = "${frame.board.size}:" + live.joinToString(",") { it.seat.toString() }
    LaunchedEffect(eqKey) {
        if (equityCache[eqKey] == null && live.size >= 2) {
            val eq = withContext(Dispatchers.Default) {
                Equity.showdown(live.map { it.hole }, frame.board, Random(record.id), 30000)
            }
            equityCache[eqKey] = live.mapIndexed { i, p -> p.seat to eq[i] }.toMap()
        }
    }

    // hero decision on this frame (just taken, or about to be taken)
    val actionsSoFar = frames.take(idx + 1).count { it.action != null }
    val decisionIndex = when {
        frame.action?.seat == record.heroSeat -> actionsSoFar - 1
        frame.toAct == record.heroSeat && !frame.isEnd -> actionsSoFar
        else -> -1
    }
    val voluntary = record.actions.filter { !it.type.isPost }
    val heroAction = if (decisionIndex >= 0) voluntary.getOrNull(decisionIndex) else null
    val savedDecisions = remember(decisions) { decisions.associateBy { it.trace.actionIndex } }
    val savedDecision = savedDecisions[decisionIndex]
    val wasHeroAction = frame.action?.seat == record.heroSeat
    val modelGap = remember(record.id, savedDecision, wasHeroAction) {
        if (wasHeroAction && savedDecision != null) {
            StudyCoach.assess(record.id, savedDecision.view, savedDecision.trace, savedDecision.action).modelGapBB
        } else null
    }
    val policyCache = remember(record.id) { mutableStateMapOf<Int, PolicySnapshot>() }
    LaunchedEffect(record.id, decisionIndex, savedDecision) {
        if (decisionIndex >= 0 && savedDecision == null && policyCache[decisionIndex] == null) {
            val p = withContext(Dispatchers.Default) {
                runCatching {
                    val e = replayer.engineBeforeAction(decisionIndex)
                    PolicySnapshot.from(Bot.decide(e, record.heroSeat, BotStyle.GTO, Random(record.id + decisionIndex)))
                }.getOrNull()
            }
            if (p != null) policyCache[decisionIndex] = p
        }
    }
    val policy = savedDecision?.trace?.baseline ?: policyCache[decisionIndex]
    val table = replayTable(st, record, frames, idx, equityCache[eqKey], fmt)
    val log = remember(record.id, st) { replayLog(st, record, frames, fmt) }
    var explainExpanded by remember(record.id) { mutableStateOf(true) }
    val opponentAction = frame.action?.takeIf { it.seat != record.heroSeat && !it.type.isPost }
    val opponent = opponentAction?.let { action -> record.players.firstOrNull { it.seat == action.seat } }

    Row(Modifier.fillMaxSize().background(Palette.Bg)) {
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 4.dp, top = 4.dp, bottom = 2.dp)) {
            PokerTable(table, fmt, st.pot, Modifier.fillMaxSize(), animateDeal = false)
        }
        Column(
            Modifier.width(240.dp).fillMaxHeight().background(Palette.Panel).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (onBack != null) SmallButton("←", onBack)
                Column(Modifier.weight(1f)) {
                    Text(
                        "${st.replay} · ${st.handNo(record.handNumber)}", color = Palette.Text, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                    Text(
                        streetName(st, frame.street) + " · ${idx + 1} / ${frames.size}",
                        color = Palette.TextDim, fontSize = 11.sp, maxLines = 1,
                    )
                }
                SmallButton(if (record.favorite) "★" else "☆", onStar,
                    Modifier.semantics {
                        contentDescription = if (record.favorite) {
                            if (st.isZh) "取消收藏本手" else "Remove hand from favorites"
                        } else st.starHand
                    }, highlight = record.favorite)
            }
            ReplayControls(idx, frames.lastIndex, jumpTo)
            if (!frame.isEnd && live.size >= 2) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Palette.Blue))
                    Text(if (st.isZh) "蓝色百分比 · 摊牌权益（含平分）" else "Blue % · showdown equity incl. splits",
                        color = Palette.TextDim, fontSize = 10.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (st.isZh) "行动记录 · 点击一条可查看原因" else "Action log · tap to inspect",
                    modifier = Modifier.weight(1f), color = Palette.TextDim, fontSize = 10.sp, maxLines = 2)
                if (heroAction != null || opponentAction != null) {
                    SmallButton(if (explainExpanded) "−" else "+", { explainExpanded = !explainExpanded })
                }
            }
            if (explainExpanded && (heroAction != null || opponentAction != null)) {
                Column(Modifier.weight(.54f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (heroAction != null) {
                        val openingBet = savedDecision?.view?.legal?.isOpeningBet
                            ?: (heroAction.type == ActionType.BET || heroAction.type == ActionType.CHECK && frame.street != Street.PREFLOP)
                        AnalysisCard(heroAction, wasHeroAction, policy, modelGap, openingBet, fmt)
                    }
                    if (opponentAction != null && opponent != null) {
                        OpponentInsightCard(opponentAction, opponent, frame.board, st, fmt)
                    }
                }
                HorizontalDivider(color = Palette.Line)
                ActionLog(log, Modifier.weight(.46f), highlightIndex = idx, onClick = jumpTo)
            } else {
                ActionLog(log, Modifier.weight(1f), highlightIndex = idx, onClick = jumpTo)
            }
            if (onContinue != null) {
                ActionButton(st.reviewNext, Palette.Gold, Modifier.fillMaxWidth(), textColor = Palette.Ink, onClick = onContinue)
            }
        }
    }
}

@Composable
private fun ReplayControls(index: Int, last: Int, onJump: (Int) -> Unit) {
    val zh = LocalStrings.current.isZh
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val controls = listOf(
            (if (zh) "开头" else "First") to 0,
            (if (zh) "上一步" else "Prev") to (index - 1),
            (if (zh) "下一步" else "Next") to (index + 1),
            (if (zh) "结尾" else "Last") to last,
        )
        for ((label, target) in controls) {
            OutlinedButton(
                onClick = { onJump(target) }, enabled = target in 0..last && target != index,
                modifier = Modifier.weight(1f).heightIn(min = 38.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) { Text(label, fontSize = 11.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun OpponentInsightCard(
    action: Action, player: com.riverrunner.engine.RecordPlayer, board: List<Int>,
    st: Strings, fmt: (Int) -> String,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Palette.PanelHi).padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text((if (st.isZh) "对手行动解读 · " else "Opponent decision · ") + player.name,
            color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text((if (st.isZh) "实际行动：" else "Recorded action: ") +
            TableMapper.actionLabel(st, action, fmt), color = Palette.Text, fontSize = 12.sp)
        Text((if (st.isZh) "模拟对手风格：" else "Bot preset: ") + st.styleName(player.style),
            color = Palette.TextDim, fontSize = 11.sp)
        Text((if (st.isZh) "赛后回看对手底牌：" else "Opponent hole cards, revealed in replay: ") +
            Cards.format(player.hole), color = Palette.Text, fontSize = 12.sp)
        if (board.size >= 3) {
            val visibleCards = player.hole.toList() + board
            val rank = HandEvaluator.evaluate(Cards.mask(visibleCards.toIntArray()))
            Text((if (st.isZh) "本街牌型：" else "Hand on this street: ") +
                HandEvaluator.categoryName(rank, st.isZh), color = Palette.TextDim, fontSize = 11.sp)
        }
        Text(StudyPresentation.opponentReason(action, player.style, st.isZh),
            color = Palette.Text, fontSize = 12.sp)
        Text(if (st.isZh)
            "对手底牌只供赛后回看；上述可能原因来自行动与预设类型，并非 AI 当时的内部推理或真实 GTO 判断。"
            else "A plausible explanation from the public action and preset, not a recorded internal bot rationale or verified GTO.",
            color = Palette.TextDim, fontSize = 10.sp)
    }
}

@Composable
private fun AnalysisCard(
    heroAction: Action, decided: Boolean, policy: PolicySnapshot?, modelGapBB: Double?, openingBet: Boolean, fmt: (Int) -> String,
) {
    val st = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (decided) {
            Text("${st.yourAction}: ${TableMapper.actionLabel(st, heroAction, fmt)}", color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (policy == null) {
            Text(st.computing, color = Palette.TextDim, fontSize = 12.sp)
            return@Column
        }
        Text(if (st.isZh) "启发式基准频率" else "Heuristic frequencies", color = Palette.Accent, fontSize = 12.sp)
        val dist = policy.choices.groupBy { it.move.type }.mapValues { (_, choices) -> choices.sumOf { it.probability } }
        val frequencies = MoveType.entries.mapNotNull { type -> dist[type]?.takeIf { it > 0 }?.let { type to it } }
        StrategyFrequencyBar(frequencies)
        for ((type, frequency) in frequencies) {
            Text(StudyPresentation.moveType(type, st.isZh, openingBet) + " " + Format.pct(frequency),
                color = strategyActionColor(type), fontSize = 11.sp)
        }
        val bets = policy.choices.filter { it.move.type == MoveType.RAISE && it.probability > 0 }
        if (bets.isNotEmpty()) Text(
            bets.joinToString(" · ") { fmt(it.move.raiseTo) + " " + Format.pct(it.probability) },
            color = Palette.TextDim, fontSize = 11.sp,
        )
        if (decided) {
            val frequency = StudyCoach.moveOf(heroAction)?.let { dist[it.type] } ?: 0.0
            if (frequency < 0.15) Text(
                (if (st.isZh) "基准低频行动 · " else "Low-frequency action · ") + Format.pct(frequency),
                color = Palette.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
            if (modelGapBB != null) {
                val delta = if (modelGapBB < .005) 0.0 else -modelGapBB
                Text((if (st.isZh) "相对模型最优 EV · " else "EV versus model best · ") + "%.2f BB".format(delta),
                    color = if (delta < 0) Palette.Gold else Palette.TextDim, fontSize = 12.sp)
            }
        }
        policy.equity?.let { Text("${st.estEquity}: ${Format.pct(it)}", color = Palette.TextDim, fontSize = 11.sp) }
        if (policy.potOdds > 0) Text("${st.needEquity}: ${Format.pct(policy.potOdds)}", color = Palette.TextDim, fontSize = 11.sp)
    }
}
