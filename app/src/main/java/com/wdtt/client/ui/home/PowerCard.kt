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
) {
    val colors = levelColors(level)
    val scheme = MaterialTheme.colorScheme
    // Thumb position 0..1 drives every color, so dragging blends them continuously:
    // green → blue on the first half, blue → neon night panel on the second.
    val pos = remember { Animatable(level.ordinal / 2f) }
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
                else Text(
                    l.title,
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
                    color = titleBlend,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                transitionSpec = {
                    (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 6 }) togetherWith
                        fadeOut(tween(140))
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
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val stable = levelColors(PowerLevel.Stable).accent
    val balanced = levelColors(PowerLevel.Balanced).accent
    val surface = MaterialTheme.colorScheme.surface
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(level, dragging) {
        if (!dragging) pos.animateTo(level.ordinal / 2f, spring(dampingRatio = 0.8f, stiffness = 400f))
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
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnChange(next)
        }
    }
    fun settle() {
        dragging = false
        val stop = (pos.value * 2).roundToInt() / 2f
        select(stop)
        scope.launch { pos.animateTo(stop, spring(0.8f, 400f)) }
    }

    BoxWithConstraints(
        Modifier
            .testTag("power-slider")
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .alpha(alpha)
            .semantics {
                stateDescription = level.title
                progressBarRangeInfo = ProgressBarRangeInfo(level.ordinal.toFloat(), 0f..2f, steps = 1)
                if (locked) disabled() else setProgress { v -> select(v / 2f); true }
            }
            .then(
                if (locked) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { o ->
                            val f = fracAt(o.x)
                            select(f)
                            scope.launch { pos.animateTo((f * 2).roundToInt() / 2f, spring(0.8f, 400f)) }
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { settle() },
                            onDragCancel = { settle() },
                        ) { change, _ ->
                            val f = fracAt(change.position.x)
                            scope.launch { pos.snapTo(f) }
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
            for (i in 0..2) {
                val sx = left + (right - left) * i / 2f
                val passed = i / 2f < p - 0.01f
                drawCircle(if (passed) Color.White else trackColor.copy(alpha = 1f), 6.dp.toPx(), Offset(sx, cy), alpha = if (passed) 0.9f else 1f)
                if (!passed) drawCircle(Color.Gray, 6.dp.toPx(), Offset(sx, cy), alpha = 0.35f)
            }
            if (k > 0f) {
                drawCircle(
                    Brush.radialGradient(listOf(neon[0].copy(alpha = 0.55f * k), neon[1].copy(alpha = 0.2f * k), Color.Transparent), Offset(x, cy), 32.dp.toPx() * halo),
                    32.dp.toPx() * halo, Offset(x, cy),
                )
            }
            drawCircle(Color.Black.copy(alpha = 0.12f), 15.dp.toPx(), Offset(x, cy + 1.5.dp.toPx()))
            drawCircle(lerp(surface, Neon.NightText, k), 14.dp.toPx(), Offset(x, cy))
            val ring = if (k > 0f) Brush.sweepGradient(blended + blended.first(), Offset(x, cy)) else Brush.linearGradient(listOf(base, base))
            drawCircle(ring, 12.5.dp.toPx(), Offset(x, cy), style = Stroke(3.dp.toPx()))
        }
    }
}
