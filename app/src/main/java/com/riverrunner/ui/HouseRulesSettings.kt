package com.riverrunner.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.strategy.EditionConfig
import java.util.Locale

@Composable
fun HouseRulesSettings(config: EditionConfig, chinese: Boolean, onChange: (EditionConfig) -> Unit) {
    fun t(zh: String, en: String) = if (chinese) zh else en
    val rules = config.cashRules
    Text(t("这些设置适用于筹码局。", "These rules apply to cash games."), color = Palette.TextDim, fontSize = 12.sp)
    SettingsSwitch(
        t("枪口位强制加盲", "Live UTG straddle"),
        t("三人及以上、筹码足够时加盲至 2 BB。", "Posts 2 BB with at least three players and enough chips."),
        rules.utgStraddle,
    ) { onChange(config.copy(cashRules = rules.copy(utgStraddle = it))) }

    val standardRates = listOf(0, 250, 500, 1000)
    val rates = if (rules.rakeBasisPoints in standardRates) standardRates else (standardRates + rules.rakeBasisPoints).sorted()
    ConfigurationChoices(t("抽水比例", "Rake rate"), rates, rules.rakeBasisPoints,
        { String.format(Locale.US, "%.2f", it / 100.0).trimEnd('0').trimEnd('.') + "%" }) { rate ->
        val cap = if (rate == 0) 0 else rules.rakeCapBB.takeIf { it > 0 } ?: 3
        onChange(config.copy(cashRules = rules.copy(rakeBasisPoints = rate, rakeCapBB = cap)))
    }
    if (rules.hasRake) {
        val standardCaps = listOf(1, 2, 3, 5, 10)
        val caps = if (rules.rakeCapBB in standardCaps) standardCaps else (standardCaps + rules.rakeCapBB).sorted()
        ConfigurationChoices(t("每手抽水上限", "Rake cap per hand"), caps, rules.rakeCapBB, { "$it BB" }) {
            onChange(config.copy(cashRules = rules.copy(rakeCapBB = it)))
        }
        SettingsSwitch(t("未见翻牌不抽水", "No flop, no drop"),
            t("牌局在翻牌前结束时免抽水。", "Waive rake for hands that end before the flop."),
            rules.noFlopNoDrop) { onChange(config.copy(cashRules = rules.copy(noFlopNoDrop = it))) }
        Text(t("底池赔率按抽水前的底池计算。", "Pot odds use the pot before rake."),
            color = Palette.TextDim, fontSize = 11.sp)
    }

    val standardRebuys = listOf(0, 1, 2, 5)
    val rebuys = if (config.automaticRebuys in standardRebuys) standardRebuys
        else (standardRebuys + config.automaticRebuys).sorted()
    ConfigurationChoices(t("每个座位自动重买次数", "Automatic rebuys per seat"),
        rebuys, config.automaticRebuys, Int::toString) { onChange(config.copy(automaticRebuys = it)) }
}
