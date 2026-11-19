package com.riverrunner.ui

import com.riverrunner.engine.Action
import com.riverrunner.engine.ActionType
import com.riverrunner.engine.BotStyle
import com.riverrunner.engine.Move
import com.riverrunner.engine.MoveType
import com.riverrunner.engine.Street
import com.riverrunner.engine.strategy.ActionValue
import com.riverrunner.engine.strategy.TrainingDrill
import java.util.Locale

/** One-language-only captions. Technical card ranks, position abbreviations and BB remain conventional. */
object StudyPresentation {
    fun moveType(type: MoveType, zh: Boolean, openingBet: Boolean = false): String = when (type) {
        MoveType.FOLD -> if (zh) "弃牌" else "Fold"
        MoveType.CHECK -> if (zh) "过牌" else "Check"
        MoveType.CALL -> if (zh) "跟注" else "Call"
        MoveType.RAISE -> actionType(if (openingBet) ActionType.BET else ActionType.RAISE, zh)
    }

    fun actionType(type: ActionType, zh: Boolean): String = when (type) {
        ActionType.FOLD -> if (zh) "弃牌" else "Fold"
        ActionType.CHECK -> if (zh) "过牌" else "Check"
        ActionType.CALL -> if (zh) "跟注" else "Call"
        ActionType.BET -> if (zh) "下注" else "Bet"
        ActionType.RAISE -> if (zh) "加注" else "Raise"
        ActionType.ANTE -> if (zh) "前注" else "Ante"
        ActionType.SMALL_BLIND -> if (zh) "小盲" else "Small blind"
        ActionType.BIG_BLIND -> if (zh) "大盲" else "Big blind"
        ActionType.STRADDLE -> if (zh) "强制再盲注" else "Straddle"
    }

    fun street(street: Street, zh: Boolean): String = when (street) {
        Street.PREFLOP -> if (zh) "翻牌前" else "Preflop"
        Street.FLOP -> if (zh) "翻牌圈" else "Flop"
        Street.TURN -> if (zh) "转牌圈" else "Turn"
        Street.RIVER -> if (zh) "河牌圈" else "River"
    }

    fun move(move: Move, bb: Int, zh: Boolean, openingBet: Boolean = false): String =
        moveType(move.type, zh, openingBet) + if (move.type == MoveType.RAISE) {
            " " + "%.1f".format(Locale.US, move.raiseTo.toDouble() / bb.coerceAtLeast(1)) + " BB"
        } else ""

    /** A short bounded set of REAL saved alternatives, never invents hypothetical EVs. */
    fun choices(drill: TrainingDrill): List<ActionValue> {
        val ordered = drill.options.sortedWith(compareBy<ActionValue> { it.move.type.ordinal }.thenBy { it.move.raiseTo })
        if (ordered.size <= 6) return ordered
        val picked = linkedSetOf(drill.best.move)
        fun include(option: ActionValue?) {
            if (picked.size < 6 && option != null) picked.add(option.move)
        }
        for (type in listOf(MoveType.FOLD, MoveType.CHECK, MoveType.CALL)) {
            include(ordered.firstOrNull { it.move.type == type })
        }
        val raises = ordered.filter { it.move.type == MoveType.RAISE }
        if (raises.isNotEmpty()) {
            include(raises.first())
            include(raises.last())
            include(raises[raises.size / 2])
        }
        for (option in ordered.sortedByDescending { it.chips }) include(option)
        return ordered.filter { it.move in picked }
    }

    fun topic(topic: String, zh: Boolean): String {
        if (!zh) return topic
        return when (topic) {
            "Preflop facing raise" -> "翻牌前面对加注"
            "Preflop opening" -> "翻牌前率先入池"
            "FLOP facing bet" -> "翻牌圈面对下注"
            "FLOP initiative" -> "翻牌圈主动行动"
            "TURN facing bet" -> "转牌圈面对下注"
            "TURN initiative" -> "转牌圈主动行动"
            "RIVER facing bet" -> "河牌圈面对下注"
            "RIVER initiative" -> "河牌圈主动行动"
            "river-model" -> "河牌模型"
            "turn-model" -> "转牌模型"
            "flop-cbet-dry" -> "翻牌干燥面 C-Bet"
            "flop-cbet-wet" -> "翻牌湿润面 C-Bet"
            else -> if (topic.startsWith("exploit-")) "剥削策略情景" else "决策训练情景"
        }
    }

    fun category(key: String, zh: Boolean): String {
        if (!zh) return key
        val value = key.substringAfter(':', key)
        return when (key.substringBefore(':')) {
            "topic" -> "情景：" + topic(value, true)
            "position" -> "位置：" + value
            "depth" -> "有效筹码：" + when (value) { "under20bb" -> "少于20BB"; else -> value }
            "size" -> "下注尺度：" + when(value) {
                "small" -> "小"; "medium" -> "中"; "large" -> "大"; "no-bet" -> "未下注"; else -> value
            }
            "odds" -> "跟注所需胜率：" + when (value) { "no-call" -> "无需跟注"; else -> value }
            else -> topic(key, true)
        }
    }

    /** Translate known persisted engine explanations; never silently emit English in Chinese mode. */
    fun modelNote(note: String, zh: Boolean): String {
        if (!zh) return note
            .replace("not knowledge of the opponent's cards or verified GTO", "not knowledge of the opponent's cards")
            .replace("; not solver GTO", "")
            .replace(", NOT solver GTO, verified EV, or historical profit", "")
        val normalized = note.lowercase(Locale.ROOT)
        return when {
            note.startsWith("Top model range classes: ") ->
                "模型估计的主要范围牌型：" + note.substringAfter(": ")
            note.contains("exact enumeration conditional on") -> {
                val count = Regex(""";\s*(\d+)\s+unblocked combinations""").find(note)?.groupValues?.get(1)
                "河牌模型按假设的对手范围枚举" + (count?.let { " $it 种有效手牌组合" } ?: "有效组合") + "；不代表已知对手底牌。"
            }
            note.contains("turn runout enumeration conditional on") -> {
                "转牌模型按主要加权组合枚举河牌发出后的权益与弃牌率；不代表已知对手底牌。"
            }
            note.contains("Board texture: dry") -> "翻牌质地：干燥面（C-Bet 弃牌率通常偏高）。"
            note.contains("Board texture: wet") -> "翻牌质地：湿润面（听牌较多，C-Bet 弃牌率相对偏低）。"
            normalized.contains("turn ev is coaching-only") ->
                "转牌 EV 仅用于训练解释，按单街延续估算，不包含完整河牌树。"
            normalized.contains("flop c-bet ev is coaching-only") ->
                "翻牌 C-Bet EV 仅用于训练解释，综合估算弃牌权益与摊牌权益。"
            normalized.contains("cash heads-up postflop only") ->
                "当前剥削模型仅支持单挑现金局的翻后情景；其他情况下保留基准策略。"
            normalized.contains("top model range") ->
                "展示的是模型估计的范围，而非对手真实手牌。"
            normalized.contains("range is a modelling assumption") ->
                "对手范围由模型根据当时信息估计。"
            normalized.contains("re-raise") && normalized.contains("terminal") ->
                "下注或加注 EV 假设面对再加注时弃牌；只计算直接结束的过牌或跟注分支。"
            normalized.contains("bounds vary response parameters") ->
                "EV 上下界只反映对手响应参数变化，未计入范围模型误差，不是统计置信区间。"
            normalized.contains("coaching-only") ->
                "河牌 EV 仅用于训练解释，不会直接替换游戏中的受限策略。"
            normalized.contains("out-of-position check") ->
                "位置不利时过牌仍可能面对下一次决策，本节点不估算其终局 EV。"
            normalized.contains("raked") || normalized.contains("rake") ->
                "本情景存在抽水；尚无足够的抽水感知 EV 模型，不显示不可靠的数值。"
            normalized.contains("side pot") || normalized.contains("side-pot") ->
                "该情景涉及暂不支持的边池，无法可靠计算行动 EV。"
            normalized.contains("unblocked combination") ->
                "排除已知公共牌及自己的手牌后，模型没有剩余的有效对手手牌组合。"
            normalized.contains("range") && normalized.contains("strength") ->
                "根据假设的对手范围和响应，比较行动 EV 的变化。"
            normalized.contains("stack cap") ->
                "筹码调整仅改变当前节点可投入的上限，不重新模拟之前的下注历史。"
            normalized.contains("bet") && normalized.contains("supported") ->
                "此局面不符合目前下注模型支持的条件，数值结果已隐藏。"
            normalized.contains("short-stack push-width") ->
                "短筹码全下范围按启发式规则估计，解读时还需考虑对手范围与 ICM。"
            normalized.contains("rfi heuristic") ->
                "模型按尚未行动的人数调整率先入池范围。"
            normalized.contains("facing a raise:") ->
                "面对加注时通常需要收紧继续范围；此图只是范围宽度示意，并非完整求解策略。"
            else -> "此条历史模型说明暂无可靠的中文对应描述，已隐藏英文原文。"
        }
    }

    /** This is an EXPLANATION HYPOTHESIS, not a re-run of the bot's private decision RNG. */
    fun opponentReason(action: Action, style: BotStyle, zh: Boolean): String {
        val move = when (action.type) {
            ActionType.FOLD -> if (zh) "这次选择弃牌，可能因为继续投入的成本较高，或模型判断当前范围不宜继续。" else
                "This fold may reflect a costly continuation or a range that the model prefers to release."
            ActionType.CHECK -> if (zh) "这次过牌可能是控池、等待后续行动或保留弱牌与强牌的混合范围；过牌不必然代表弱牌。" else
                "Checking can manage the pot, keep a mixed range or wait for action; it does not prove weakness."
            ActionType.CALL -> if (zh) "这次跟注表示模型愿意继续投入，也可能包含成手牌、听牌或宽松跟注；单凭跟注不能确定牌力。" else
                "Calling may involve made hands, draws or a loose continue range; the action alone does not identify a hand."
            ActionType.BET, ActionType.RAISE -> if (zh)
                "这次主动下注或加注会给其他玩家制造压力；可能是价值下注，也可能是诈唬或半诈唬。" else
                "Aggression applies pressure and may represent value, a bluff or a semi-bluff."
            else -> if (zh) "这是牌局规则要求的盲注或前注，不属于自愿策略选择。" else
                "This forced blind or ante is not a voluntary strategic decision."
        }
        val styleHint = when (style) {
            BotStyle.GTO -> ""
            BotStyle.TAG -> if (zh) "紧凶预设偏向较窄入池范围与主动价值下注。" else
                "The tight-aggressive preset tends towards narrower entries and aggressive value play."
            BotStyle.LAG -> if (zh) "松凶预设入池更宽，也倾向更频繁主动施压。" else
                "The loose-aggressive preset enters wider ranges and applies pressure more often."
            BotStyle.STATION -> if (zh) "跟注站预设更愿意跟注、较少主动诈唬。" else
                "The calling-station preset calls more freely and bluffs less."
            BotStyle.NIT -> if (zh) "超紧预设更重视强牌范围，常放弃边缘牌。" else
                "The very-tight preset leans toward strong hands and drops marginal holdings."
            BotStyle.MANIAC -> if (zh) "激进预设会更频繁下注或加注，不能直接把攻击解释为强牌。" else
                "The aggressive preset bets and raises frequently; an aggressive action need not mean strength."
        }
        return listOf(move, styleHint).filter(String::isNotBlank).joinToString(" ")
    }
}
