package com.riverrunner.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.riverrunner.i18n.LocalStrings
import com.riverrunner.i18n.Strings

@Composable
fun RiverTheme(
    strings: Strings,
    deck: DeckStyle = DeckStyle.FOUR_COLOR,
    back: CardBackStyle = CardBackStyle.CLASSIC,
    table: TableStyle = TableStyle.CLASSIC,
    textSizePercent: Int = 100,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Palette.Accent, onPrimary = Palette.Ink, secondary = Palette.Accent,
        background = Palette.Bg, onBackground = Palette.Text, surface = Palette.Panel,
        onSurface = Palette.Text, onSurfaceVariant = Palette.TextDim,
        surfaceContainerHigh = Palette.PanelHi, outline = Palette.Line,
    )) {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, density.fontScale * textSizePercent / 100f),
            LocalStrings provides strings,
            LocalDeckStyle provides deck,
            LocalCardBackStyle provides back,
            LocalTableStyle provides table,
            content = content,
        )
    }
}
