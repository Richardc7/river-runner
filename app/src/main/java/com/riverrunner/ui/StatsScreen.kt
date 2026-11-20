package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Achievements
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.HeroStats
import com.riverrunner.engine.NetPoint
import com.riverrunner.engine.Progress
import com.riverrunner.engine.Stats
import com.riverrunner.i18n.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PERCENT_MINIMUM = 50
private const val WIN_RATE_MINIMUM = 100

@Composable
fun StatsScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    BackHandler { vm.screen = Screen.Home }
    var mode by remember(vm.profile.id) { mutableStateOf<GameMode?>(null) }
    val history = vm.history
    val selectedMode = mode
    val stats by produceState<HeroStats?>(null, history, selectedMode) {
        value = null
        value = withContext(Dispatchers.IO) {
            val records = history.all()
            Stats.compute(if (selectedMode == null) records else records.filter { it.mode == selectedMode })
        }
    }
    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallButton("← " + st.back, { vm.screen = Screen.Home })
            Text(st.stats, color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Pill(st.all, mode == null, { mode = null })
            Pill(st.cash, mode == GameMode.CASH, { mode = GameMode.CASH })
            Pill(st.tournament, mode == GameMode.TOURNAMENT, { mode = GameMode.TOURNAMENT })
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatsPanel(Modifier.weight(1.1f)) {
                val current = stats
                if (current == null) {
                    SectionTitle(st.heroStatsTitle)
                    Text(st.computing, color = Palette.TextDim, fontSize = 13.sp)
                } else SampleStatistics(current)
            }
            StatsPanel(Modifier.weight(1.16f)) {
                SectionTitle(if (st.isZh) "累计输赢趋势" else "Cumulative results")
                if (stats?.netTrend?.isNotEmpty() == true) {
                    val current = requireNotNull(stats)
                    NetTrendChart(current.netTrend)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Palette.Line)
                    SectionTitle(st.byPosition)
                    val order = listOf("UTG", "UTG+1", "UTG+2", "UTG+3", "HJ", "CO", "BTN", "SB", "BB")
                    for (position in (order + current.byPosition.keys).distinct()) {
                        current.byPosition[position]?.let { StatLine(position, "%.1f".format(it), it) }
                    }
                } else if (stats != null) {
                    Text(if (st.isZh) "还没有可用于统计的牌局。" else "No recorded hands for this selection.",
                        color = Palette.TextDim, fontSize = 13.sp)
                }
            }
            StatsPanel(Modifier.weight(1f)) {
                val profile = vm.profile
                fun counter(key: String) = profile.counter(key)
                val cashBB = counter(Progress.CASH_PROFIT_CENTI_BB) / 100.0
                SectionTitle(st.careerTitleFor(profile.name))
                StatLine(st.handsLabel, "%,d".format(counter(Progress.HANDS)))
                StatLine(st.sessions, "${counter(Progress.CASH_SESSIONS)}")
                StatLine(st.cashProfitBB, "%.1f".format(cashBB), cashBB)
                StatLine(st.tournamentsPlayed, "${counter(Progress.TOURNEYS)}")
                StatLine(st.tournamentWins, "${counter(Progress.TOURNEY_WINS)}")
                StatLine(st.itm, "${counter(Progress.TOURNEY_ITM)}")
                val points = counter(Progress.TOURNEY_POINTS)
                StatLine(st.tourneyPoints, "$points", points.toDouble())
                StatLine(st.achievements, "${profile.unlocked.size} / ${Achievements.all.size}")
            }
        }
    }
}

@Composable
private fun StatsPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxHeight().studioCard(radius = 16.dp)
        .verticalScroll(rememberScrollState()).padding(14.dp), content = content)
}

private data class SampleMetric(
    val label: String,
    val value: String,
    val samples: Int,
    val minimum: Int,
    val definition: String,
)

@Composable
private fun SampleStatistics(s: HeroStats) {
    val st = LocalStrings.current
    fun t(zh: String, en: String) = if (st.isZh) zh else en
    var selected by remember(s) { mutableStateOf<SampleMetric?>(null) }
    val metrics = listOf(
        SampleMetric(st.vpip, Format.pct(s.vpip), s.hands, PERCENT_MINIMUM,
            t("翻牌前自愿跟注或加注的手数，占所打手数的比例。强制下注不计入。",
                "Share of hands with a voluntary preflop call or raise. Forced bets do not count.")),
        SampleMetric(st.pfr, Format.pct(s.pfr), s.hands, PERCENT_MINIMUM,
            t("翻牌前主动加注的手数，占所打手数的比例。",
                "Share of hands in which you raised preflop.")),
        SampleMetric(t("再加注率 3-Bet", st.threeBet), Format.pct(s.threeBet), s.threeBetOpportunities, PERCENT_MINIMUM,
            t("你第一次翻牌前行动时，面对恰好一次加注并再加注的比例。",
                "How often you re-raised when your first preflop decision faced exactly one raise.")),
        SampleMetric(st.wtsd, Format.pct(s.wtsd), s.flopsSeen, PERCENT_MINIMUM,
            t("在看到翻牌的手数中，你打到摊牌的比例。",
                "How often you reached showdown after seeing the flop.")),
        SampleMetric(st.wsd, Format.pct(s.wsd), s.showdowns, PERCENT_MINIMUM,
            t("在打到摊牌的手数中，你净赢筹码的比例。",
                "How often you finished a showdown hand with a net chip gain.")),
        SampleMetric(st.bbPer100, "%.1f".format(s.bbPer100), s.hands, WIN_RATE_MINIMUM,
            t("每 100 手平均净赢的大盲数。每手按当时的大盲折算。",
                "Average net big blinds per 100 hands. Each hand uses its own big blind.")),
    )
    SectionTitle(st.heroStatsTitle + t(" · ${s.hands} 手", " · ${s.hands} hands"))
    if (metrics.any { it.samples < it.minimum }) {
        Text(t("样本不足时以 — 显示。", "— indicates too few samples."),
            color = Palette.Gold, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
    }
    for (metric in metrics) {
        val enough = metric.samples >= metric.minimum
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button,
                onClickLabel = t("查看指标解释", "Explain metric")) { selected = metric }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(metric.label, color = Palette.Text, fontSize = 12.sp)
                val count = if (enough) "${metric.samples}" else "${metric.samples} / ${metric.minimum}"
                Text(t("$count 个样本", "$count samples"), color = Palette.TextDim, fontSize = 10.sp)
            }
            Text(if (enough) metric.value else "—", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (enough) Palette.Text else Palette.TextDim)
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Palette.Line)
    StatLine(st.netBB, "%.1f".format(s.netBB), s.netBB)
    selected?.let { metric ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(metric.label) },
            text = {
                Text(metric.definition + "\n\n" + t(
                    "当前有 ${metric.samples} 个有效样本，达到 ${metric.minimum} 个后显示该指标。",
                    "${metric.samples} eligible samples. This metric is shown from ${metric.minimum} samples.",
                ))
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text(st.back) } },
        )
    }
}

@Composable
private fun NetTrendChart(points: List<NetPoint>) {
    val zh = LocalStrings.current.isZh
    val dateFormat = remember { SimpleDateFormat("MM-dd", Locale.getDefault()) }
    val first = dateFormat.format(Date(points.first().time))
    val last = dateFormat.format(Date(points.last().time))
    val low = minOf(0.0, points.minOf { it.cumulativeBB })
    val high = maxOf(0.0, points.maxOf { it.cumulativeBB })
    val bottom = if (high == low) -1.0 else low
    val top = if (high == low) 1.0 else high
    val net = points.last().cumulativeBB
    val line = when {
        net > 0 -> Palette.Green
        net < 0 -> Palette.Red
        else -> Palette.Gold
    }
    val signedNet = (if (net > 0) "+" else "") + "%.1f BB".format(net)
    Text(signedNet, color = line, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth().height(114.dp).padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("%.1f".format(top), color = Palette.TextDim, fontSize = 10.sp)
            Text("%.1f".format(bottom), color = Palette.TextDim, fontSize = 10.sp)
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().semantics {
            contentDescription = if (zh) "$first 至 $last，${points.size} 手，累计 $signedNet"
            else "$first to $last, ${points.size} hands, cumulative $signedNet"
        }) {
            val inset = 3.dp.toPx()
            val height = (size.height - inset * 2).coerceAtLeast(1f)
            val width = (size.width - inset * 2).coerceAtLeast(1f)
            fun y(value: Double) = inset + ((top - value) / (top - bottom)).toFloat() * height
            val zero = y(0.0)
            drawLine(Palette.Line, Offset(inset, zero), Offset(inset + width, zero), 1.dp.toPx())
            val path = Path().apply {
                moveTo(inset, zero)
                points.forEachIndexed { index, point ->
                    lineTo(inset + width * (index + 1) / points.size, y(point.cumulativeBB))
                }
            }
            val area = Path().apply {
                addPath(path)
                lineTo(inset + width, zero)
                close()
            }
            drawPath(area, line.copy(alpha = .12f))
            drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(line, 3.dp.toPx(), Offset(inset + width, y(net)))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(first, color = Palette.TextDim, fontSize = 10.sp)
        Text(if (zh) "按对局时间排列 · BB" else "Hands in time order · BB", color = Palette.TextDim, fontSize = 10.sp)
        Text(last, color = Palette.TextDim, fontSize = 10.sp)
    }
}

@Composable
private fun StatLine(label: String, value: String, signed: Double? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Palette.TextDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = when {
                signed == null -> Palette.Text
                signed > 0 -> Palette.Green
                signed < 0 -> Palette.Red
                else -> Palette.Text
            })
    }
}
