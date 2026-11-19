package com.riverrunner.engine

import com.riverrunner.engine.strategy.OpponentPreset
import kotlin.random.Random

data class OpponentNaming(val chinese: Boolean = false, val followsStyle: Boolean = false) {
    internal fun shuffle(random: Random): List<String> = (if (chinese) chineseNames else englishNames).shuffled(random)

    internal fun styledName(neutral: String, preset: OpponentPreset, occurrence: Int): String {
        if (chinese) {
            val variants = chineseStyleNames.getValue(preset)
            return neutral.first() + variants[(occurrence - 1) % variants.size]
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
        val chineseStyleNames = mapOf(
            OpponentPreset.BASELINE to listOf("知衡", "景和", "允中", "修齐", "明达", "仲宁", "思齐", "怀远"),
            OpponentPreset.TAG to listOf("谨锋", "锐行", "立钧", "峻成"),
            OpponentPreset.LAG to listOf("逐风", "凌川", "远驰", "飞扬"),
            OpponentPreset.STATION to listOf("守河", "久安", "笃行", "长宁"),
            OpponentPreset.NIT to listOf("慎言", "静之", "思远", "清谨"),
            OpponentPreset.MANIAC to listOf("乘浪", "鸣雷", "惊涛", "云驰"),
            OpponentPreset.LOOSE_PASSIVE to listOf("随舟", "听澜", "泊川", "望潮"),
        )
    }
}
