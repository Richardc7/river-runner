package com.riverrunner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards
import com.riverrunner.i18n.LocalStrings

private val previewCards = listOf("As", "Kh", "Qd", "Jc").map(Cards::parse)

@Composable
fun AppearanceScreen(vm: AppViewModel) {
    val st = LocalStrings.current
    fun t(zh: String, en: String) = if (st.isZh) zh else en
    val appearance = vm.appearance
    val back = { vm.screen = Screen.Settings }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("← " + st.back, back)
            Text(t("牌桌与扑克牌外观", "Table and card appearance"),
                fontSize = 20.sp, color = Palette.Text, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Palette.Panel)
                .padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("实时预览", "Preview"), color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 80.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Palette.Bg), contentAlignment = Alignment.Center) {
                    TableSurface(appearance.table, Modifier.fillMaxSize(), centerY = .5f)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (card in previewCards) PlayingCard(card, 33.dp)
                        CardBack(33.dp)
                    }
                }
                ConfigurationChoices(t("整套风格", "Presets"), PokerAppearance.presets, appearance,
                    { it.table.title(st.isZh) }, vm::updateAppearance)
            }
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Palette.Panel)
                .verticalScroll(rememberScrollState()).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ConfigurationChoices(t("牌桌", "Table"), TableStyle.entries, appearance.table, { it.title(st.isZh) }) {
                    vm.updateAppearance(appearance.copy(table = it))
                }
                ConfigurationChoices(t("牌背", "Card back"), CardBackStyle.entries, appearance.back, { it.art.title(st.isZh) }) {
                    vm.updateAppearance(appearance.copy(back = it))
                }
                ConfigurationChoices(t("牌面", "Card face"), DeckStyle.entries, appearance.face, {
                    when (it) {
                        DeckStyle.FOUR_COLOR -> st.deckFour
                        DeckStyle.FOUR_COLOR_FILLED -> st.deckFilled
                        DeckStyle.CLASSIC -> st.deckClassic
                        DeckStyle.CYBERPUNK -> TableStyle.CYBERPUNK.title(st.isZh)
                        DeckStyle.WESTERN -> TableStyle.WESTERN.title(st.isZh)
                        DeckStyle.TAROT -> TableStyle.TAROT.title(st.isZh)
                    }
                }) { vm.updateAppearance(appearance.copy(face = it)) }
            }
        }
    }
}
