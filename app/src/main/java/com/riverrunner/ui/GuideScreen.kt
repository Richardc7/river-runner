package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards
import com.riverrunner.i18n.LocalStrings
import kotlin.math.cos
import kotlin.math.sin

enum class GuideTab { BASICS, RANKINGS, POSITIONS, MATH, GLOSSARY, APP, TIPS }

@Composable
fun GuideScreen(vm: AppViewModel) {
    val close = { vm.screen = vm.guideReturnScreen }
    BackHandler(onBack = close)
    GuideContent(onClose = close, startTab = vm.guideTab)
}

@Composable
fun GuideContent(onClose: () -> Unit, startTab: GuideTab = GuideTab.BASICS) {
    val st = LocalStrings.current
    val zh = st.isZh
    val focus = LocalFocusManager.current
    var tab by rememberSaveable(startTab) { mutableStateOf(startTab) }
    var termQuery by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(tab, termQuery) { scroll.scrollTo(0) }
    Row(Modifier.fillMaxSize().background(Palette.Bg).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallButton("←", onClose)
                Spacer(Modifier.width(8.dp))
                Text(st.guide, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).selectableGroup().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (target in GuideTab.entries) {
                    val selected = target == tab
                    val shape = RoundedCornerShape(8.dp)
                    Box(Modifier.fillMaxWidth().clip(shape)
                        .background(if (selected) Palette.AccentDim else Palette.Panel)
                        .then(if (selected) Modifier.border(1.dp, Palette.Accent, shape) else Modifier)
                        .selectable(selected, role = Role.Tab) {
                            focus.clearFocus()
                            tab = target
                        }
                        .padding(horizontal = 10.dp, vertical = 7.dp)) {
                        Text(tabTitle(target, zh), color = Palette.Text, fontSize = 13.sp, lineHeight = 17.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(Palette.Panel)
                .padding(14.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                GuideTab.BASICS -> Basics(zh)
                GuideTab.RANKINGS -> Rankings(zh)
                GuideTab.POSITIONS -> Positions(zh)
                GuideTab.MATH -> QuickPokerMath(zh)
                GuideTab.GLOSSARY -> Glossary(zh, termQuery) { termQuery = it }
                GuideTab.TIPS -> Paragraphs(if (zh) TIPS_ZH else TIPS_EN)
                GuideTab.APP -> GuideFeatures(zh)
            }
        }
    }
}

private fun tabTitle(t: GuideTab, zh: Boolean) = when (t) {
    GuideTab.BASICS -> if (zh) "基本玩法" else "How to play"
    GuideTab.RANKINGS -> if (zh) "牌型大小" else "Hand rankings"
    GuideTab.POSITIONS -> if (zh) "位置" else "Positions"
    GuideTab.MATH -> if (zh) "扑克速算" else "Quick odds"
    GuideTab.GLOSSARY -> if (zh) "术语表" else "Glossary"
    GuideTab.APP -> if (zh) "功能指南" else "App features"
    GuideTab.TIPS -> if (zh) "新手建议" else "Beginner tips"
}

@Composable
private fun H(text: String) = Text(text, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)

@Composable
private fun P(text: String) = Text(text, color = Palette.Text, fontSize = 13.sp, lineHeight = 20.sp)

@Composable
private fun Paragraphs(items: List<Pair<String, String>>) {
    for ((title, body) in items) {
        H(title)
        P(body)
        Spacer(Modifier.height(2.dp))
    }
}

// ----------------------------------------------------------------- basics

@Composable
private fun Basics(zh: Boolean) {
    if (zh) {
        H("目标")
        P("每人发 2 张只有自己能看的底牌，桌面中央再陆续发出 5 张所有人共用的公共牌。用你的 2 张底牌加 5 张公共牌，任选 5 张组成最大的牌型。要么在摊牌时牌最大，要么通过下注让别人全部弃牌，就能赢得底池。")
        H("一手牌的流程")
        Steps(listOf(
            "盲注" to "庄家按钮(D)左边两位强制下注：小盲(SB)下半个大盲，大盲(BB)下一个大盲。锦标赛后期每人还要交前注(Ante)。",
            "翻牌前" to "每人拿到 2 张底牌。三人及以上通常从大盲左边（UTG）开始。单挑时按钮兼小盲，翻前先行动，翻后最后行动。",
            "翻牌" to "发出 3 张公共牌，进行第二轮下注。从这一轮起，由按钮左边第一位还在牌局里的玩家先行动。",
            "转牌" to "发出第 4 张公共牌，第三轮下注。",
            "河牌" to "发出第 5 张公共牌，最后一轮下注。",
            "摊牌" to "还剩两人以上时亮牌比大小，牌型最大者赢得底池；一样大则平分。",
        ))
        H("可以做的动作")
        Steps(listOf(
            "弃牌" to "放弃这手牌，已经下注的筹码留在底池里。",
            "过牌" to "本轮还没人下注时，不下注，把行动交给下一位。",
            "跟注" to "补齐到当前最高下注额。",
            "下注 / 加注" to "主动下注，或在别人下注的基础上加码。无限注德州里，加注额至少要等于上一次的加注幅度，最多可以全下。",
            "全下(All-in)" to "把所有筹码推进去。筹码不够跟注时也可以全下，这时会形成边池：你只能赢到你投入部分对应的底池。",
        ))
        H("按钮和盲注每手轮转")
        P("每打完一手，庄家按钮顺时针移动一位，所以每个人都会轮流坐到每个位置。")
    } else {
        H("Goal")
        P("Each player gets 2 private hole cards; 5 shared community cards are dealt in the middle. Make the best 5-card hand from your 2 cards plus the 5 on the board. You win the pot either by having the best hand at showdown or by betting so that everyone else folds.")
        H("How a hand goes")
        Steps(listOf(
            "Blinds" to "The two players left of the dealer button (D) post forced bets: small blind (half a big blind) and big blind. Later in tournaments everyone also posts an ante.",
            "Preflop" to "Everyone gets two cards. At tables with three or more players, action usually starts left of the big blind (UTG). Heads-up, the button posts the small blind and acts first preflop, last after the flop.",
            "Flop" to "3 community cards, then a betting round. From now on the first active player left of the button acts first.",
            "Turn" to "The 4th community card and a betting round.",
            "River" to "The 5th community card and the last betting round.",
            "Showdown" to "If two or more players remain, hands are shown; the best hand wins, ties split the pot.",
        ))
        H("Actions")
        Steps(listOf(
            "Fold" to "Give up the hand; chips already bet stay in the pot.",
            "Check" to "Pass the action when nobody has bet this round.",
            "Call" to "Match the current highest bet.",
            "Bet / Raise" to "Put chips in first, or increase someone's bet. In no-limit a raise must be at least the size of the previous raise; the maximum is all-in.",
            "All-in" to "Push all your chips. If you can't cover a bet you can still go all-in; a side pot is created and you can only win as much from each player as you put in.",
        ))
        H("The button moves every hand")
        P("After each hand the dealer button moves one seat clockwise, so everyone takes turns in every position.")
    }
}

@Composable
private fun Steps(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((i, it) in items.withIndex()) {
            Row {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Palette.AccentDim), contentAlignment = Alignment.Center) {
                    Text("${i + 1}", color = Palette.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(it.first, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(it.second, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------------------- rankings

private data class RankRow(val zh: String, val en: String, val cards: String, val zhDesc: String, val enDesc: String)

private val RANKS = listOf(
    RankRow("皇家同花顺", "Royal flush", "AsKsQsJsTs", "同花色的 A-K-Q-J-10，最大的牌", "A-K-Q-J-10 of one suit"),
    RankRow("同花顺", "Straight flush", "9h8h7h6h5h", "同花色的五张连牌", "Five in a row, same suit"),
    RankRow("四条", "Four of a kind", "QcQdQhQs7d", "四张点数相同", "Four cards of one rank"),
    RankRow("葫芦", "Full house", "KcKdKh4s4d", "三条 + 一对", "Three of a kind plus a pair"),
    RankRow("同花", "Flush", "AdJd8d5d2d", "五张同花色（不连）", "Five of one suit"),
    RankRow("顺子", "Straight", "Tc9d8s7h6c", "五张连续点数；A-2-3-4-5 是最小的顺子", "Five in a row; A-2-3-4-5 is the lowest"),
    RankRow("三条", "Three of a kind", "8c8h8sKdQc", "三张点数相同", "Three cards of one rank"),
    RankRow("两对", "Two pair", "JcJdTs Th4c".replace(" ", ""), "两个对子", "Two different pairs"),
    RankRow("一对", "One pair", "AhAc9s6d3c", "一个对子", "Two cards of one rank"),
    RankRow("高牌", "High card", "AsQd9c6h2s", "以上都不是，比最大的单张", "None of the above; highest card plays"),
)

@Composable
private fun Rankings(zh: Boolean) {
    H(if (zh) "从大到小" else "Strongest to weakest")
    for ((i, r) in RANKS.withIndex()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Palette.PanelHi).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}", color = Palette.Accent, fontSize = 18.sp, fontFamily = Numeric, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            Column(Modifier.width(118.dp)) {
                Text(if (zh) r.zh else r.en, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(if (zh) r.zhDesc else r.enDesc, color = Palette.TextDim, fontSize = 10.sp, lineHeight = 13.sp)
            }
            Spacer(Modifier.width(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in Cards.parseMany(r.cards)) PlayingCard(c, 26.dp)
            }
        }
    }
    H(if (zh) "比大小的细节" else "Tie-breaks")
    P(
        if (zh) "• 花色不分大小。\n• 牌型相同时比点数，再比踢脚(Kicker)：比如都是一对A，就比剩下三张里最大的。\n• 只比最好的5张：如果最好的5张全在公共牌上，所有人平分。\n• 顺子比最高牌。同花逐张从高到低比，五张点数全相同才平分。"
        else "• Suits never matter.\n• Same category: compare ranks, then kickers (e.g. two players with a pair of aces compare their next highest cards).\n• Only the best five cards count — if the board is the best hand, everyone still in splits.\n• Compare the top rank of a straight and all five ranks of a flush. Identical five-card values split the pot."
    )
}

// ----------------------------------------------------------------- positions

@Composable
private fun Positions(zh: Boolean) {
    H(if (zh) "8人桌的位置" else "Positions at an 8-handed table")
    BoxWithConstraints(Modifier.fillMaxWidth().height(190.dp)) {
        val w = maxWidth.value
        val h = maxHeight.value
        val labels = listOf("BTN", "SB", "BB", "UTG", "UTG+1", "UTG+2", "HJ", "CO")
        Canvas(Modifier.fillMaxSize()) {
            val d = density
            val rx = w * 0.30f * d
            val ry = h * 0.32f * d
            val c = Offset(w / 2 * d, h / 2 * d)
            drawOval(Palette.Rail, Offset(c.x - rx - 8 * d, c.y - ry - 8 * d), Size((rx + 8 * d) * 2, (ry + 8 * d) * 2))
            drawOval(Brush.radialGradient(listOf(Palette.FeltCenter, Palette.FeltEdge), c, rx), Offset(c.x - rx, c.y - ry), Size(rx * 2, ry * 2))
        }
        Text(
            if (zh) "顺时针行动 ↻" else "Action moves clockwise ↻", color = Palette.Text.copy(alpha = 0.8f), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        for ((i, l) in labels.withIndex()) {
            val ang = Math.toRadians(90.0 + i * 45.0)
            val x = w / 2 + w * 0.40f * cos(ang).toFloat()
            val y = h / 2 + h * 0.42f * sin(ang).toFloat()
            val color = when (l) {
                "BTN", "CO" -> Palette.Green
                "SB", "BB" -> Palette.Blue
                "HJ" -> Palette.Gold
                else -> Palette.Red
            }
            Box(
                Modifier.offset((x - 30).dp, (y - 11).dp).width(60.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) { Text(l, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
    }
    val rows = if (zh) listOf(
        "BTN · Button 按钮位/庄位" to "只要仍在牌局里，翻牌后最后行动，能先看到其他玩家的选择。",
        "SB · Small Blind 小盲" to "通常先交半个大盲。多人桌仍在局时，翻牌后先行动。单挑时小盲就是按钮，翻后最后行动。",
        "BB · Big Blind 大盲" to "已交一个大盲，跟注时只需补差额。决定是否防守还要看加注大小和翻后位置。",
        "UTG · Under The Gun 枪口位" to "通常是多人桌翻牌前第一个行动的位置，后面待行动的人多，入池范围通常较紧。",
        "UTG+1 / UTG+2 / UTG+3" to "多人桌上，位于枪口位后面一、二、三个行动位置。实际有哪些名称取决于人数。",
        "HJ · Hijack 劫持位" to "关煞位前一位，翻前待行动的人比前位少。",
        "CO · Cutoff 关煞位" to "按钮前一位，属于后位。",
    ) else listOf(
        "BTN · Button" to "Acts last after the flop while still in the hand, with all other actions visible first.",
        "SB · Small Blind" to "Usually posts half a big blind. At multiway tables the small blind acts first after the flop while still in. Heads-up the small blind is the button and acts last postflop.",
        "BB · Big Blind" to "You've already posted a blind and pay only the difference to call. Consider the raise size and postflop position before defending.",
        "UTG · Under The Gun" to "Normally first to act preflop on multiway tables, so opening ranges tend to be tight.",
        "UTG+1 / UTG+2 / UTG+3" to "One, two or three seats after UTG in action order. Which labels appear depends on table size.",
        "HJ · Hijack" to "One seat before the cutoff, with fewer players left to act preflop than early position.",
        "CO · Cutoff" to "The late-position seat immediately before the button.",
    )
    for ((t, d) in rows) {
        Text(t, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(d, color = Palette.TextDim, fontSize = 12.sp, lineHeight = 18.sp)
    }
    H(if (zh) "为什么位置重要" else "Why position matters")
    P(
        if (zh) "后行动可以先看对手怎么做。后面待行动的人少时，通常可用更宽的开池范围。具体范围还会随有效筹码、下注大小和对手打法改变。"
        else "Acting later lets you see what opponents do first. Opening ranges often widen with fewer players left to act. Effective stacks, bet sizes and opponent styles also matter."
    )
}

// ----------------------------------------------------------------- glossary

/** [full]: the full English name of an abbreviation (shown in both languages). */
private data class Term(
    val zh: String, val zhDesc: String, val en: String, val enDesc: String,
    val full: String = "", val aliases: String = "",
) {
    fun matches(query: String): Boolean {
        val terms = listOf(zh, en, full, aliases).joinToString(" ")
        return query.split(Regex("\\s+")).all { terms.contains(it, ignoreCase = true) }
    }
}

private val TERMS = listOf(
    Term("BB / 大盲", "大盲注。本软件用多少个大盲来衡量筹码和下注，比如 100 BB 表示 100 个大盲。锦标赛涨盲后，同样的筹码折合的 BB 数会变少。", "BB", "The big blind. Stacks and bets are measured in big blinds, e.g. 100 BB. When tournament blinds rise, the same chips are worth fewer BB.", "Big Blind"),
    Term("SB / 小盲", "小盲注，等于半个大盲。", "SB", "The small blind: half of a big blind.", "Small Blind"),
    Term("BTN / 按钮位", "庄家按钮所在位置，翻牌后最后行动。", "BTN", "The dealer button seat; acts last after the flop.", "Button"),
    Term("UTG / 枪口位", "翻牌前第一个行动的位置，就像\"在枪口下\"。UTG+1、UTG+2 是它左边的位置。", "UTG", "First to act preflop. UTG+1 and UTG+2 are the seats to its left.", "Under The Gun"),
    Term("HJ / 劫持位", "关煞位右边的中间位置。", "HJ", "The middle-position seat right of the cutoff.", "Hijack"),
    Term("CO / 关煞位", "按钮右边的位置，后位。", "CO", "The seat right of the button; late position.", "Cutoff"),
    Term("前注 Ante", "每人每手都要交的小额强制下注，让底池更大，常见于锦标赛后期。", "Ante", "A small forced bet from every player each hand, common later in tournaments."),
    Term("底池 Pot", "本手所有人已经投入的筹码总和。", "Pot", "All chips put in so far this hand."),
    Term("全下 All-in", "把剩下的筹码全部推进去。", "All-in", "Putting all your remaining chips in the pot."),
    Term("边池 Side pot", "有人全下但筹码比别人少时，超出部分单独形成边池，全下的人赢不到边池。", "Side pot", "When a player is all-in for less, the extra chips form a side pot that player can't win."),
    Term("底池赔率 Pot odds", "游戏以跟注额比当前底池表示。当前底池已包括对手下注，尚未包括你的跟注。当前底池 6 BB、需跟 2 BB 时显示 1 : 3。", "Pot odds", "The app displays amount to call versus the current pot, which includes the opponent's bet. A 2 BB call into a current pot of 6 BB displays 1 : 3."),
    Term("所需胜率", "跟注后直接摊牌且无抽水时，打平所需的权益 = 跟注额 ÷（当前底池 + 跟注额）。", "Equity needed", "The break-even equity for a call going straight to showdown without rake: call ÷ (current pot + call).", aliases = "break even breakeven"),
    Term("摊牌权益 Equity", "发完公共牌后平均能分到的底池份额。两人平分时各计一半。回放中的蓝色百分比按已知底牌计算。", "Showdown equity", "The expected share of the pot after the remaining board is dealt. A two-way split counts as half for each player. Replay's blue percentages use the recorded hole cards.", aliases = "胜率 win chance"),
    Term("SPR / 筹码底池比", "常用算法是有效筹码除以底池。牌桌练习读数用你自己的剩余筹码。当对手更短时，用较小的一方筹码手算。", "SPR", "Usually the effective stack divided by the pot. The live practice readout uses your own remaining stack. Against a shorter opponent, calculate it with the smaller stack.", "Stack-to-Pot Ratio"),
    Term("有效筹码", "两人中筹码较少的一方的筹码量，决定了最多能输赢多少。", "Effective stack", "The smaller of two stacks: the most either can win or lose."),
    Term("范围 Range", "对手在某种行动下可能持有的所有手牌集合。高手思考的是范围，而不是猜一手具体的牌。", "Range", "All the hands a player could have given their actions. Think in ranges, not single hands."),
    Term("GTO / 博弈论最优", "在给定规则和筹码条件下达到均衡的策略。求解器参考依赖输入的范围和下注尺寸。", "GTO", "An equilibrium strategy for the specified game. Solver references depend on the input ranges and bet sizes.", "Game Theory Optimal"),
    Term("EV / 期望值", "在指定假设下，重复使用某个行动的平均结果。复盘常用大盲（BB）衡量。", "EV", "The average result of an action under the stated assumptions, often measured in big blinds.", "Expected Value"),
    Term("弃牌、过牌、跟注、加注", "弃牌是退出本手。无人下注时可过牌。跟注补齐当前下注，加注提高当前下注。", "Fold, check, call, raise", "Fold to leave the hand, check if there is no bet to face, call to match the bet or raise to increase it."),
    Term("剥削 Exploit", "针对对手的漏洞调整打法，比如对跟注站少诈唬、多价值下注。", "Exploit", "Deviating to attack an opponent's leaks, e.g. bluff less and value-bet more against a calling station."),
    Term("RFI / 开池加注", "前面的人都弃牌时第一个加注入池，也叫 Open。", "RFI / Open", "The first raise when everyone before you folded.", "Raise First In"),
    Term("溜入 Limp", "翻牌前只跟注大盲而不加注入池，通常不推荐。", "Limp", "Just calling the big blind preflop instead of raising; usually not recommended."),
    Term("3-Bet / 4-Bet", "翻牌前，大盲算第一注，对首次加注再加注是 3-Bet，继续加注是 4-Bet。本应用的 3-Bet 率以首次翻前行动时面对恰好一次加注的次数为分母。", "3-Bet / 4-Bet", "Preflop, the big blind is bet one, the open is bet two, and the next two raises are a 3-bet and 4-bet. The app's 3-bet denominator counts first preflop decisions facing exactly one raise.", "Third bet / Fourth bet", "再加注率 三下注"),
    Term("C-bet / 持续下注", "翻牌前加注的人在翻牌继续下注。", "C-bet", "The preflop raiser bets again on the flop.", "Continuation Bet"),
    Term("价值下注 Value bet", "用你认为领先的牌下注，希望更差的牌跟注。", "Value bet", "Betting a hand you think is best so worse hands call."),
    Term("诈唬 Bluff", "用很弱的牌下注，希望更好的牌弃牌。半诈唬(Semi-bluff)是用听牌诈唬，被跟注后还有机会成牌。", "Bluff", "Betting a weak hand to make better hands fold. A semi-bluff has a draw that can still improve."),
    Term("听牌 Draw", "还差一张就能成同花或顺子的牌，比如四张同花。", "Draw", "A hand that needs one more card to make a flush or straight."),
    Term("出张 Outs", "能让听牌成牌的未知牌。通常听同花有 9 张，两头顺有 8 张、卡顺有 4 张。重合只算一次，成牌仍可能输的牌需要打折。", "Outs", "Unseen cards that complete the draw. Common counts are 9 for a flush, 8 for an open-ender and 4 for a gutshot. Deduplicate overlaps and discount cards that may still lose.", aliases = "补牌 outs"),
    Term("4/2 法则", "翻牌看至河牌两张用出张 × 4%。只看下一张时用出张 × 2%。算例见「扑克速算」。", "Rule of 4 and 2", "Multiply outs by 4% to estimate hitting by the river from the flop, or by 2% when seeing one more card. See Quick odds for examples.", aliases = "2/4 4.2 42 四二 rule of 2 and 4"),
    Term("踢脚 Kicker", "牌型相同时用来比大小的剩余单张。", "Kicker", "The side card that breaks ties between equal hands."),
    Term("坚果 Nuts", "当前公共牌下最大的牌。", "Nuts", "The best possible hand on the current board."),
    Term("顶对 / 超对 / 暗三条", "顶对 Top pair：用底牌配上公共牌最大的那张。超对 Overpair：口袋对子比所有公共牌都大。暗三条 Set：口袋对子配中公共牌。", "Top pair / Overpair / Set", "Top pair: pairing the highest board card. Overpair: a pocket pair above every board card. Set: a pocket pair that hits the board."),
    Term("翻牌 / 转牌 / 河牌", "第 3、4、5 张公共牌出现的三个阶段（翻牌一次发三张）。", "Flop / Turn / River", "The 3-card, 4th and 5th community card stages."),
    Term("摊牌 Showdown", "最后一轮下注结束后亮牌比大小。", "Showdown", "Showing hands after the last betting round."),
    Term("ICM / 独立筹码模型", "锦标赛里把筹码换算成奖金价值的模型。临近奖励圈时，输光的代价更大，需要更紧。", "ICM", "Converts tournament chips into prize value. Near the money, busting costs more, so play tighter.", "Independent Chip Model"),
    Term("泡沫 Bubble", "离进入奖励圈只差一个人出局的阶段。", "Bubble", "When one more elimination puts everyone left in the money."),
    Term("ITM / 进入奖励圈", "锦标赛中进入有奖名次。", "ITM", "Finishing in a paid place in a tournament.", "In The Money"),
    Term("推/弃 Push/Fold", "短筹码时可用的一种简化策略，只比较全下和弃牌。适用性取决于筹码、前注、位置、对手和奖金结构。", "Push/fold", "A simplified short-stack approach that considers shoving or folding. Its usefulness depends on stacks, antes, position, opponents and payouts."),
    Term("NLHE / 无限注德州", "下注上限只受自己筹码限制的德州扑克，本软件就是这种。", "NLHE", "Hold'em where the only betting limit is your stack — what this app plays.", "No-Limit Hold'em"),
    Term("VPIP / 主动入池率", "翻牌前有过自愿跟注或加注的手数，占所打手数的比例。强制下注不计入。", "VPIP", "Share of hands with a voluntary preflop call or raise. Forced bets do not count.", "Voluntarily Put Money In Pot"),
    Term("PFR / 翻前加注率", "翻牌前加注的比例。和 VPIP 越接近，说明越少溜入/跟注，打法越主动。", "PFR", "How often you raise preflop. The closer to VPIP, the more aggressive.", "Pre-Flop Raise"),
    Term("WTSD / 摊牌率", "看到翻牌后一路打到摊牌的比例。", "WTSD", "How often you reach showdown after seeing the flop.", "Went To ShowDown"),
    Term("W\$SD / 摊牌胜率", "打到摊牌时赢钱的比例。", "W\$SD", "How often you win money when you reach showdown.", "Won \$ at ShowDown"),
    Term("BB/100", "每 100 手平均净赢的 BB，用每手当时的大盲折算。", "BB/100", "Net big blinds won per hundred hands, using each hand’s blind level.", "Big Blinds per 100 hands"),
    Term("TAG / 紧凶", "玩得少，但一旦入池就主动下注加注。", "TAG", "Plays few hands but bets and raises them.", "Tight-Aggressive"),
    Term("LAG / 松凶", "玩得多且主动。", "LAG", "Plays many hands aggressively.", "Loose-Aggressive"),
    Term("跟注站 / 紧弱 / 疯子", "跟注站跟注偏多。紧弱的入池范围很窄。疯子频繁下注和加注。", "Calling station / Nit / Maniac", "A calling station calls often, a nit enters few pots, and a maniac bets and raises often."),
    Term("宽松被动 / 松被动", "入池范围宽，主动下注和加注较少。可在自定义座位里指定。", "Loose-passive", "Enters with a wide range and seldom takes the betting lead. Available as a custom seat style."),
    Term("单挑 / 多人底池", "单挑指只有两人参与。多人底池指至少三人还在争夺。九人桌也可能在后续街只剩两人。", "Heads-up / Multiway", "Heads-up means two players. A multiway pot has at least three contenders. A nine-handed table can still reach a heads-up street.", aliases = "HU heads up"),
    Term("IP / OOP", "IP 是有利位置，本街相对于相关对手后行动。OOP 是不利位置，相对对手先行动。", "IP / OOP", "In position means acting after the relevant opponent on the street. Out of position means acting before them.", "In position / Out of position"),
    Term("起手牌记法 · AA / AJs / AJo", "AA 是口袋对子，AJs 是同花 A 和 J，AJo 是不同花。范围矩阵里两张牌的花色按 s 或 o 标记。", "Hand notation · AA / AJs / AJo", "AA is a pocket pair, AJs is suited ace-jack and AJo is offsuit. The 13×13 grid groups hands by ranks and suitedness.", aliases = "suited offsuit pocket pair 169"),
    Term("两头顺 / 卡顺", "两头顺能从两端补成，例如 5-6-7-8 等 4 或 9，通常有 8 出张。卡顺缺中间一张，例如 5-6-8-9 等 7，通常有 4 出张。", "Open-ended draw / Gutshot", "An open-ender can fill either end, e.g. 5-6-7-8 wants a 4 or 9 (usually eight outs). A gutshot needs an inside rank, e.g. 5-6-8-9 wants a 7 (usually four outs)."),
    Term("隐含赔率 / 反向隐含赔率", "还会下注时，成牌后可能额外赢到的筹码提高跟注价值。若成牌后反而容易输大底池，则要考虑反向隐含赔率。", "Implied odds / Reverse implied odds", "Future chips you could win on a hit add to the value of a call. Reverse implied odds reflect extra losses when improving to a second-best hand."),
    Term("弃牌率 / 弃牌权益", "弃牌率是对手放弃的频率。弃牌权益指你的下注让对手放弃，因而直接赢下底池的价值。", "Fold rate / Fold equity", "Fold rate is how often opponents fold. Fold equity is the value of winning the pot outright when a bet makes opponents fold."),
    Term("枪口位强制加盲 Straddle", "筹码局可选规则。当至少三人在桌且枪口位筹码足够时，枪口位多交 2 BB 的活盲注，翻前起始行动随之顺延。", "Live UTG straddle", "An optional cash rule. With three or more players and enough chips, UTG posts a live 2 BB blind and preflop action starts after it."),
    Term("抽水 / 每手抽水上限 / 未见翻牌不抽水", "抽水是从底池扣除的费用。上限限制一手最多扣多少。开启未见翻牌不抽水时，翻牌前结束的牌免收。", "Rake / Rake cap / No flop, no drop", "Rake is a fee deducted from the pot. The cap is the maximum per hand. With no flop no drop, hands ending preflop pay no rake."),
    Term("重买 / 补码", "重买是在筹码用完后重新取得设定数量的筹码。补码是在两手之间把未用完的筹码补到目标量。投入都计入筹码局结算。", "Rebuy / Top-up", "A rebuy restores the starting stack after busting. A top-up adds chips to a partial stack between hands. Both count toward buy-ins for cash settlement."),
    Term("基准 / 启发式 / 动态剥削", "基准是比较时用的起始策略。启发式是用规则和估算辅助决策。动态剥削根据对手公开行动累积的证据作有限调整。", "Baseline / Heuristic / Adaptive exploit", "The baseline is the starting policy for comparisons. A heuristic uses rules and estimates. Adaptive exploit makes bounded adjustments based on opponents' public actions."),
    Term("证据权重 / 样本不足", "估计对手倾向时，记录得越少通常越应接近基准。比如对手弃了 2 次、共遇到 3 次机会，2/3 只是观察结果。", "Evidence weight / Thin sample", "With few observations, estimates usually stay closer to the baseline. Two folds in three opportunities is an observation with very little evidence."),
    Term("行动频率 / 混合策略", "同一情景可以给多个行动分配概率。例如 70% 跟注、30% 弃牌，指模型如何分配行动。各项相加为 100%。", "Action frequency / Mixed strategy", "A policy can assign probabilities to multiple moves. For example, 70% call and 30% fold describe how it mixes actions, with probabilities adding up to 100%."),
    Term("模型偏离 / 条件模型 EV 差距", "偏离标记用于找出值得复习的决策。有可比较的价值时，EV 差是该行动与模型首选的差额。估计依赖范围、下注尺度和对手反应等假设。", "Model deviation / Conditional model EV gap", "A deviation flag marks a decision worth reviewing. Where values are available, the EV gap compares the move against the model's best. It depends on the assumed range, sizes and opponent responses."),
    Term("范围权重 / 组合", "一种起手牌类别可能对应好几个具体花色组合。范围可给不同组合分配不同权重，已知牌会排除与之冲突的组合。", "Range weights / Combos", "A hand class can contain several suit combinations. A range can give those combos different weights, and known cards rule out conflicting combos."),
    Term("求解器 / 决策场景", "求解器根据给定的规则、范围、筹码和下注尺寸计算策略。一个场景指某个具体的行动节点及其条件。", "Solver / Decision spot", "Solvers compute strategies for specified rules, ranges, stacks and sizes. A decision spot is one action point and its context.", aliases = "求解场景 RR-REFERENCE-1 reference GTO"),
    Term("河牌情景对比 What-if", "以保存的河牌决策为起点，修改模型假设后比较各行动的估算 EV。", "River what-if / Sensitivity", "Compare estimated action EVs when you vary the assumptions of a saved river decision."),
    Term("离桌自动弃牌 / 未行动 / 已复盘", "自动弃牌指离桌时应用代为弃牌。未行动指你没有做过自愿决策。打开复盘后，牌局记录会标记为已复盘。", "Auto-fold on exit / No action / Reviewed", "The app folds for you on exit. No action means you made no voluntary decision. Opening replay marks the hand reviewed."),
    Term("盲测 / 间隔复习", "盲测是先用决策当时的可见信息选行动，再看解析。间隔复习根据已记录的决策和练习结果安排下次复习时间。", "Blind quiz / Spaced review", "A blind quiz asks you to act on what was visible at the time before revealing feedback. Spaced review schedules the next attempt using saved decisions and practice results."),
    Term("有效样本", "每项指标的分母不同。例如摊牌率只统计看到翻牌的手数，摊牌胜率只统计实际摊牌的手数。统计页可点开查看口径。", "Eligible samples", "The denominator depends on the metric. WTSD counts hands where you saw a flop, and W\$SD counts hands where you reached showdown. Tap the metric for its definition."),

)

@Composable
private fun Glossary(zh: Boolean, query: String, onQuery: (String) -> Unit) {
    val focus = LocalFocusManager.current
    var searchFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = searchFocused) { focus.clearFocus() }
    OutlinedTextField(
        value = query,
        onValueChange = { onQuery(it.take(100)) },
        modifier = Modifier.fillMaxWidth().onFocusChanged { searchFocused = it.isFocused },
        label = { Text(if (zh) "搜索中英文术语" else "Search terms in either language") },
        placeholder = { Text(if (zh) "例如 SPR、底池、4/2" else "Try SPR, pot or 4/2") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        trailingIcon = if (query.isBlank()) null else {
            { TextButton(onClick = { onQuery("") }) { Text(if (zh) "清除" else "Clear") } }
        },
    )
    val matches = if (query.isBlank()) TERMS else TERMS.filter { it.matches(query.trim()) }
    if (matches.isEmpty()) P(if (zh) "没有找到匹配术语。" else "No matching terms.")
    for (term in matches) {
        GuideCard(if (zh) term.zh else term.en) {
            if (term.full.isNotEmpty()) {
                Text(term.full, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 15.sp)
            }
            GuideParagraph(if (zh) term.zhDesc else term.enDesc)
        }
    }
}

// ----------------------------------------------------------------- tips

private val TIPS_ZH = listOf(
    "1. 先打紧一点" to "新手最常见的错误是玩太多牌。前位只玩大对子(AA–TT)、AK、AQ、AJs、KQs 这类强牌；越靠近按钮可以越宽。练习模式里的\"起手牌 · 前 X%\"能帮你判断这手牌有多强。",
    "2. 要么加注，要么弃牌" to "前面没人入池时，尽量用加注（约 2.5 个大盲）入池，而不是只跟注大盲(Limp)。",
    "3. 先弄清这次跟注能买几张牌" to "打开练习模式，看跟注额和所需胜率。只买到下一张公共牌时，先用出张 × 2% 估算。后续下注、对手范围和抽水都会影响这次跟注的价值。",
    "4. 重视位置" to "同样的牌，在按钮位比在前位好打得多。位置差时少玩边缘牌。",
    "5. 不要爱上一对" to "顶对在多人底池、湿润牌面、对手大额加注时经常已经落后。对手很被动却突然加注，通常代表强牌。",
    "6. 针对对手调整" to "在混合桌：对跟注站少诈唬、多价值下注；对紧弱玩家多偷盲；对疯子用好牌让他来诈唬你。",
    "7. 每局复盘" to "去\"牌局记录\"打开输得多的手牌，看每条街的胜率和模型参考，优先回看偏离较大的决策。",
    "8. 锦标赛：留意短筹码与奖励圈" to "用 BB 衡量有效筹码。短筹码时考虑全下或弃牌，具体选择仍取决于位置、前注、前面行动和奖金结构。",
)

private val TIPS_EN = listOf(
    "1. Start tight" to "The most common beginner mistake is playing too many hands. From early position stick to big pairs (AA–TT), AK, AQ, AJs, KQs; widen as you get closer to the button. Practice mode shows \"top X%\" for your starting hand.",
    "2. Raise or fold" to "When nobody has entered the pot, come in with a raise (about 2.5 BB) rather than limping.",
    "3. Count how many cards the call buys" to "Use practice mode to see the call cost and equity needed. If the call only buys one card, start with outs × 2%. Future betting, ranges and rake all affect the value of the call.",
    "4. Respect position" to "The same hand is much easier to play on the button. Play fewer marginal hands out of position.",
    "5. Don't marry one pair" to "Top pair is often behind in multi-way pots, on wet boards or against big raises. A passive player who suddenly raises usually has it.",
    "6. Adjust to opponents" to "On a mixed table: value-bet stations and bluff them less, steal blinds from nits, let maniacs bluff into your strong hands.",
    "7. Review every session" to "Open your biggest losing hands in History. Check equity and the model reference on each street. Start with decisions that show a large deviation.",
    "8. Tournaments: check stack depth and payouts" to "Measure the effective stack in BB. Shoving and folding are common short-stack options. The right play also depends on position, antes, prior actions and payouts.",
)
