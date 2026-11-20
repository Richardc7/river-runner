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
                    val shape = RoundedCornerShape(10.dp)
                    Box(Modifier.fillMaxWidth().clip(shape)
                        .background(if (selected) Brush.verticalGradient(listOf(Palette.FeltCenter, Palette.FeltMid)) else Brush.verticalGradient(listOf(Palette.PanelHi, Palette.Panel)))
                        .border(1.dp, if (selected) Palette.Green.copy(alpha = 0.65f) else Palette.Line.copy(alpha = 0.45f), shape)
                        .selectable(selected, role = Role.Tab) {
                            focus.clearFocus()
                            tab = target
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(tabTitle(target, zh), color = if (selected) Color.White else Palette.TextDim, fontSize = 13.sp, lineHeight = 17.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().studioCard(radius = 16.dp)
                .padding(16.dp).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                GuideTab.BASICS -> Basics(zh)
                GuideTab.RANKINGS -> Rankings(zh)
                GuideTab.POSITIONS -> Positions(zh)
                GuideTab.MATH -> QuickPokerMath(zh)
                GuideTab.GLOSSARY -> Glossary(zh, termQuery) { termQuery = it }
                GuideTab.TIPS -> Paragraphs(if (zh) GUIDE_TIPS_ZH else GUIDE_TIPS_EN)
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

@Composable
private fun Rankings(zh: Boolean) {
    H(if (zh) "从大到小" else "Strongest to weakest")
    for ((i, r) in GUIDE_RANKS.withIndex()) {
        Row(
            Modifier.fillMaxWidth().studioCard(radius = 10.dp).padding(horizontal = 10.dp, vertical = 7.dp),
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
    val matches = if (query.isBlank()) GUIDE_TERMS else GUIDE_TERMS.filter { it.matches(query.trim()) }
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
