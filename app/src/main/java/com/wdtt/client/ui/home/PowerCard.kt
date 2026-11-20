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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
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
    val isMax = level == PowerLevel.Maximum
    val scheme = MaterialTheme.colorScheme
    val cardColor by animateColorAsState(if (isMax) Neon.Night else scheme.surface, tween(450), label = "card")
    val textMuted by animateColorAsState(if (isMax) Neon.NightMuted else scheme.onSurfaceVariant, tween(400), label = "t2")
    val textFaint by animateColorAsState(if (isMax) Neon.NightFaint else scheme.outline, tween(400), label = "t3")
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardColor)
            .then(if (isMax) Modifier.border(1.dp, colors.neon[1].copy(alpha = 0.45f), shape) else Modifier),
    ) {
        if (isMax) NeonAura(colors.neon)
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
                    color = levelColors(l).accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PowerSlider(level = level, colors = colors, locked = locked, trackColor = if (isMax) Neon.NightTrack else scheme.surfaceVariant, onLevelChange = onLevelChange)
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
private fun androidx.compose.foundation.layout.BoxScope.NeonAura(neon: List<Color>) {
    val motion = rememberMotionScale()
    val t = rememberInfiniteTransition(label = "aura")
    val angle by t.animateFloat(
        0f, 360f, infiniteRepeatable(tween((9000 / motion).toInt(), easing = LinearEasing)), label = "a",
    )
    Canvas(Modifier.matchParentSize().alpha(0.22f)) {
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
    colors: LevelColors,
    locked: Boolean,
    trackColor: Color,
    onLevelChange: (PowerLevel) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(level.ordinal / 2f) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(level) {
        if (!dragging) pos.animateTo(level.ordinal / 2f, spring(dampingRatio = 0.8f, stiffness = 400f))
    }
    val isMax = level == PowerLevel.Maximum
    val motion = rememberMotionScale()
    val t = rememberInfiniteTransition(label = "slider")
    val flow by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween((2400 / motion).toInt(), easing = LinearEasing)), label = "flow",
    )
    val halo by t.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "halo")
    val alpha by animateFloatAsState(if (locked) 0.45f else 1f, tween(300), label = "lock")
    val thumbFill = if (isMax) Neon.NightText else MaterialTheme.colorScheme.surface
    var widthPx by remember { mutableFloatStateOf(1f) }
    val pad = with(LocalDensity.current) { 14.dp.toPx() }

    fun fracAt(x: Float) = ((x - pad) / (widthPx - 2 * pad)).coerceIn(0f, 1f)
    fun select(frac: Float) {
        val next = PowerLevel.fromIndex((frac * 2).roundToInt())
        if (next != level) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLevelChange(next)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .alpha(alpha)
            .semantics {
                stateDescription = level.title
                progressBarRangeInfo = ProgressBarRangeInfo(level.ordinal.toFloat(), 0f..2f, steps = 1)
                if (locked) disabled() else setProgress { v -> select(v / 2f); true }
            }
            .then(
                if (locked) Modifier else Modifier
                    .pointerInput(level) {
                        detectTapGestures { o ->
                            val f = fracAt(o.x)
                            select(f)
                            scope.launch { pos.animateTo((f * 2).roundToInt() / 2f, spring(0.8f, 400f)) }
                        }
                    }
                    .pointerInput(level) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                val stop = (pos.value * 2).roundToInt() / 2f
                                select(stop)
                                scope.launch { pos.animateTo(stop, spring(0.8f, 400f)) }
                            },
                            onDragCancel = { dragging = false },
                        ) { change, _ ->
                            val f = fracAt(change.position.x)
                            scope.launch { pos.snapTo(f) }
                            select(f)
                        }
                    }
            ),
    ) {
        Canvas(Modifier.matchParentSize()) {
            widthPx = size.width
            val cy = size.height / 2f
            val trackH = 10.dp.toPx()
            val left = pad
            val right = size.width - pad
            val x = left + (right - left) * pos.value
            val r = CornerRadius(trackH / 2f)
            drawRoundRect(trackColor, Offset(left, cy - trackH / 2f), Size(right - left, trackH), r)
            val fillBrush = if (isMax) {
                val period = (right - left).coerceAtLeast(1f)
                Brush.linearGradient(
                    colors.neon + colors.neon.first(),
                    start = Offset(left + flow * period, 0f),
                    end = Offset(left + flow * period + period, 0f),
                    tileMode = TileMode.Repeated,
                )
            } else Brush.linearGradient(listOf(colors.accent, colors.accent))
            if (isMax) {
                drawRoundRect(colors.neon[0].copy(alpha = 0.35f), Offset(left, cy - trackH), Size(x - left, trackH * 2), CornerRadius(trackH))
            }
            drawRoundRect(fillBrush, Offset(left, cy - trackH / 2f), Size((x - left).coerceAtLeast(trackH), trackH), r)
            for (i in 0..2) {
                val sx = left + (right - left) * i / 2f
                val passed = i / 2f < pos.value - 0.01f
                drawCircle(if (passed) Color.White else trackColor.copy(alpha = 1f), 6.dp.toPx(), Offset(sx, cy), alpha = if (passed) 0.9f else 1f)
                if (!passed) drawCircle(Color.Gray, 6.dp.toPx(), Offset(sx, cy), alpha = 0.35f)
            }
            if (isMax) {
                drawCircle(
                    Brush.radialGradient(listOf(colors.neon[0].copy(alpha = 0.55f), colors.neon[1].copy(alpha = 0.2f), Color.Transparent), Offset(x, cy), 32.dp.toPx() * halo),
                    32.dp.toPx() * halo, Offset(x, cy),
                )
            }
            drawCircle(Color.Black.copy(alpha = 0.12f), 15.dp.toPx(), Offset(x, cy + 1.5.dp.toPx()))
            drawCircle(thumbFill, 14.dp.toPx(), Offset(x, cy))
            val ring = if (isMax) Brush.sweepGradient(colors.neon + colors.neon.first(), Offset(x, cy)) else Brush.linearGradient(listOf(colors.accent, colors.accent))
            drawCircle(ring, 12.5.dp.toPx(), Offset(x, cy), style = Stroke(3.dp.toPx()))
        }
    }
}
