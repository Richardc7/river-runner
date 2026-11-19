package com.riverrunner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.i18n.Strings

@Composable
fun RiverTheme(
    strings: Strings,
    deck: DeckStyle = DeckStyle.FOUR_COLOR,
    back: CardBackStyle = CardBackStyle.CLASSIC,
    table: TableStyle = TableStyle.CLASSIC,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Palette.Accent, onPrimary = Palette.Ink, secondary = Palette.Accent, background = Palette.Bg, surface = Palette.Panel)) {
        CompositionLocalProvider(
            LocalStrings provides strings,
            LocalDeckStyle provides deck,
            LocalCardBackStyle provides back,
            LocalTableStyle provides table,
            content = content,
        )
    }
}
