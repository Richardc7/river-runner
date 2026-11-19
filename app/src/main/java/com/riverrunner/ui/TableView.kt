package com.riverrunner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** All positions on the table in dp, for a table area of [w] x [h] with [n] seats (hero at the bottom). */
class TableGeometry(val w: Float, val h: Float, val n: Int, textScale: Float = 1f) {
    /** 7-9 players or a narrow table: smaller seats and board */
    val crowded = n >= 7 || w < 520f
    val cx = w / 2f
    val cy = h * 0.42f
    // Nine seats on short landscape viewports need compact pods so neighbouring
    // players remain separately tappable and the centre board stays legible.
    val podW = when {
        n == 9 && w < 445f -> 80f
        n == 9 && w < 500f -> 92f
        crowded -> 100f
        else -> 114f
    }
    val podH = 50f + ((textScale - 1f).coerceAtLeast(0f) * 56f).coerceAtMost(16f)
    private val cardWidth = if (n == 9 && w < 445f) 20f else if (crowded) 22f else 24f
    val miniCardW = cardWidth - ((textScale - 1f).coerceAtLeast(0f) * 10f).coerceAtMost(3f)
    val seatTextPadding = if (textScale > 1.05f) 5.dp else 8.dp
    val boardCardW = if (crowded) 31f else if (w < 520f || h < 320f) 34f else 38f
    val boardCardH = boardCardW * 1.4f
    val boardGap = 4f
    val boardW = boardCardW * 5 + boardGap * 4
    val boardTop = cy - boardCardH / 2
    val boardBottom = cy + boardCardH / 2
    // Scale hero cards gracefully with viewport height so lifted cards never touch the pot pill.
    val heroCardW = (h * 0.135f).coerceIn(38f, 50f)
    val heroCardH = heroCardW * 1.4f
    val heroCardsW = heroCardW * 1.8f
    val heroPod = Offset(cx, h - podH / 2 - 2f)
    val heroPodTop = heroPod.y - podH / 2
    val heroCardsTop = heroPodTop + 8f - heroCardH
    /** The pot sits under the board with guaranteed clearance above hero cards. */
    val potCenter = Offset(cx, minOf(boardBottom + 14f, heroCardsTop - 16f))
    val bannerCenter = Offset(cx, boardBottom + 15f)
    val heroBadgeLeft = Offset((cx - heroCardsW / 2 - 42f).coerceAtLeast(podW / 2 + 12f), heroPodTop - 10f)

    fun seatCenter(rel: Int): Offset {
        if (rel == 0) return heroPod
        val ang = Math.toRadians(90.0 + rel * 360.0 / n)
        val rx = w / 2 - podW / 2 - 6f
        val ry = h * if (n >= 7) 0.43f else 0.44f
        val x = cx + rx * cos(ang).toFloat()
        val y = cy + ry * sin(ang).toFloat()
        return Offset(
            x.coerceIn(podW / 2 + 6f, w - podW / 2 - 4f),
            y.coerceIn(podH / 2 + 12f, h - podH / 2 - 16f),
        )
    }
}

private fun Modifier.centerAt(p: Offset): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(
            (p.x.dp.toPx() - placeable.width / 2f).roundToInt(),
            (p.y.dp.toPx() - placeable.height / 2f).roundToInt(),
        )
    }
}

@Composable
fun PokerTable(
    t: TableUi,
    fmt: (Int) -> String,
    potLabel: String,
    modifier: Modifier = Modifier,
    animateDeal: Boolean = true,
    showTotalPot: Boolean = false,
) {
    BoxWithConstraints(modifier) {
        val n = t.seats.size
        val textScale = LocalDensity.current.fontScale
        val g = remember(maxWidth.value, maxHeight.value, n, textScale) {
            TableGeometry(maxWidth.value, maxHeight.value, n, textScale)
        }
        val heroIndex = t.seats.indexOfFirst { it.isHero }.coerceAtLeast(0)
        fun rel(i: Int) = (i - heroIndex + n) % n

        TableSurface(LocalTableStyle.current, Modifier.fillMaxSize())

        // board
        Row(
            Modifier.fillMaxSize().centerAt(Offset(g.cx, g.cy)).wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(g.boardGap.dp),
        ) {
            for (i in 0 until 5) {
                val c = t.board.getOrNull(i)
                BoardCard(c, g.boardCardW.dp, if (i < 3) i * 120 else 0, dim = t.highlight.isNotEmpty() && c !in t.highlight)
            }
        }

        Box(Modifier.fillMaxSize().centerAt(g.potCenter)) {
            if (showTotalPot) {
                ChipLabel("$potLabel ${fmt(t.totalPot)}")
            } else {
                val potShown by animateIntAsState(t.pot, tween(450), label = "pot")
                AnimatedVisibility(t.pot > 0, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                    ChipLabel("$potLabel ${fmt(potShown)}")
                }
            }
        }

        val bannerCenter = if (showTotalPot) Offset(g.cx, (g.boardTop - 15f).coerceAtLeast(16f)) else g.bannerCenter
        Box(Modifier.fillMaxSize().centerAt(bannerCenter)) {
            AnimatedVisibility(
                t.banner != null,
                enter = fadeIn(tween(300, 350)) + slideInVertically(tween(300, 350)) { it / 2 },
                exit = fadeOut(),
            ) {
                Text(
                    t.banner ?: "", color = Palette.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xE6000000))
                        .border(1.dp, Palette.Gold.copy(alpha = 0.65f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }

        // seats
        for ((i, s) in t.seats.withIndex()) {
            val r = rel(i)
            val center = g.seatCenter(r)
            val dealDelay = 80 * ((r + n - 1) % n)
            val dealFrom = Offset(g.cx - center.x, g.cy - center.y)
            if (s.isHero) {
                HeroSeat(s, g, t, fmt, dealDelay, dealFrom, animateDeal)
            } else {
                Box(Modifier.fillMaxSize().centerAt(center)) {
                    SeatPod(s, g, t, fmt, dealDelay, dealFrom, animateDeal)
                }
            }
        }

        // winnings flying from the pot to the winner
        for ((i, s) in t.seats.withIndex()) {
            if (s.won > 0) WinChip(t.handKey, s.won, g.potCenter, g.seatCenter(rel(i)), fmt)
        }
    }
}

@Composable
internal fun TableSurface(style: TableStyle, modifier: Modifier, centerY: Float = .42f) {
    val art = style.art?.let { ImageBitmap.imageResource(it) }
    Canvas(modifier) {
        val d = density
        val c = Offset(size.width * .5f, size.height * centerY)
        val rx = size.width * .43f
        val ry = size.height * .39f
        val rail = minOf(13f * d, size.height * .078f)
        val outer = Rect(c.x - rx - rail, c.y - ry - rail, c.x + rx + rail, c.y + ry + rail)
        val surface = Rect(c.x - rx, c.y - ry, c.x + rx, c.y + ry)

        // Subtle floor drop-shadow under the walnut rail
        val shadowRect = outer.inflate(3f * d)
        drawOval(
            Color(0x55000000),
            topLeft = Offset(shadowRect.left, shadowRect.top + 3f * d),
            size = shadowRect.size,
        )

        val railColors = if (art == null) listOf(Palette.RailHi, Palette.Rail, Color(0xFF1A110B)) else listOf(
            lerp(Palette.Ink, style.trim, .58f),
            lerp(Palette.Ink, style.trim, .16f),
            lerp(Palette.Ink, style.trim, .36f),
        )
        drawOval(
            Brush.verticalGradient(railColors, startY = outer.top, endY = outer.bottom),
            topLeft = outer.topLeft, size = outer.size,
        )
        val railBevel = outer.deflate(2f * d)
        drawOval(
            Brush.verticalGradient(
                listOf(style.trim.copy(alpha = .62f), Color.Transparent, style.trim.copy(alpha = .28f)),
                startY = outer.top, endY = outer.bottom,
            ),
            topLeft = railBevel.topLeft, size = railBevel.size, style = Stroke(1.3f * d),
        )
        if (art == null) {
            drawOval(
                Brush.radialGradient(
                    0.0f to Palette.FeltCenter,
                    0.62f to Palette.FeltMid,
                    1.0f to Palette.FeltEdge,
                    center = c,
                    radius = rx * 1.06f,
                ),
                topLeft = surface.topLeft, size = surface.size,
            )
            // Inner rail shadow ring for depth
            drawOval(
                Color(0x45000000),
                topLeft = surface.topLeft, size = surface.size, style = Stroke(3.5f * d),
            )
        } else {
            val cover = maxOf(surface.width / art.width, surface.height / art.height)
            val imageOrigin = surface.center - Offset(art.width * cover / 2f, art.height * cover / 2f)
            clipPath(Path().apply { addOval(surface) }) {
                withTransform({
                    translate(imageOrigin.x, imageOrigin.y)
                    scale(cover, cover, pivot = Offset.Zero)
                }) {
                    drawImage(art)
                }
            }
            drawOval(style.trim.copy(alpha = .68f), surface.topLeft, surface.size, style = Stroke(1.5f * d))
        }
        // Subtle brass/gold felt betting line
        drawOval(
            if (art == null) Palette.Gold.copy(alpha = .20f) else style.trim.copy(alpha = .26f),
            topLeft = Offset(c.x - rx * 0.79f, c.y - ry * 0.73f),
            size = Size(rx * 1.58f, ry * 1.46f), style = Stroke(1.3f * d),
        )
    }
}

/** Board card that flips face-up when it is dealt. */
@Composable
private fun BoardCard(card: Int?, width: Dp, delayMs: Int, dim: Boolean) {
    if (card == null) {
        val theme = LocalTableStyle.current
        val outline = if (theme == TableStyle.CLASSIC) Color(0x2EFFFFFF) else theme.trim.copy(alpha = .42f)
        Box(
            Modifier.size(width, width * 1.4f).clip(RoundedCornerShape(width * 0.12f))
                .background(Color(0x18000000)).border(1.dp, outline, RoundedCornerShape(width * 0.12f))
        )
        return
    }
    val rot = remember(card) { Animatable(180f) }
    LaunchedEffect(card) {
        delay(delayMs.toLong())
        rot.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { rotationY = rot.value; cameraDistance = 14f * density }) {
        if (rot.value > 90f) CardBack(width) else PlayingCard(card, width, dim)
    }
}

/** A card that flips between back and face. */
@Composable
fun FlipCard(card: Int, faceUp: Boolean, width: Dp, dim: Boolean = false) {
    val rot by animateFloatAsState(if (faceUp) 0f else 180f, tween(450), label = "flip")
    Box(Modifier.graphicsLayer { rotationY = rot; cameraDistance = 14f * density }) {
        if (rot > 90f) CardBack(width) else PlayingCard(card, width, dim)
    }
}

/** Animates content flying in from the middle of the table at the start of a hand. */
@Composable
private fun DealIn(key: Long, delayMs: Int, from: Offset, enabled: Boolean, content: @Composable () -> Unit) {
    val p = remember(key) { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(key) {
        if (enabled) {
            delay(delayMs.toLong())
            p.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            val k = 1f - p.value
            translationX = from.x.dp.toPx() * k
            translationY = from.y.dp.toPx() * k
            rotationZ = -25f * k
            alpha = if (p.value < 0.02f) 0f else 1f
            scaleX = 0.7f + 0.3f * p.value
            scaleY = 0.7f + 0.3f * p.value
        }
    ) { content() }
}

@Composable
private fun actingPulse(active: Boolean): Float {
    if (!active) return 0f
    val tr = rememberInfiniteTransition(label = "pulse")
    val v by tr.animateFloat(0.35f, 1f, infiniteRepeatable(tween(650, easing = LinearEasing), RepeatMode.Reverse), label = "pulseV")
    return v
}

@Composable
private fun ThinkingBar(key: Int, durationMs: Long, modifier: Modifier) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key, durationMs) {
        if (durationMs > 0) p.animateTo(1f, tween(durationMs.toInt(), easing = LinearEasing))
    }
    Box(modifier.height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(p.value).background(Palette.Accent))
    }
}

/** Keep the action in the seat frame so it never covers another seat or a card. */
@Composable
private fun SeatStatus(seat: SeatUi, fmt: (Int) -> String, modifier: Modifier = Modifier) {
    val caption = seat.handName ?: seat.action ?: seat.streetBet.takeIf { it > 0 }?.let(fmt)
    val color = when {
        seat.handName != null && seat.won > 0 -> Palette.Gold
        seat.actKind == ActKind.BET -> Palette.Gold
        seat.actKind == ActKind.ALLIN -> Palette.Red
        else -> Palette.TextDim
    }
    val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    Text(
        caption.orEmpty(), modifier = modifier.height((12 * textScale).dp), color = color,
        fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DealerButton() {
    Box(
        Modifier.size(18.dp).shadow(3.dp, CircleShape).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFFFFFDF7), Color(0xFFDCD3BE))))
            .border(1.5.dp, Color(0xFF8D6F37), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text("D", color = Palette.Ink, fontSize = 10.sp, fontWeight = FontWeight.Black, lineHeight = 10.sp) }
}

@Composable
private fun PodFrame(s: SeatUi, pulse: Float, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val accent = LocalTableStyle.current.controlAccent
    val borderColor = when {
        s.won > 0 -> Palette.Gold
        s.isActing -> accent.copy(alpha = .48f + pulse * .52f)
        else -> accent.copy(alpha = .34f)
    }
    val borderW = if (s.isActing || s.won > 0) 2.dp else 1.dp
    Box(
        modifier
            .shadow(
                if (s.isActing || s.won > 0) 8.dp else 4.dp, shape,
                ambientColor = if (s.won > 0) Palette.Gold else accent,
                spotColor = if (s.won > 0) Palette.Gold else if (s.isActing) accent else Color.Black,
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        lerp(Palette.PanelHi, accent, if (s.isActing) .18f else .09f),
                        lerp(Palette.Bg, accent, .028f),
                    )
                )
            )
            .border(borderW, borderColor, shape),
    ) { content() }
}

@Composable
private fun SeatPod(s: SeatUi, g: TableGeometry, t: TableUi, fmt: (Int) -> String, dealDelay: Int, dealFrom: Offset, animateDeal: Boolean) {
    val pulse = actingPulse(s.isActing)
    val dimAlpha by animateFloatAsState(if (s.busted) 0.3f else if (s.folded) 0.5f else 1f, tween(300), label = "fold")
    Box(Modifier.size((g.podW + 12).dp, (g.podH + 34).dp), contentAlignment = Alignment.Center) {
        PodFrame(s, pulse, Modifier.size(g.podW.dp, g.podH.dp).alpha(dimAlpha)) {
            Column(Modifier.fillMaxSize().padding(start = g.seatTextPadding, end = 5.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = Palette.Text, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (s.busted) "—" else fmt(s.stack), maxLines = 1,
                            color = if (s.allIn && !s.busted) Palette.Red else Palette.Gold, fontSize = if (g.crowded) 16.sp else 17.sp, lineHeight = 18.sp, fontFamily = Numeric, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    val hole = s.hole
                    if (hole != null && !s.busted) {
                        AnimatedVisibility(!s.folded || s.faceUp, enter = fadeIn(), exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.4f)) {
                            DealIn(t.handKey, dealDelay, dealFrom, animateDeal) {
                                Box(Modifier.size((g.miniCardW * 1.68f).dp, (g.miniCardW * 1.4f).dp)) {
                                    val dim = t.highlight.isNotEmpty()
                                    FlipCard(hole[0], s.faceUp, g.miniCardW.dp, dim && hole[0] !in t.highlight)
                                    Box(Modifier.offset(x = (g.miniCardW * 0.68f).dp, y = 0.dp)) {
                                        FlipCard(hole[1], s.faceUp, g.miniCardW.dp, dim && hole[1] !in t.highlight)
                                    }
                                }
                            }
                        }
                    }
                }
                SeatReadout(s, fmt, Modifier.fillMaxWidth())
                Spacer(Modifier.height(3.dp))
            }
            if (s.isActing) {
                ThinkingBar(t.actionSeq, t.thinkingMs, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp))
            }
        }
        // dealer button (top-left)
        if (s.isButton) Box(Modifier.align(Alignment.TopStart).offset(x = 0.dp, y = 10.dp)) { DealerButton() }
        // position (+ style in replay) (bottom-left)
        val posText = listOfNotNull(s.position.takeIf { it.isNotEmpty() }, s.tag).joinToString(" · ")
        if (posText.isNotEmpty()) {
            Text(
                posText, color = Palette.TextDim, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).offset(x = 12.dp, y = (-2).dp)
                    .clip(RoundedCornerShape(4.dp)).background(Color(0xCC0B1210)).padding(horizontal = 4.dp),
            )
        }
        // winnings (bottom-right)
        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = 0.dp)) {
            WinningsBadge(s, fmt)
        }
    }
}

@Composable
private fun WinningsBadge(s: SeatUi, fmt: (Int) -> String) {
    AnimatedVisibility(s.won > 0, enter = scaleIn(tween(300, 550)) + fadeIn(tween(300, 550)), exit = fadeOut()) {
        Text(
            "+" + fmt(s.won), color = Palette.Ink, fontSize = 14.sp, fontFamily = Numeric, fontWeight = FontWeight.Bold, maxLines = 1,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Green).padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun SeatReadout(s: SeatUi, fmt: (Int) -> String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        SeatStatus(s, fmt, Modifier.weight(1f))
        s.equity?.let {
            Text(
                "%.1f%%".format(it * 100), color = Palette.Blue, fontSize = 11.sp,
                fontFamily = Numeric, fontWeight = FontWeight.Bold, lineHeight = 12.sp, maxLines = 1,
            )
        }
    }
}

@Composable
private fun HeroSeat(s: SeatUi, g: TableGeometry, t: TableUi, fmt: (Int) -> String, dealDelay: Int, dealFrom: Offset, animateDeal: Boolean) {
    val pulse = actingPulse(s.isActing)
    val lift by animateFloatAsState(if (s.isActing) -8f else 0f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "lift")
    val cardsAlpha by animateFloatAsState(if (s.folded) 0.45f else 1f, tween(300), label = "heroFold")
    // cards
    val hole = s.hole
    if (hole != null && !s.busted) {
        val cardsCenter = Offset(g.cx, g.heroCardsTop + g.heroCardH / 2)
        Box(Modifier.fillMaxSize().centerAt(cardsCenter)) {
            DealIn(t.handKey, dealDelay, Offset(g.cx - cardsCenter.x, g.cy - cardsCenter.y), animateDeal) {
                Box(Modifier.graphicsLayer { translationY = lift.dp.toPx(); alpha = cardsAlpha }.size(g.heroCardsW.dp, g.heroCardH.dp)) {
                    val dim = t.highlight.isNotEmpty()
                    Box(Modifier.graphicsLayer { rotationZ = -5f }) {
                        PlayingCard(hole[0], g.heroCardW.dp, dim && hole[0] !in t.highlight)
                    }
                    Box(Modifier.offset(x = (g.heroCardW * 0.8f).dp).graphicsLayer { rotationZ = 5f }) {
                        PlayingCard(hole[1], g.heroCardW.dp, dim && hole[1] !in t.highlight)
                    }
                }
            }
        }
    }
    // pod
    Box(Modifier.fillMaxSize().centerAt(g.heroPod)) {
        Box {
            PodFrame(s, pulse, Modifier.size((g.podW + 16).dp, g.podH.dp)) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            s.name, color = Palette.Text, fontSize = 12.sp, lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                        )
                        if (s.position.isNotEmpty()) {
                            Text(
                                s.position, color = Palette.TextDim, fontSize = 10.sp, lineHeight = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            if (s.busted) "—" else fmt(s.stack), color = if (s.allIn) Palette.Red else Palette.Gold,
                            fontSize = 17.sp, lineHeight = 19.sp, fontFamily = Numeric,
                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    if (s.equity == null) SeatStatus(s, fmt, Modifier.padding(horizontal = 5.dp))
                    else SeatReadout(s, fmt, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                }
            }
            if (s.isButton) Box(Modifier.align(Alignment.CenterStart).offset(x = (-22).dp)) { DealerButton() }
        }
    }
    Box(Modifier.fillMaxSize().centerAt(g.heroBadgeLeft)) {
        WinningsBadge(s, fmt)
    }
}

@Composable
private fun WinChip(key: Long, amount: Int, pot: Offset, seat: Offset, fmt: (Int) -> String) {
    val pos = remember(key, amount) { Animatable(pot, Offset.VectorConverter) }
    val alpha = remember(key, amount) { Animatable(1f) }
    LaunchedEffect(key, amount) {
        pos.animateTo(seat, tween(600, easing = FastOutSlowInEasing))
        alpha.animateTo(0f, tween(250))
    }
    if (alpha.value > 0f) {
        Box(Modifier.fillMaxSize().centerAt(pos.value).alpha(alpha.value)) {
            ChipLabel(fmt(amount), color = Palette.Green)
        }
    }
}
