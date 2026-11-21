package com.wdtt.client.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wdtt.client.lite.PowerLevel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt
import com.wdtt.client.ui.motion.magnetTo
import kotlin.math.roundToInt

private const val LOCK_HINT = "Изменение мощности доступно после отключения."

/**
 * Power card: level title, three-stop slider, description. At the maximum level the card
 * turns into a dark neon panel in both themes, so the near-white title stays readable.
 * While [locked] the slider ignores input and shows why.
 */
@Composable
fun PowerCard(
    level: PowerLevel,
    locked: Boolean,
    onLevelChange: (PowerLevel) -> Unit,
    modifier: Modifier = Modifier,
    // Thumb position 0..1 drives every color, so dragging blends them continuously:
    // blue → green on the first half, green → neon night panel on the second.
    // Hoisted so the connect button can blend with it.
    pos: Animatable<Float, androidx.compose.animation.core.AnimationVector1D> = remember { Animatable(level.ordinal / 2f) },
) {
    val colors = levelColors(level)
    val scheme = MaterialTheme.colorScheme
    val k = ((pos.value - 0.5f) * 2f).coerceIn(0f, 1f)
    val titleBlend = lerp(levelColors(PowerLevel.Stable).accent, levelColors(PowerLevel.Balanced).accent, (pos.value * 2f).coerceIn(0f, 1f))
    val cardColor = lerp(scheme.surface, Neon.Night, k)
    // Text switches to night colors faster than the panel darkens, so it never sits
    // grey-on-grey in the middle of the blend.
    val tk = ((k - 0.3f) / 0.2f).coerceIn(0f, 1f)
    val textMuted = lerp(scheme.onSurfaceVariant, Neon.NightMuted, tk)
    val textFaint = lerp(scheme.outline, Neon.NightFaint, tk)
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardColor)
            .then(if (k > 0.01f) Modifier.border(1.dp, colors.neon[1].copy(alpha = 0.45f * k), shape) else Modifier),
    ) {
        if (k > 0.01f) NeonAura(colors.neon, k)
        Column(
            Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "МОЩНОСТЬ",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                color = textFaint,
            )
            AnimatedContent(
                targetState = level,
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 3 }) togetherWith
                        (fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 3 })
                },
                label = "title",
            ) { l ->
                if (l == PowerLevel.Maximum) NeonTitle(l.title, colors.neon)
                else SheenTitle(l.title, titleBlend, (1f - abs(pos.value - 0.5f) * 2f).coerceIn(0f, 1f))
            }
            PowerSlider(
                level = level,
                pos = pos,
                neon = colors.neon,
                locked = locked,
                trackColor = lerp(scheme.surfaceVariant, Neon.NightTrack, k),
                onLevelChange = onLevelChange,
            )
            AnimatedVisibility(
                visible = locked,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Text(LOCK_HINT, style = MaterialTheme.typography.bodySmall, color = textFaint)
            }
            AnimatedContent(
                targetState = level,
                // The old description dissolves, then the new one fades in; the height eases.
                transitionSpec = {
                    (fadeIn(tween(420, delayMillis = 180)) togetherWith fadeOut(tween(260)))
                        .using(androidx.compose.animation.SizeTransform(clip = false) { _, _ -> tween(380) })
                },
                label = "desc",
            ) { l ->
                Text(
                    l.description,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                    color = textMuted,
                )
            }
        }
    }
}

/**
 * Level title; at the balanced level a soft light sheen passes over it now and then
 * ([strength] 0..1 fades it in as the thumb nears the middle).
 */
@Composable
private fun SheenTitle(text: String, color: Color, strength: Float) {
    val style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
    if (strength <= 0.01f) {
        Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    val motion = rememberMotionScale()
    val t = rememberInfiniteTransition(label = "sheen")
    // One pass every ~4 s: the band crosses in the first 40%, then rests.
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween((4200 / motion.coerceAtLeast(0.05f)).toInt(), easing = LinearEasing)), label = "p")
    val width = with(LocalDensity.current) { 260.dp.toPx() }
    val x = (p / 0.4f).coerceAtMost(1.2f) * width * 1.6f - width * 0.4f
    val light = lerp(color, Color.White, 0.55f * strength)
    val brush = Brush.linearGradient(
        0f to color, 0.42f to color, 0.5f to light, 0.58f to color, 1f to color,
        start = Offset(x - width * 0.5f, 0f), end = Offset(x + width * 0.5f, 0f),
    )
    Text(text, style = style.copy(brush = brush), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Near-white title with a thin flowing neon outline and a soft glow. */
@Composable
private fun NeonTitle(text: String, neon: List<Color>) {
    val motion = rememberMotionScale()
    val t = rememberInfiniteTransition(label = "neon")
    val shift by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween((2600 / motion).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    val pulse by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween((1600 / motion).toInt()), RepeatMode.Reverse),
        label = "pulse",
    )
    val period = with(LocalDensity.current) { 220.dp.toPx() }
    val brush = remember(shift, neon) {
        Brush.linearGradient(
            colors = neon + neon.first(),
            start = Offset(shift * period, 0f),
            end = Offset(shift * period + period, 0f),
            tileMode = TileMode.Repeated,
        )
    }
    val base = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.2).sp)
    Box {
        Text(
            text,
            style = base.copy(
                brush = brush,
                drawStyle = Stroke(width = with(LocalDensity.current) { 2.4.dp.toPx() }),
                shadow = Shadow(neon[1].copy(alpha = 0.5f + 0.3f * pulse), blurRadius = 14f + 10f * pulse),
            ),
            maxLines = 1,
        )
        Text(text, style = base.copy(color = Neon.NightText), maxLines = 1)
    }
}

/** Slowly turning blurred neon wash behind the card content. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.NeonAura(neon: List<Color>, strength: Float) {
    val motion = rememberMotionScale()
    val t = rememberInfiniteTransition(label = "aura")
    val angle by t.animateFloat(
        0f, 360f, infiniteRepeatable(tween((9000 / motion).toInt(), easing = LinearEasing)), label = "a",
    )
    Canvas(Modifier.matchParentSize().alpha(0.22f * strength)) {
        val c = center
        rotate(angle, c) {
            drawCircle(Brush.sweepGradient(neon + neon.first(), c), radius = size.maxDimension, center = c)
        }
    }
}

/** Reach of each stop's pull, as a share of the track. */
private const val MAGNET_RADIUS = 0.13f

/** Inside this the thumb is caught and stays on the stop. */
private const val MAGNET_CAPTURE = 0.035f

/**
 * Magnet while dragging. Entering the reach, the thumb lags the finger a little; the
 * closer it gets, the faster it is drawn in (distance ~ sqrt), until it is caught on
 * the stop. Leaving, the same curve: it holds, then breaks free.
 */
internal fun magnet(f: Float): Float {
    val stop = (f * 2f).roundToInt() / 2f
    val d = f - stop
    val x = abs(d)
    if (x >= MAGNET_RADIUS) return f
    if (x <= MAGNET_CAPTURE) return stop
    val u = (x - MAGNET_CAPTURE) / (MAGNET_RADIUS - MAGNET_CAPTURE)
    return stop + kotlin.math.sign(d) * MAGNET_RADIUS * sqrt(u)
}

/**
 * Three-stop slider. The thumb follows the finger exactly while dragging, the level
 * (title, colors) switches when the nearest stop changes, and on release the thumb
 * glides to the stop. Tapping anywhere on the track jumps to the nearest stop.
 */
@Composable
private fun PowerSlider(
    level: PowerLevel,
    pos: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    neon: List<Color>,
    locked: Boolean,
    trackColor: Color,
    onLevelChange: (PowerLevel) -> Unit,
) {
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val scope = rememberCoroutineScope()
    val stable = levelColors(PowerLevel.Stable).accent
    val balanced = levelColors(PowerLevel.Balanced).accent
    val surface = MaterialTheme.colorScheme.surface
    var dragging by remember { mutableStateOf(false) }
    var caught by remember { mutableStateOf(-1) }
    var pressed by remember { mutableStateOf(false) }
    // Liquid glass on: while held, the thumb swells into a lens that magnifies the track.
    val glassOn = com.wdtt.client.ui.components.surface.LocalGlassBackdrop.current != null
    val trackLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val trackGlass = remember(trackLayer) { com.wdtt.client.ui.components.surface.GlassBackdrop(trackLayer) }
    val lens by animateFloatAsState(
        if (glassOn && !locked && (pressed || dragging)) 1f else 0f,
        spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "lens",
    )
    LaunchedEffect(level, dragging) {
        if (!dragging) pos.magnetTo(level.ordinal / 2f)
    }
    val motion = rememberMotionScale()
    val t = rememberInfiniteTransition(label = "slider")
    val flow by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween((2400 / motion).toInt(), easing = LinearEasing)), label = "flow",
    )
    val halo by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "halo")
    val alpha by animateFloatAsState(if (locked) 0.45f else 1f, tween(300), label = "lock")
    var widthPx by remember { mutableFloatStateOf(1f) }
    val pad = with(LocalDensity.current) { 14.dp.toPx() }

    // Gesture handlers live across level changes (keyed on Unit), so a drag is never
    // cut off when the level switches under the finger; they read the latest values here.
    val currentLevel by rememberUpdatedState(level)
    val currentOnChange by rememberUpdatedState(onLevelChange)
    fun fracAt(x: Float) = ((x - pad) / (widthPx - 2 * pad)).coerceIn(0f, 1f)
    fun select(frac: Float) {
        val next = PowerLevel.fromIndex((frac * 2).roundToInt())
        if (next != currentLevel) {
            haptics.perform(com.wdtt.client.ui.motion.Haptic.Tick)
            currentOnChange(next)
        }
    }
    fun settle() {
        dragging = false
        val stop = (pos.value * 2).roundToInt() / 2f
        select(stop)
        scope.launch { pos.magnetTo(stop, onContact = { haptics.perform(com.wdtt.client.ui.motion.Haptic.Tick) }) }
    }

    BoxWithConstraints(
        Modifier
            .testTag("power-slider")
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .onGloballyPositioned { trackGlass.origin = it.positionInRoot() }
            .alpha(alpha)
            .semantics {
                stateDescription = level.title
                progressBarRangeInfo = ProgressBarRangeInfo(level.ordinal.toFloat(), 0f..2f, steps = 1)
                if (locked) disabled() else setProgress { v -> select(v / 2f); true }
            }
            .then(
                if (locked) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            },
                        ) { o ->
                            val f = fracAt(o.x)
                            select(f)
                            scope.launch {
                                pos.magnetTo((f * 2).roundToInt() / 2f, onContact = { haptics.perform(com.wdtt.client.ui.motion.Haptic.Tick) })
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { settle() },
                            onDragCancel = { settle() },
                        ) { change, _ ->
                            val f = fracAt(change.position.x)
                            val shown = magnet(f)
                            // A light tick when the thumb catches a stop.
                            val stop = (f * 2).roundToInt()
                            if (abs(f - stop / 2f) <= MAGNET_CAPTURE && caught != stop) {
                                caught = stop
                                haptics.perform(com.wdtt.client.ui.motion.Haptic.Tick)
                            } else if (abs(f - stop / 2f) > MAGNET_RADIUS) {
                                caught = -1
                            }
                            scope.launch { pos.snapTo(shown) }
                            select(f)
                        }
                    }
            ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val p = pos.value
            val g = (p * 2f).coerceIn(0f, 1f)
            val k = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
            val base = lerp(stable, balanced, g)
            val blended = neon.map { lerp(balanced, it, k) }
            val cy = size.height / 2f
            val trackH = 10.dp.toPx()
            val left = pad
            val right = size.width - pad
            val x = left + (right - left) * p
            val r = CornerRadius(trackH / 2f)
            val drawTrack: androidx.compose.ui.graphics.drawscope.DrawScope.(Boolean) -> Unit = { withStops ->
                drawRoundRect(trackColor, Offset(left, cy - trackH / 2f), Size(right - left, trackH), r)
                val fillBrush = if (k > 0f) {
                    val period = (right - left).coerceAtLeast(1f)
                    Brush.linearGradient(
                        blended + blended.first(),
                        start = Offset(left + flow * period, 0f),
                        end = Offset(left + flow * period + period, 0f),
                        tileMode = TileMode.Repeated,
                    )
                } else Brush.linearGradient(listOf(base, base))
                if (k > 0f) {
                    drawRoundRect(neon[0].copy(alpha = 0.35f * k), Offset(left, cy - trackH), Size(x - left, trackH * 2), CornerRadius(trackH))
                }
                drawRoundRect(fillBrush, Offset(left, cy - trackH / 2f), Size((x - left).coerceAtLeast(trackH), trackH), r)
                // Balanced: a faint light band running along the fill.
                val bal = (1f - abs(p - 0.5f) * 2f).coerceIn(0f, 1f)
                if (bal > 0.01f && x - left > trackH) {
                    val fillW = x - left
                    val bx = left + ((flow * 1.6f) % 1.6f - 0.3f) * fillW
                    drawRoundRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.38f * bal), Color.Transparent),
                            startX = bx - 28.dp.toPx(), endX = bx + 28.dp.toPx(),
                        ),
                        Offset(left, cy - trackH / 2f), Size(fillW, trackH), r,
                    )
                }
                if (withStops) for (i in 0..2) {
                    val sx = left + (right - left) * i / 2f
                    val passed = i / 2f <= p + 0.01f
                    drawCircle(if (passed) Color.White else trackColor.copy(alpha = 1f), 6.dp.toPx(), Offset(sx, cy), alpha = if (passed) 0.9f else 1f)
                    if (!passed) drawCircle(Color.Gray, 6.dp.toPx(), Offset(sx, cy), alpha = 0.35f)
                }
            }
            // While held with glass on, the track is also recorded for the lens to refract.
            // Explicit density and size: recording through this Canvas's own draw scope
            // would make it reference itself.
            val recDensity = androidx.compose.ui.unit.Density(density, fontScale)
            if (lens > 0.01f) {
                // The lens bends an opaque picture: the track over the card color.
                val cardBehind = lerp(surface, Neon.Night, k)
                trackLayer.record(recDensity, layoutDirection, androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())) {
                    drawRect(cardBehind)
                    // Without the stop dots: through the glass only the bar is seen.
                    drawTrack(false)
                }
            }
            drawTrack(true)
            if (k > 0f) {
                drawCircle(
                    Brush.radialGradient(listOf(neon[0].copy(alpha = 0.55f * k), neon[1].copy(alpha = 0.2f * k), Color.Transparent), Offset(x, cy), 32.dp.toPx() * halo),
                    32.dp.toPx() * halo, Offset(x, cy),
                )
            }
            val solid = 1f - lens
            if (solid > 0.01f) {
                drawCircle(Color.Black.copy(alpha = 0.12f * solid), 15.dp.toPx(), Offset(x, cy + 1.5.dp.toPx()))
                // The thumb itself takes the level color: blue → green, then a light core in a neon ring.
                drawCircle(lerp(base, Neon.NightText, k), 14.dp.toPx(), Offset(x, cy), alpha = solid)
                drawCircle(Color.White, 12.5.dp.toPx(), Offset(x, cy), alpha = 0.9f * (1f - k) * solid, style = Stroke(2.dp.toPx()))
                if (k > 0f) {
                    drawCircle(Brush.sweepGradient(blended + blended.first(), Offset(x, cy)), 12.5.dp.toPx(), Offset(x, cy), alpha = k * solid, style = Stroke(3.dp.toPx()))
                }
                drawCircle(Color.White, 4.dp.toPx(), Offset(x, cy), alpha = 0.85f * (1f - k) * solid)
            }
        }
        // Liquid glass lens: a capsule that bends the track along its rim (the fill wraps
        // around the edge, the background is squeezed into a bright band) and magnifies the middle.
        if (lens > 0.01f) {
            val dark = com.wdtt.client.ui.theme.isDarkSurface()
            val h = (26f + 16f * lens).dp
            val w = h * (1f + 0.62f * lens)
            val density = LocalDensity.current
            Box(
                Modifier
                    .offset {
                        val track = widthPx - 2 * pad
                        val cx = pad + track * pos.value
                        IntOffset((cx - w.toPx() / 2f).roundToInt(), ((constraints.maxHeight - h.toPx()) / 2f).roundToInt())
                    }
                    .size(w, h)
                    .shadow((6f * lens).dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f)),
            ) {
                com.wdtt.client.ui.components.surface.LiquidGlassPanel(
                    backdrop = trackGlass,
                    cornerRadius = h / 2,
                    tint = (if (dark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.10f)),
                    dark = dark,
                    // A narrow rim that bends steeply: right at the edge the glass reaches the
                    // track in the middle and smears its color along the rim; the middle stays flat.
                    bezel = h * 0.22f,
                    depth = h * 0.42f,
                    zoom = 0.04f,
                    fringe = 0.3f,
                    blur = 0.dp,
                    underlay = lerp(surface, Neon.Night, ((pos.value - 0.5f) * 2f).coerceIn(0f, 1f)),
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = lens.coerceIn(0f, 1f) },
                ) {}
            }
        }
    }
}
