package com.riverrunner.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riverrunner.engine.Cards

/**
 * Obsidian & Emerald Studio colour tokens: deep matte obsidian surfaces,
 * muted studio emerald felt, and champagne-gold accents.
 */
object Palette {
    internal var highContrast by mutableStateOf(false)
    internal var theme by mutableStateOf(AppTheme.EMERALD)

    val Bg: Color get() = if (highContrast) Color(0xFF000000) else theme.bg
    val Panel: Color get() = if (highContrast) Color(0xFF0E1110) else theme.panel
    val PanelHi: Color get() = if (highContrast) Color(0xFF191E1B) else theme.panelHi
    val PanelHi2: Color get() = if (highContrast) Color(0xFF323C37) else theme.panelHi2
    val Line: Color get() = if (highContrast) Color(0xFF89968E) else theme.line
    val FeltCenter: Color get() = theme.feltCenter
    val FeltMid: Color get() = theme.feltMid
    val FeltEdge: Color get() = theme.feltEdge
    val Rail: Color get() = theme.rail
    val RailHi: Color get() = theme.railHi
    val Gold: Color get() = if (highContrast) Color(0xFFFFD76D) else theme.gold
    val GoldDim: Color get() = theme.goldDim
    val Accent: Color get() = Gold          // selection, your turn, primary action
    val AccentDim: Color get() = GoldDim
    val Text: Color get() = if (highContrast) Color(0xFFFFFFFF) else theme.text
    val TextDim: Color get() = if (highContrast) Color(0xFFD9E1DC) else theme.textDim
    val Red: Color get() = if (highContrast) Color(0xFFFF837D) else theme.red
    val Green: Color get() = if (highContrast) Color(0xFF70E5AA) else theme.green
    val Blue: Color get() = if (highContrast) Color(0xFF90C6FF) else theme.blue
    val FoldBtn = Color(0xFFA83A33)
    val CallBtn = Color(0xFF1F7A50)
    val RaiseBtn: Color get() = Gold
    val AllInBtn = Color(0xFFA82C22)
    val Ink: Color get() = theme.ink
}

/** Condensed numerals for chip amounts and scores (Barlow Condensed, SIL OFL). */
val Numeric = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(com.riverrunner.R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(com.riverrunner.R.font.barlow_condensed_bold, FontWeight.Bold),
)

private val SUIT_SYMBOL = charArrayOf('♣', '♦', '♥', '♠')

fun rankText(card: Int): String = Cards.RANKS[Cards.rank(card)].let { if (it == 'T') "10" else it.toString() }
fun suitText(card: Int): String = SUIT_SYMBOL[Cards.suit(card)].toString()

/** Frosted dark-glass studio container used across Home, Setup, and Settings. */
internal fun Modifier.studioCard(
    accent: Color = Palette.Line,
    radius: Dp = 16.dp,
    highlighted: Boolean = false,
): Modifier {
    val shape = RoundedCornerShape(radius)
    val topColor = if (highlighted) lerp(Palette.PanelHi, accent, 0.14f) else lerp(Palette.PanelHi, Palette.Panel, 0.45f)
    val bottomColor = lerp(Palette.Panel, Palette.Bg, 0.35f)
    val borderColor = if (highlighted) accent.copy(alpha = 0.55f) else accent.copy(alpha = 0.45f)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
        .border(1.dp, borderColor, shape)
}

/** Unified segmented control bar for compact mutually-exclusive choices. */
@Composable
internal fun <T> StudioSegmentedBar(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .clip(shape)
            .background(Palette.Bg.copy(alpha = 0.78f))
            .border(1.dp, Palette.Line.copy(alpha = 0.6f), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((value, label) in options) {
            val active = value == selected
            val pillShape = RoundedCornerShape(7.dp)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 34.dp)
                    .clip(pillShape)
                    .background(
                        if (active) Brush.verticalGradient(listOf(Palette.FeltCenter, Palette.FeltMid))
                        else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .then(
                        if (active) Modifier.border(1.dp, Palette.Green.copy(alpha = 0.55f), pillShape)
                        else Modifier
                    )
                    .clickable(role = Role.RadioButton) { onSelect(value) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) Color.White else Palette.TextDim,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Card face (or back when [card] is null). Height = 1.4 x width. */
@Composable
fun PlayingCard(card: Int?, width: Dp, dim: Boolean = false, modifier: Modifier = Modifier) {
    val h = width * 1.4f
    val shape = RoundedCornerShape(width * 0.12f)
    if (card == null) {
        CardBack(width, modifier)
        return
    }
    val suit = Cards.suit(card)
    val style = LocalDeckStyle.current
    val filled = style == DeckStyle.FOUR_COLOR_FILLED
    val ink = when (style) {
        DeckStyle.CLASSIC -> classicColor(suit)
        DeckStyle.FOUR_COLOR -> fourColor(suit)
        DeckStyle.FOUR_COLOR_FILLED -> Color.White
        DeckStyle.CYBERPUNK -> when (suit) {
            0 -> Color(0xFF84F5A8)
            1 -> Color(0xFF70E7FF)
            2 -> Color(0xFFFF86B4)
            else -> Color(0xFFE1DAFF)
        }
        DeckStyle.WESTERN -> if (suit == 1 || suit == 2) Color(0xFF902F2B) else Color(0xFF382820)
        DeckStyle.TAROT -> when (suit) {
            0 -> Color(0xFF2C684E)
            1 -> Color(0xFF3458A0)
            2 -> Color(0xFF9A2D4B)
            else -> Color(0xFF292346)
        }
        DeckStyle.CHINESE -> when (suit) {
            0 -> Color(0xFF1D6B53) // celadon jade
            1 -> Color(0xFF9E7022) // antique bronze gold
            2 -> Color(0xFFB83227) // vermilion seal red
            else -> Color(0xFF171A1E) // sumi ink
        }
        DeckStyle.MODERN -> when (suit) {
            0 -> Color(0xFF4ADE80)
            1 -> Color(0xFF38BDF8)
            2 -> Color(0xFFFB7185)
            else -> Color(0xFFF8FAFC)
        }
        DeckStyle.KYOTO -> when (suit) {
            0 -> Color(0xFF236B5E) // bamboo teal
            1 -> Color(0xFF365C94) // aizome slate blue
            2 -> Color(0xFFB83B35) // sakura vermilion
            else -> Color(0xFF16243B) // deep indigo ink
        }
    }
    val face = when (style) {
        DeckStyle.FOUR_COLOR_FILLED -> if (suit == 3) Color(0xFF2A2D31) else fourColor(suit)
        DeckStyle.CYBERPUNK -> Color(0xFF151629)
        DeckStyle.WESTERN -> Color(0xFFFFE6BB)
        DeckStyle.TAROT -> Color(0xFFF7EBCA)
        DeckStyle.CHINESE -> Color(0xFFF7F1E1)
        DeckStyle.MODERN -> Color(0xFF141B29)
        DeckStyle.KYOTO -> Color(0xFFF5F0E6)
        else -> Color(0xFFFBF9F3)
    }
    val border = when (style) {
        DeckStyle.FOUR_COLOR_FILLED -> Color.White.copy(alpha = 0.85f)
        DeckStyle.CYBERPUNK -> ink.copy(alpha = 0.66f)
        DeckStyle.WESTERN -> Color(0xFFAE7946)
        DeckStyle.TAROT -> Color(0xFFA37A3D)
        DeckStyle.CHINESE -> Color(0xFFC9A458)
        DeckStyle.MODERN -> Color(0xFF475569)
        DeckStyle.KYOTO -> Color(0xFFC7A65E)
        else -> Color(0x45000000)
    }
    val font = when (style) {
        DeckStyle.CYBERPUNK -> FontFamily.Monospace
        DeckStyle.TAROT, DeckStyle.WESTERN, DeckStyle.CHINESE, DeckStyle.KYOTO -> FontFamily.Serif
        else -> FontFamily.Default
    }
    // Compensate fontScale inside fixed-dp cards so 110%/120% text settings never clip ranks.
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val rankFontSp = (width.value * 0.42f / fontScale).sp
    val rankLineSp = (width.value * 0.45f / fontScale).sp
    Box(
        modifier.size(width, h)
            .shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .background(face)
            .border(if (filled) 1.5.dp else 1.dp, border, shape)
    ) {
        if (style == DeckStyle.CHINESE || style == DeckStyle.KYOTO) {
            Canvas(Modifier.fillMaxSize().padding(width * 0.06f)) {
                drawRoundRect(
                    border.copy(alpha = 0.45f),
                    style = Stroke(0.8.dp.toPx()),
                    cornerRadius = CornerRadius(width.toPx() * 0.08f, width.toPx() * 0.08f),
                )
            }
        }
        Column(Modifier.padding(start = width * 0.09f, top = width * 0.04f)) {
            Text(
                rankText(card), color = ink, fontWeight = FontWeight.Bold, fontFamily = font,
                fontSize = rankFontSp, lineHeight = rankLineSp, maxLines = 1, softWrap = false,
            )
            SuitIcon(suit, width * 0.26f, ink)
        }
        SuitIcon(
            suit, width * 0.48f, ink.copy(alpha = 0.95f),
            Modifier.align(Alignment.BottomEnd).padding(end = width * 0.08f, bottom = width * 0.08f),
        )
        if (dim) Box(Modifier.matchParentSize().background(Color(0x73000000)))
    }
}

@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.12f)
    val style = LocalCardBackStyle.current
    val art = style.image
    if (art != null) {
        Image(
            painterResource(art), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = modifier.size(width, width * 1.4f)
                .shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f))
                .clip(shape)
                .border(1.dp, style.art.trim.copy(alpha = .75f), shape),
        )
        return
    }
    val bgColors = when (style) {
        CardBackStyle.CHINESE -> listOf(Color(0xFF6B1C16), Color(0xFF280A08))
        CardBackStyle.MODERN -> listOf(Color(0xFF1D3054), Color(0xFF0A1120))
        CardBackStyle.KYOTO -> listOf(Color(0xFF1A365C), Color(0xFF0B172A))
        else -> listOf(Color(0xFF962537), Color(0xFF4B0F1A))
    }
    val rimColor = when (style) {
        CardBackStyle.CHINESE -> Color(0xFFD9B464)
        CardBackStyle.MODERN -> Color(0xFF38BDF8)
        CardBackStyle.KYOTO -> Color(0xFFD8B468)
        else -> Color(0xFFE8D9B0)
    }
    Box(
        modifier.size(width, width * 1.4f)
            .shadow(2.dp, shape, ambientColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .background(Brush.linearGradient(bgColors))
            .border(1.dp, rimColor.copy(alpha = 0.82f), shape)
    ) {
        Canvas(Modifier.fillMaxSize().padding(width * 0.1f)) {
            val w = size.width
            val h = size.height
            when (style) {
                CardBackStyle.CHINESE -> {
                    drawRoundRect(rimColor.copy(alpha = 0.65f), style = Stroke(1.1f), cornerRadius = CornerRadius(3f, 3f))
                    val inset = w * 0.16f
                    drawRoundRect(
                        rimColor.copy(alpha = 0.38f),
                        topLeft = Offset(inset, inset),
                        size = Size(w - inset * 2, h - inset * 2),
                        style = Stroke(0.9f),
                        cornerRadius = CornerRadius(2f, 2f),
                    )
                    val cx = w / 2f
                    val cy = h / 2f
                    val rx = w * 0.24f
                    val ry = h * 0.18f
                    val diamond = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy - ry); lineTo(cx + rx, cy); lineTo(cx, cy + ry); lineTo(cx - rx, cy); close()
                    }
                    drawPath(diamond, Color(0xFF1F5B4F).copy(alpha = 0.85f))
                    drawPath(diamond, rimColor.copy(alpha = 0.85f), style = Stroke(1.1f))
                }
                CardBackStyle.MODERN -> {
                    val step = w / 3.2f
                    var x = -h
                    while (x < w) {
                        drawLine(rimColor.copy(alpha = 0.22f), Offset(x, h), Offset(x + h, 0f), 1f)
                        x += step
                    }
                    drawRoundRect(rimColor.copy(alpha = 0.55f), style = Stroke(1f), cornerRadius = CornerRadius(3f, 3f))
                    val cx = w / 2f
                    val cy = h / 2f
                    val r = w * 0.22f
                    drawCircle(rimColor.copy(alpha = 0.18f), r * 1.4f, Offset(cx, cy))
                    drawCircle(rimColor.copy(alpha = 0.75f), r, Offset(cx, cy), style = Stroke(1.2f))
                }
                CardBackStyle.KYOTO -> {
                    val cols = 2
                    val rows = 3
                    val cw = w / cols
                    val ch = h / rows
                    for (ix in 0..cols) {
                        drawLine(rimColor.copy(alpha = 0.22f), Offset(ix * cw, 0f), Offset(ix * cw, h), 0.8f)
                    }
                    for (iy in 0..rows) {
                        drawLine(rimColor.copy(alpha = 0.22f), Offset(0f, iy * ch), Offset(w, iy * ch), 0.8f)
                    }
                    for (ix in 0 until cols) for (iy in 0 until rows) {
                        val x0 = ix * cw; val y0 = iy * ch
                        drawLine(rimColor.copy(alpha = 0.26f), Offset(x0, y0), Offset(x0 + cw, y0 + ch), 0.8f)
                        drawLine(rimColor.copy(alpha = 0.26f), Offset(x0 + cw, y0), Offset(x0, y0 + ch), 0.8f)
                    }
                    drawRoundRect(rimColor.copy(alpha = 0.6f), style = Stroke(1f), cornerRadius = CornerRadius(3f, 3f))
                    drawCircle(Color(0xFFC6422C), w * 0.13f, Offset(w / 2f, h / 2f))
                    drawCircle(rimColor.copy(alpha = 0.85f), w * 0.13f, Offset(w / 2f, h / 2f), style = Stroke(0.9f))
                }
                else -> {
                    val step = w / 3.5f
                    var x = -h
                    while (x < w) {
                        drawLine(Color(0x30FFFFFF), Offset(x, h), Offset(x + h, 0f), 1.2f)
                        x += step
                    }
                    drawRoundRect(Color(0x66E8D9B0), style = Stroke(1f), size = Size(w, h), cornerRadius = CornerRadius(3f, 3f))
                }
            }
        }
    }
}

/** Tactile casino chip badge + amount. */
@Composable
fun ChipLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.Gold) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .shadow(3.dp, shape, ambientColor = Color.Black.copy(alpha = 0.6f))
            .clip(shape)
            .background(Color(0xE6091310))
            .border(1.dp, color.copy(alpha = 0.62f), shape)
            .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(13.dp)) {
            val r = size.minDimension / 2f
            drawCircle(color, r)
            drawCircle(
                Color.White.copy(alpha = 0.88f), r - 1.2.dp.toPx(),
                style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.2f, 2.8f), 0f)),
            )
            drawCircle(Color(0xFF0C1310).copy(alpha = 0.25f), r * 0.45f)
            drawCircle(Color.White.copy(alpha = 0.45f), r * 0.45f, style = Stroke(0.8.dp.toPx()))
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text, color = Palette.Text, fontSize = 13.sp, fontFamily = Numeric,
            fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
        )
    }
}

/** Adds a small "press down" scale to any clickable. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(stiffness = 900f), label = "press")
    return this.scale(scale).clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.pressable(onClick = onClick).heightIn(min = 38.dp).clip(shape)
            .background(
                if (selected) Brush.verticalGradient(listOf(Palette.FeltCenter, Palette.FeltMid))
                else Brush.verticalGradient(listOf(Palette.PanelHi, Palette.Panel))
            )
            .border(
                1.dp,
                if (selected) Palette.Green.copy(alpha = 0.65f) else Palette.Line.copy(alpha = 0.55f),
                shape,
            )
            .padding(horizontal = 13.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Palette.TextDim,
            maxLines = 1,
            softWrap = false,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun SettingsChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Palette.PanelHi, labelColor = Palette.Text,
            selectedContainerColor = Palette.Gold, selectedLabelColor = Palette.Ink,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> ConfigurationChoices(
    label: String, options: List<T>, selected: T, title: (T) -> String, onPick: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (option in options) SettingsChip(title(option), selected == option) { onPick(option) }
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    sub: String? = null,
    textColor: Color = Palette.Text,
    onClick: () -> Unit,
) {
    Box(
        modifier.pressable(enabled, onClick).clip(RoundedCornerShape(12.dp))
            .background(
                if (enabled) Brush.verticalGradient(listOf(color.copy(alpha = 1f), lerpDark(color)))
                else Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), color.copy(alpha = 0.3f)))
            )
            .border(1.dp, Color(0x45FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text, color = if (enabled) textColor else Palette.TextDim, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
            if (sub != null) Text(sub, color = textColor.copy(alpha = 0.92f), fontSize = 15.sp, lineHeight = 16.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private fun lerpDark(c: Color) = Color(c.red * 0.72f, c.green * 0.72f, c.blue * 0.72f, c.alpha)

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier.pressable(onClick = onClick).heightIn(min = 38.dp).widthIn(min = 38.dp).clip(shape)
            .background(if (highlight) Palette.AccentDim else Palette.PanelHi)
            .border(1.dp, if (highlight) Palette.Gold.copy(alpha = 0.6f) else Palette.Line.copy(alpha = 0.55f), shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Palette.Text, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        color = Palette.TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}
