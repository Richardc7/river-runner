package com.riverrunner.engine

import com.riverrunner.engine.strategy.OpponentPreset
import kotlin.random.Random

data class OpponentNaming(val chinese: Boolean = false, val followsStyle: Boolean = false) {
    internal fun shuffle(random: Random): List<String> = (if (chinese) chineseNames else englishNames).shuffled(random)

    internal fun styledName(neutral: String, preset: OpponentPreset, occurrence: Int): String {
        if (chinese) {
            val givenName = when (preset) {
                OpponentPreset.BASELINE -> "知衡"
                OpponentPreset.TAG -> "谨锋"
                OpponentPreset.LAG -> "逐风"
                OpponentPreset.STATION -> "守河"
                OpponentPreset.NIT -> "慎言"
                OpponentPreset.MANIAC -> "乘浪"
                OpponentPreset.LOOSE_PASSIVE -> "随舟"
            }
            return neutral.first() + givenName
        }
        val nickname = when (preset) {
            OpponentPreset.BASELINE -> "Anchor"
            OpponentPreset.TAG -> "Lock"
            OpponentPreset.LAG -> "Chase"
            OpponentPreset.STATION -> "River"
            OpponentPreset.NIT -> "Frost"
            OpponentPreset.MANIAC -> "Storm"
            OpponentPreset.LOOSE_PASSIVE -> "Drift"
        }
        return if (occurrence == 1) nickname else "$nickname $occurrence"
    }

    private companion object {
        val englishNames = listOf("Ace", "Blaze", "Cobra", "Dune", "Echo", "Flint", "Ghost", "Hawk",
            "Iris", "Jade", "Koda", "Luna", "Maverick", "Nova", "Onyx", "Pike")
        // Distinct surnames also keep style-linked names unique on a full table.
        val chineseNames = listOf("陈明远", "林思羽", "周启文", "沈安宁", "许清禾", "顾云青", "韩亦辰", "吴星扬",
            "唐子墨", "叶知秋", "罗书言", "何若岚", "梁景行", "宋雨桐", "程怀瑾", "孟晓月")
    }
}
