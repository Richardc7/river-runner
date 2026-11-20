package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.GameSession
import com.riverrunner.engine.SessionSummary
import com.riverrunner.i18n.LocalStrings
import java.text.DecimalFormat

@Composable
fun ResultScreen(vm: AppViewModel, summary: SessionSummary) {
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
        Column(
            Modifier.width(268.dp).fillMaxHeight()
                .studioCard(accent = Palette.Gold, radius = 16.dp, highlighted = !cash && summary.heroPlace == 1 && !earlyTournament)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!cash) Text(st.result, color = Palette.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
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
                    color = profitColor, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                )
            }
            Text("${st.handsPlayed}: ${summary.handsPlayed}", color = Palette.TextDim, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            ActionButton(st.playAgain, Palette.RaiseBtn, textColor = Palette.Ink, modifier = Modifier.fillMaxWidth()) { vm.playAgain() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(st.history, Palette.PanelHi, modifier = Modifier.weight(1f)) { vm.openHistory() }
                ActionButton(st.home, Palette.PanelHi, modifier = Modifier.weight(1f)) { vm.screen = Screen.Home }
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight().studioCard(radius = 16.dp).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (cash) item {
                SectionTitle(if (st.isZh) "离桌筹码" else "Closing stacks")
            }
            items(if (cash) summary.standings.sortedBy { it.seat } else summary.standings) { s ->
                Row(
                    Modifier.fillMaxWidth()
                        .studioCard(accent = if (s.isHuman) Palette.Gold else Palette.Line, radius = 10.dp, highlighted = s.isHuman)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!cash) {
                        Text(
                            "#${s.place}", color = if (s.place == 1) Palette.Gold else Palette.Text, fontSize = 20.sp, fontFamily = Numeric,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp),
                        )
                    }
                    Text(
                        if (s.isHuman) st.you else s.name,
                        color = Palette.Text, fontSize = 15.sp,
                        fontWeight = if (s.isHuman) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    val stackText = when {
                        cash && vm.showInBB -> settlementAmount.format(s.stack.toDouble() / GameSession.BASE_BB) + " BB"
                        cash || s.stack > 0 -> Format.amount(s.stack, GameSession.BASE_BB, false)
                        else -> st.busted
                    }
                    Text(stackText, color = if (s.isHuman) Palette.Gold else Palette.TextDim, fontSize = 14.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
