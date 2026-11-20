package com.riverrunner.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.riverrunner.R

/** Global visual theme controlling surfaces, accents, felt tones, and matching card/table bundle. */
enum class AppTheme(
    val code: String,
    val bg: Color,
    val panel: Color,
    val panelHi: Color,
    val panelHi2: Color,
    val line: Color,
    val feltCenter: Color,
    val feltMid: Color,
    val feltEdge: Color,
    val rail: Color,
    val railHi: Color,
    val gold: Color,
    val goldDim: Color,
    val text: Color,
    val textDim: Color,
    val red: Color,
    val green: Color,
    val blue: Color,
    val ink: Color,
    val badgeAccent: Color,
) {
    EMERALD(
        code = "emerald",
        bg = Color(0xFF0B0F14),
        panel = Color(0xFF121820),
        panelHi = Color(0xFF1A222C),
        panelHi2 = Color(0xFF26313F),
        line = Color(0xFF2D3B49),
        feltCenter = Color(0xFF1F6E49),
        feltMid = Color(0xFF134E34),
        feltEdge = Color(0xFF0A2C1D),
        rail = Color(0xFF221A14),
        railHi = Color(0xFF4B3829),
        gold = Color(0xFFE5BC66),
        goldDim = Color(0xFF665126),
        text = Color(0xFFF0F4F8),
        textDim = Color(0xFF93A3B3),
        red = Color(0xFFEC6B64),
        green = Color(0xFF42C78A),
        blue = Color(0xFF60A5FA),
        ink = Color(0xFF0B0F14),
        badgeAccent = Color(0xFF42C78A),
    ),
    CHINESE(
        code = "chinese",
        bg = Color(0xFF0D0F12),
        panel = Color(0xFF15191D),
        panelHi = Color(0xFF1E2429),
        panelHi2 = Color(0xFF2C343B),
        line = Color(0xFF473E2E),
        feltCenter = Color(0xFF1F5B4F),
        feltMid = Color(0xFF144038),
        feltEdge = Color(0xFF0B2420),
        rail = Color(0xFF2B1714),
        railHi = Color(0xFF5A2B24),
        gold = Color(0xFFD9B464),
        goldDim = Color(0xFF664F24),
        text = Color(0xFFF5EFE2),
        textDim = Color(0xFFA9A295),
        red = Color(0xFFD95344),
        green = Color(0xFF4FB89A),
        blue = Color(0xFF69A8D8),
        ink = Color(0xFF120E0B),
        badgeAccent = Color(0xFFC94A38),
    ),
    WESTERN(
        code = "western",
        bg = Color(0xFF17100B),
        panel = Color(0xFF231811),
        panelHi = Color(0xFF302118),
        panelHi2 = Color(0xFF422E21),
        line = Color(0xFF5D4330),
        feltCenter = Color(0xFF3B5A2E),
        feltMid = Color(0xFF28401F),
        feltEdge = Color(0xFF162511),
        rail = Color(0xFF331F14),
        railHi = Color(0xFF6B432B),
        gold = Color(0xFFDCA252),
        goldDim = Color(0xFF6B491E),
        text = Color(0xFFF6EAD7),
        textDim = Color(0xFFB59F85),
        red = Color(0xFFE06253),
        green = Color(0xFF72C26F),
        blue = Color(0xFF7BAFD4),
        ink = Color(0xFF191009),
        badgeAccent = Color(0xFFDCA252),
    ),
    MODERN(
        code = "modern",
        bg = Color(0xFF0A0D14),
        panel = Color(0xFF121722),
        panelHi = Color(0xFF1B2333),
        panelHi2 = Color(0xFF28344B),
        line = Color(0xFF334361),
        feltCenter = Color(0xFF1D4E89),
        feltMid = Color(0xFF143662),
        feltEdge = Color(0xFF0B1F3B),
        rail = Color(0xFF1B2230),
        railHi = Color(0xFF3A4963),
        gold = Color(0xFF38BDF8),
        goldDim = Color(0xFF1D5273),
        text = Color(0xFFF3F6FB),
        textDim = Color(0xFF94A3B8),
        red = Color(0xFFFB7185),
        green = Color(0xFF4ADE80),
        blue = Color(0xFF60A5FA),
        ink = Color(0xFF07111F),
        badgeAccent = Color(0xFF38BDF8),
    ),
    KYOTO(
        code = "kyoto",
        bg = Color(0xFF0B111C),
        panel = Color(0xFF121B2B),
        panelHi = Color(0xFF1A273D),
        panelHi2 = Color(0xFF263754),
        line = Color(0xFF394B69),
        feltCenter = Color(0xFF1E3F66),
        feltMid = Color(0xFF142B47),
        feltEdge = Color(0xFF0A1829),
        rail = Color(0xFF171315),
        railHi = Color(0xFF3E3230),
        gold = Color(0xFFD8B468),
        goldDim = Color(0xFF634E24),
        text = Color(0xFFF4F0E6),
        textDim = Color(0xFF9AA7BA),
        red = Color(0xFFE05A4F),
        green = Color(0xFF4EC2A5),
        blue = Color(0xFF6BA6E8),
        ink = Color(0xFF0D1118),
        badgeAccent = Color(0xFFC6422C),
    );

    fun title(zh: Boolean): String = when (this) {
        EMERALD -> if (zh) "暗夜翡翠" else "Emerald Studio"
        CHINESE -> if (zh) "中国风 · 墨玉" else "Chinese Ink"
        WESTERN -> if (zh) "美式西部" else "Western Saloon"
        MODERN -> if (zh) "现代钛蓝" else "Modern Cobalt"
        KYOTO -> if (zh) "日式 · 京都靛蓝" else "Kyoto Indigo"
    }

    val defaultAppearance: PokerAppearance get() = when (this) {
        EMERALD -> PokerAppearance(DeckStyle.FOUR_COLOR, CardBackStyle.CLASSIC, TableStyle.CLASSIC)
        CHINESE -> PokerAppearance(DeckStyle.CHINESE, CardBackStyle.CHINESE, TableStyle.CHINESE)
        WESTERN -> PokerAppearance(DeckStyle.WESTERN, CardBackStyle.WESTERN, TableStyle.WESTERN)
        MODERN -> PokerAppearance(DeckStyle.MODERN, CardBackStyle.MODERN, TableStyle.MODERN)
        KYOTO -> PokerAppearance(DeckStyle.KYOTO, CardBackStyle.KYOTO, TableStyle.KYOTO)
    }

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: EMERALD
    }
}

enum class TableStyle(val code: String, @DrawableRes val art: Int?, val trim: Color) {
    CLASSIC("classic", null, Color(0xFF907246)),
    CYBERPUNK("cyberpunk", R.drawable.table_cyberpunk, Color(0xFF62D9F2)),
    WESTERN("western", R.drawable.table_western, Color(0xFFD89658)),
    TAROT("tarot", R.drawable.table_tarot, Color(0xFFE1BC6B)),
    CHINESE("chinese", null, Color(0xFFD9B464)),
    MODERN("modern", null, Color(0xFF38BDF8)),
    KYOTO("kyoto", null, Color(0xFFD8B468));

    val controlAccent: Color get() = if (this == CLASSIC) Palette.Gold else trim

    fun title(zh: Boolean) = when (this) {
        CLASSIC -> if (zh) "经典翡翠" else "Classic"
        CYBERPUNK -> if (zh) "赛博朋克" else "Cyberpunk"
        WESTERN -> if (zh) "美式西部" else "Western"
        TAROT -> if (zh) "塔罗" else "Tarot"
        CHINESE -> if (zh) "中国风" else "Chinese Ink"
        MODERN -> if (zh) "现代钛蓝" else "Modern"
        KYOTO -> if (zh) "日式靛蓝" else "Kyoto"
    }

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code } ?: CLASSIC
    }
}

enum class CardBackStyle(val art: TableStyle, @DrawableRes val image: Int?) {
    CLASSIC(TableStyle.CLASSIC, null),
    CYBERPUNK(TableStyle.CYBERPUNK, R.drawable.back_cyberpunk),
    WESTERN(TableStyle.WESTERN, R.drawable.back_western),
    TAROT(TableStyle.TAROT, R.drawable.back_tarot),
    CHINESE(TableStyle.CHINESE, null),
    MODERN(TableStyle.MODERN, null),
    KYOTO(TableStyle.KYOTO, null);

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
            PokerAppearance(DeckStyle.CHINESE, CardBackStyle.CHINESE, TableStyle.CHINESE),
            PokerAppearance(DeckStyle.WESTERN, CardBackStyle.WESTERN, TableStyle.WESTERN),
            PokerAppearance(DeckStyle.MODERN, CardBackStyle.MODERN, TableStyle.MODERN),
            PokerAppearance(DeckStyle.KYOTO, CardBackStyle.KYOTO, TableStyle.KYOTO),
            PokerAppearance(DeckStyle.CYBERPUNK, CardBackStyle.CYBERPUNK, TableStyle.CYBERPUNK),
            PokerAppearance(DeckStyle.TAROT, CardBackStyle.TAROT, TableStyle.TAROT),
        )
    }
}

val LocalCardBackStyle = staticCompositionLocalOf { CardBackStyle.CLASSIC }
val LocalTableStyle = staticCompositionLocalOf { TableStyle.CLASSIC }
