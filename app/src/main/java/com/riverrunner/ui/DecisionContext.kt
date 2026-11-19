package com.riverrunner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Action
import com.riverrunner.engine.ActionType
import com.riverrunner.engine.Cards
import com.riverrunner.engine.GameMode
import com.riverrunner.engine.HandEngine
import com.riverrunner.engine.Move
import com.riverrunner.engine.Street
import com.riverrunner.engine.strategy.BetSizing
import com.riverrunner.engine.strategy.DecisionView
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.i18n.Strings
import java.util.Locale

/** All figures describe the actor just before the decision. */
internal class DecisionContext(private val view: DecisionView) {
    private val legal = view.legal
    private val actor = view.actor
    private val opponents = view.opponents
    private val able = opponents.filterNot { it.allIn }
    private val streetActions = view.actions.filter { it.street == view.street && !it.type.isPost }
    private val acted = streetActions.map { it.seat }.toSet()

    val position = positionFor(view.actorSeat)
    val effectiveStacks = able.map { minOf(actor.stack, it.stack) }
    val call = legal.toCall
    val eligiblePot = view.seats.sumOf { minOf(it.committed, actor.committed + call) }
    val equityNeeded = if (call > 0) call.toDouble() / (eligiblePot + call) else null
    val potAfterCall = eligiblePot + call
    val othersToAct = able.count { it.seat !in acted || it.streetBet < legal.currentBet }
    val behindAfterCall = minOf((actor.stack - call).coerceAtLeast(0),
        able.maxOfOrNull { (it.stack - (legal.currentBet - it.streetBet).coerceAtLeast(0)).coerceAtLeast(0) } ?: 0)
    private val lastAggressor = streetActions.lastOrNull { it.type.isAggressive }
    private val playerAfter = able.count { view.order.indexOf(it.seat) > view.order.indexOf(view.actorSeat) }

    private fun positionFor(seat: Int): String =
        HandEngine.positionName(view.order.indexOf(seat), view.order.size)

    fun lines(st: Strings, actual: Action?, modelEquity: Double? = null): List<Pair<String, String>> {
        fun t(zh: String, en: String) = if (st.isZh) zh else en
        fun bb(chips: Int) = String.format(Locale.US, "%.2f", chips.toDouble() / view.bigBlind)
            .trimEnd('0').trimEnd('.') + " BB"
        fun percent(value: Double) = Format.pct(value)
        val preflop = view.street == Street.PREFLOP
        val passiveAction = if (call > 0) t("跟注", "A call") else t("过牌", "A check")
        val positionNote = when {
            able.isEmpty() -> t("其余对手已全下", "All remaining opponents are all-in")
            playerAfter == 0 -> if (preflop) t("翻后有利位置", "In position postflop")
                else t("有利位置", "In position")
            playerAfter == able.size -> if (preflop) t("翻后不利位置", "Out of position postflop")
                else t("不利位置", "Out of position")
            else -> t("翻后夹在对手之间（后方 $playerAfter 人）", "Between opponents postflop ($playerAfter behind)")
        }
        val min = effectiveStacks.minOrNull()
        val max = effectiveStacks.maxOrNull()
        val depth = when {
            min == null || max == null -> t("无可继续下注的对手", "No opponent left who can bet")
            min == max -> bb(max)
            else -> "${bb(min)}–${bb(max)}"
        }
        val ratio = max?.takeIf { view.pot > 0 }?.let { String.format(Locale.US, "%.1f", it.toDouble() / view.pot) }
        val street = StudyPresentation.street(view.street, st.isZh)
        val hand = TableMapper.handText(st, view.ownHole.toIntArray(), view.board)
        return buildList {
            add(t("位置", "Position") to "$position · $street · $positionNote")
            add(t("筹码", "Stacks") to t("本手起始 ${bb(actor.startStack)} · 行动前剩 ${bb(actor.stack)} · 有效后手 $depth",
                "Started with ${bb(actor.startStack)} · ${bb(actor.stack)} behind · Effective behind $depth"))
            add(t("底池与对手", "Pot & players") to t("行动前底池 ${bb(view.pot)} · ${opponents.size} 名对手",
                "Pot before acting ${bb(view.pot)} · ${opponents.size} opponent(s)") +
                if (ratio == null) "" else if (effectiveStacks.distinct().size > 1) " · " + t("最深 SPR $ratio", "Deepest SPR $ratio") else " · SPR $ratio")
            val boardNote = if (preflop) emptyList() else buildList {
                if (view.board.groupingBy(Cards::rank).eachCount().values.any { it >= 2 })
                    add(t("公共牌成对，要考虑三条或葫芦", "Paired board: consider trips and full houses"))
                val suited = view.board.groupingBy(Cards::suit).eachCount().values.max()
                if (suited >= 3) add(t("公共牌有 $suited 张同花色，要考虑同花", "$suited cards of one suit: consider flushes"))
                else if (suited == 2 && view.street != Street.RIVER)
                    add(t("牌面有同花听牌可能", "Flush draws are possible"))
            }
            add(t("手牌", "Hand") to (listOf(hand) + boardNote).joinToString(" · "))
            if (call > 0 && equityNeeded != null) {
                val sizing = lastAggressor?.let {
                    val lastPosition = positionFor(it.seat)
                    val verb = if (it.type == ActionType.BET) t("下注", "bet") else t("加注到", "raised to")
                    "$lastPosition $verb ${bb(it.streetTotal)} · "
                }.orEmpty()
                val odds = String.format(Locale.US, "%.1f", eligiblePot.toDouble() / call)
                val contested = if (eligiblePot < view.pot) t(" · 可争底池 ${bb(eligiblePot)}", " · Eligible pot ${bb(eligiblePot)}") else ""
                add(t("跟注价格", "Price to call") to sizing +
                    t("补 ${bb(call)} · 赔率 1 : $odds · 门槛 ${percent(equityNeeded)}",
                        "Call ${bb(call)} · Odds 1 : $odds · ${percent(equityNeeded)} break-even equity") +
                    contested + if (view.cashRules.hasRake) t("（抽水前）", " before rake") else "")
            }
            if (modelEquity != null) {
                val value = percent(modelEquity)
                val estimate = if (equityNeeded != null && opponents.size == 1 && eligiblePot == view.pot) {
                    val margin = (modelEquity - equityNeeded) * 100
                    val points = String.format(Locale.US, "%.1f", kotlin.math.abs(margin))
                    val relation = when {
                        kotlin.math.abs(margin) < .05 -> t("接近当前门槛", "near the current threshold")
                        margin > 0 -> t("高出门槛 $points 个百分点", "$points points above the threshold")
                        else -> t("低于门槛 $points 个百分点", "$points points below the threshold")
                    }
                    t("对模型范围约为 $value，$relation。", "About $value against the modelled range, $relation.")
                } else t("对模型范围约为 $value。", "About $value against the modelled range.")
                add(t("模型权益", "Model equity") to estimate)
            }
            add(t("位置影响", "Position in this spot") to when {
                able.isEmpty() -> t("对手没有后手；继续投入以后只剩发牌和结算。", "The remaining opponents have no chips behind. Continuing leads to the runout and settlement.")
                preflop && lastAggressor == null && othersToAct > 0 ->
                    t("翻前还没有加注。${passiveAction}后仍有 $othersToAct 人待行动，后面有人加注会提高继续价格。",
                        "There has been no preflop raise. $passiveAction leaves $othersToAct still to act. A raise would increase the price.")
                preflop && othersToAct > 0 ->
                    t("若只跟，仍有 $othersToAct 人待行动。要考虑身后跟入形成多人底池，或有人再加注。",
                        "If you only call, $othersToAct still have to act. A player behind can make the pot multiway or reraise.")
                preflop -> if (playerAfter > 0) t("${passiveAction}会结束翻前。翻后仍要先于有位置的对手行动。",
                    "$passiveAction closes preflop. The acting seat remains out of position after the flop.")
                else t("${passiveAction}会结束翻前。翻后可先看仍有筹码的对手怎么打。",
                    "$passiveAction closes preflop. After the flop this seat sees what opponents with chips do first.")
                playerAfter > 0 && call > 0 && othersToAct > 0 ->
                    t("行动者身后有 $playerAfter 名有后手的对手，这次只跟仍有 $othersToAct 人要行动。有人加注会改变继续价格。",
                        "$playerAfter opponent(s) with chips act after this seat in positional order, and $othersToAct still have to act after a call. A raise would change the price.")
                playerAfter > 0 && call > 0 -> t("当前跟注会结束这一轮；若还有下一街，有位置的对手仍能在你之后行动。",
                    "Calling closes this round. If another street follows, the opponent in position can still act after you.")
                playerAfter > 0 -> t("有 $playerAfter 名有后手的对手位置在该座位之后。过牌可能给对手先下注的机会。",
                    "$playerAfter opponent(s) with chips have position on this seat. Checking can hand them the first chance to bet.")
                else -> t("该座位最后行动。无人下注时过牌可直接看下一张或摊牌；面对下注时可在已知对手动作后权衡价格。",
                    "This seat acts last. A check closes an unbet round; when facing a bet, you can weigh the price after seeing the other actions.")
            })
            if (call > 0) {
                add(t("跟注后的筹码", "After calling") to when {
                    behindAfterCall == 0 && othersToAct == 0 -> t(
                        "跟注后没有可继续争夺的后手。可用 ${percent(requireNotNull(equityNeeded))} 作为直接摊牌的筹码门槛，另考虑抽水或奖金结构。",
                        "After calling there are no contestable chips behind. Use ${percent(requireNotNull(equityNeeded))} as the chip break-even point, with rake or payouts considered separately.")
                    behindAfterCall == 0 -> t("跟注将用尽可对抗的后手，其他玩家仍可能行动。多人全下时还要区分主池与边池。",
                        "This call uses the remaining contestable stack, with other players still to act. Multiway all-ins can create separate pots.")
                    view.street == Street.RIVER -> t("跟注后最多还剩 ${bb(behindAfterCall)} 可与对手对抗。河牌没有新牌，重点比较摊牌权益和当下价格。",
                        "Up to ${bb(behindAfterCall)} remains contestable after calling. The river brings no new cards, so compare showdown equity with the price.")
                    else -> t("在无人追加筹码的情况下，跟注后底池约 ${bb(potAfterCall)}，最多仍有 ${bb(behindAfterCall)} 可继续争夺。后续下注会影响能实现多少摊牌权益。",
                        "Without further contributions, a call makes the pot ${bb(potAfterCall)} with up to ${bb(behindAfterCall)} still contestable. Later bets affect how much equity you realise.")
                })
            } else if (!preflop && legal.canRaise && ratio != null) {
                val sizing = BetSizing.target(legal, .5).raiseTo - legal.myStreetBet
                val contestable = minOf(sizing, max)
                if (contestable > 0) add(t("筹码压力", "Stack pressure") to
                    t("若下注 ${bb(sizing)}，其中最多 ${bb(contestable)} 可被跟注，占最深有效后手的 ${percent(contestable.toDouble() / max)}。",
                        "A bet of ${bb(sizing)} puts up to ${bb(contestable)} at risk of being matched, ${percent(contestable.toDouble() / max)} of the deepest effective stack."))
            }
            if (preflop && max != null && max < 20L * view.bigBlind) {
                val note = if (view.mode == GameMode.TOURNAMENT)
                    t("锦标赛还要看奖金与出局代价。", "Tournament payouts and the cost of elimination also matter.")
                else t("翻后能追求的额外回报有限。", "Less can be won in later rounds.")
                add(t("短筹码", "Short stacks") to t(
                    "可对抗的最深后手为 ${bb(max)}。翻前加注会占用更大比例的筹码。",
                    "The deepest effective stack is ${bb(max)}. A preflop raise can commit a large share of it.") + " " + note)
            }
            if (actual != null && actual.type.isAggressive && view.pot > 0) {
                val fraction = BetSizing.fraction(legal, Move.raiseTo(actual.streetTotal))
                val description = if (actual.type == ActionType.BET) {
                    t("投入 ${bb(actual.amount)}，相当于行动前底池的 ${percent(fraction)}。",
                        "Invested ${bb(actual.amount)}, ${percent(fraction)} of the pot before acting.")
                } else t("加到 ${bb(actual.streetTotal)}，实际投入 ${bb(actual.amount)}。超过跟注额的部分约是跟注后底池的 ${percent(fraction)}。",
                    "Raised to ${bb(actual.streetTotal)} and put in ${bb(actual.amount)}. The raise increment is ${percent(fraction)} of the pot after a call.")
                add(t("实际尺度", "Actual size") to description)
            }
        }
    }
}

@Composable
internal fun DecisionContextDetails(view: DecisionView, actual: Action? = null, modelEquity: Double? = null) {
    val st = LocalStrings.current
    val details = remember(view, actual, st, modelEquity) { DecisionContext(view).lines(st, actual, modelEquity) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        for ((label, text) in details) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(label, color = Palette.Gold, fontSize = 11.sp, lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = if (st.isZh) 60.dp else 105.dp))
                Text(text, color = Palette.Text, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
            }
        }
    }
}
