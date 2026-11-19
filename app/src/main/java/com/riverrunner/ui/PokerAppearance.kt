package com.riverrunner.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.riverrunner.R

enum class TableStyle(val code: String, @DrawableRes val art: Int?, val trim: Color) {
    CLASSIC("classic", null, Color(0xFF907246)),
    CYBERPUNK("cyberpunk", R.drawable.table_cyberpunk, Color(0xFF62D9F2)),
    WESTERN("western", R.drawable.table_western, Color(0xFFD89658)),
    TAROT("tarot", R.drawable.table_tarot, Color(0xFFE1BC6B));

    val controlAccent: Color get() = if (this == CLASSIC) Palette.Gold else trim

    fun title(zh: Boolean) = when (this) {
        CLASSIC -> if (zh) "经典" else "Classic"
        CYBERPUNK -> if (zh) "赛博朋克" else "Cyberpunk"
        WESTERN -> if (zh) "美式西部" else "Western"
        TAROT -> if (zh) "塔罗" else "Tarot"
    }

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: CLASSIC
    }
}

enum class CardBackStyle(val art: TableStyle, @DrawableRes val image: Int?) {
    CLASSIC(TableStyle.CLASSIC, null),
    CYBERPUNK(TableStyle.CYBERPUNK, R.drawable.back_cyberpunk),
    WESTERN(TableStyle.WESTERN, R.drawable.back_western),
    TAROT(TableStyle.TAROT, R.drawable.back_tarot);

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.art.code == code } ?: CLASSIC
    }
}

data class PokerAppearance(
    val face: DeckStyle = DeckStyle.FOUR_COLOR,
    val back: CardBackStyle = CardBackStyle.CLASSIC,
    val table: TableStyle = TableStyle.CLASSIC,
) {
    companion object {
        val presets = listOf(
            PokerAppearance(),
            PokerAppearance(DeckStyle.CYBERPUNK, CardBackStyle.CYBERPUNK, TableStyle.CYBERPUNK),
            PokerAppearance(DeckStyle.WESTERN, CardBackStyle.WESTERN, TableStyle.WESTERN),
            PokerAppearance(DeckStyle.TAROT, CardBackStyle.TAROT, TableStyle.TAROT),
        )
    }
}

val LocalCardBackStyle = staticCompositionLocalOf { CardBackStyle.CLASSIC }
val LocalTableStyle = staticCompositionLocalOf { TableStyle.CLASSIC }
