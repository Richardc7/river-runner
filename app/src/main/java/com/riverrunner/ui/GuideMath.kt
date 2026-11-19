package com.riverrunner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun QuickPokerMath(zh: Boolean) {
    fun t(cn: String, en: String) = if (zh) cn else en
    GuideCard(t("4/2 法则 · 速算听牌命中率", "The rule of 4 and 2")) {
        GuideParagraph(t(
            "先数出张，也就是能补成目标牌型的未知牌。同一张只算一次。若补成后仍可能输给更大的牌，要给这些出张打折。",
            "Count the unseen cards that complete your draw. Count each card once. Discount outs that could still leave you behind.",
        ))
        Formula(t("只看下一张：出张 × 2%", "One card to come: outs × 2%"))
        GuideParagraph(t(
            "翻牌看转牌、转牌看河牌，都用 × 2。即使现在是翻牌圈，如果这次跟注只让你看到转牌，也先按一张算。",
            "Use × 2 from flop to turn or turn to river. If a flop call only buys the turn, start with the one-card estimate.",
        ))
        Formula(t("翻牌看到河牌：出张 × 4%", "Flop to river: outs × 4%"))
        GuideParagraph(t(
            "能看到转牌和河牌两张时用 × 4，例如对手全下、跟注后直接发完。出张越多，近似误差可能越大。",
            "Use × 4 when you will see both cards, such as when a call closes all-in action. The approximation can get worse with many outs.",
        ))
        GuideMathRow(t("听牌示例", "Draw"), t("出张", "Outs"), t("一张 ≈", "One ≈"), t("两张 ≈", "Two ≈"), header = true)
        GuideMathRow(t("卡顺", "Gutshot"), "4", "8%", "16%")
        GuideMathRow(t("两头顺", "Open-ended"), "8", "16%", "32%")
        GuideMathRow(t("同花听牌", "Flush draw"), "9", "18%", "36%")
        GuideParagraph(t(
            "例如，你有 A♥ J♥，翻牌是 8♥ 5♥ 2♣。还有 13 − 4 = 9 张红桃。翻牌到转牌精算是 9/47 ≈ 19.1%，转牌没有命中时，转牌到河牌是 9/46 ≈ 19.6%。翻牌看到河牌至少中一次是 1 − (38/47 × 37/46) ≈ 35.0%。",
            "For example, A♥ J♥ on 8♥ 5♥ 2♣ leaves 13 − 4 = 9 hearts. The exact turn hit rate is 9/47 ≈ 19.1%. After a miss, the river rate is 9/46 ≈ 19.6%. The chance of at least one hit by the river is 1 − (38/47 × 37/46) ≈ 35.0%.",
        ))
        GuideParagraph(t(
            "命中率只告诉你成牌有多常见。摊牌权益还取决于对手范围、你未成牌时能否获胜，以及成牌后是否仍会输或平分。",
            "A hit rate measures how often your draw completes. Showdown equity also depends on the opponent's range, wins without hitting, losses after hitting, and splits.",
        ))
    }
    GuideCard(t("跟注价格 · 底池赔率和所需胜率", "The price of a call")) {
        Formula(t("所需胜率 = 跟注额 ÷（当前底池 + 跟注额）", "Equity needed = call ÷ (current pot + call)"))
        GuideParagraph(t(
            "当前底池已包含对手这次下注。例如，原底池 10 BB，对手下注 5 BB，桌面底池是 15 BB。需跟 5 BB，游戏显示赔率 1 : 3，所需胜率 = 5 ÷ 20 = 25%。",
            "The current pot already includes the opponent's bet. For example, a 5 BB bet into 10 BB makes the current pot 15 BB. You call 5 BB. The app shows 1 : 3, and the equity threshold is 5 ÷ 20 = 25%.",
        ))
        GuideMathRow(t("对手下注", "Bet size"), t("所需胜率", "Equity needed"), t("下注方纯诈唬", "Pure bluff folds"), header = true)
        GuideMathRow(t("1/3 池", "⅓ pot"), "20%", "25%")
        GuideMathRow(t("1/2 池", "½ pot"), "25%", "33.3%")
        GuideMathRow(t("3/4 池", "¾ pot"), "30%", "42.9%")
        GuideMathRow(t("一倍池", "Pot"), "33.3%", "50%")
        GuideParagraph(t(
            "上表的跟注门槛用在这次跟注后直接摊牌、无抽水的简单情景。假设你只在同花命中时赢，9 出张只看一张约 18%，低于半池下注的 25%；全下后能看两张时约 36%，高于门槛。还有后续下注时，要另外考虑未来的投入和回报。",
            "Treat these thresholds as a simple unraked showdown comparison. If you win only when your nine-out flush draw hits, one card gives about 18%, below the 25% half-pot price. An all-in call that buys both cards gives about 36%. With betting left, also consider future costs and payoffs.",
        ))
    }
    GuideCard(t("下注方速算 · 纯诈唬需要多少弃牌", "Betting shortcut: pure-bluff break-even")) {
        Formula(t("所需弃牌率 = 下注额 ÷（下注前底池 + 下注额）", "Folds needed = bet ÷ (pot before betting + bet)"))
        GuideParagraph(t(
            "例如，向 10 BB 底池下注 5 BB。对手弃牌就赢 10 BB，被跟注就输 5 BB，因此至少约三分之一的弃牌率才能打平。这个简式假设单挑、被继续时没有权益、没有后续投入和抽水。",
            "For example, bet 5 BB into 10 BB. A fold wins 10 BB, a call loses 5 BB, so the opponent must fold about one third of the time to break even. This shortcut assumes heads-up play, zero equity when continued against, and no further costs or rake.",
        ))
    }
    GuideCard(t("把输赢合起来算 · 跟注 EV", "Combine outcomes: call EV")) {
        Formula(t("跟注 EV = 权益 × 跟注后底池 − 跟注额", "Call EV = equity × final pot − call"))
        GuideParagraph(t(
            "沿用 15 BB 底池、跟注 5 BB 的例子。如果按对手范围估计有 30% 摊牌权益，跟注 EV 就是 30% × 20 − 5 = +1 BB，以此刻弃牌为 0 比较。这里仍假设跟注后直接摊牌且没有抽水。",
            "With a 15 BB pot and a 5 BB call, suppose you estimate 30% showdown equity against the opponent’s range. The call is worth 30% × 20 − 5 = +1 BB relative to folding now. This still assumes an unraked call straight to showdown.",
        ))
    }
    GuideCard(t("用 BB 比较筹码 · 有效筹码与 SPR", "Compare stack depth: BB and SPR")) {
        Formula(t("筹码 ÷ 大盲 = BB；有效筹码 ÷ 底池 = SPR", "Chips ÷ big blind = BB   •   Effective stack ÷ pot = SPR"))
        GuideParagraph(t(
            "大盲是 200 时，12,000 筹码等于 60 BB。若单挑对手只剩 30 BB，有效筹码就是 30 BB。底池 10 BB 时，双方可争夺的剩余筹码对应 SPR = 3。练习模式的桌面比值以你的剩余筹码计算；对手更短时，用较小的筹码手算。",
            "At 200 per big blind, 12,000 chips is 60 BB. If your heads-up opponent has 30 BB left, the effective stack is 30 BB. A 10 BB pot gives an effective SPR of 3. The live practice readout uses your own remaining stack, so use the smaller stack yourself against a shorter opponent.",
        ))
    }
}

@Composable
private fun Formula(value: String) {
    Text(value, color = Palette.Gold, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().background(Palette.Bg, RoundedCornerShape(8.dp)).padding(8.dp))
}

@Composable
private fun GuideMathRow(vararg cells: String, header: Boolean = false) {
    Row(Modifier.fillMaxWidth()
        .background(if (header) Palette.Bg else Palette.Panel, RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.forEachIndexed { index, cell ->
            Text(cell, modifier = Modifier.weight(if (index == 0) 1.35f else 1f),
                color = if (header) Palette.TextDim else Palette.Text,
                fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
