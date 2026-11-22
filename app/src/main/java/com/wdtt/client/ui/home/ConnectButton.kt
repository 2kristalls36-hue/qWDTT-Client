package com.wdtt.client.ui.home

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wdtt.client.lite.PowerLevel
import kotlin.math.cos
import kotlin.math.sin

enum class TunnelUiState { Off, Connecting, On }

/**
 * Round connect button. Flat level-colored core; around it, depending on state and level:
 * a progress arc while connecting, radiating rings when connected and, at the maximum level,
 * a rotating neon rim, a soft glow and orbiting sparks. Decorative speeds ease smoothly
 * between «off» and «on» instead of jumping.
 */
@Composable
fun ConnectButton(
    state: TunnelUiState,
    blend: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Real connection stages; null — the client does not report them, a timed arc is shown. */
    stages: com.wdtt.client.lite.StageView? = null,
) {
    // blend is the power thumb position 0..1: blue → green on the first half,
    // green → neon on the second. Every layer mixes by it, so dragging the slider
    // repaints the button continuously.
    val g = (blend * 2f).coerceIn(0f, 1f)
    val k = ((blend - 0.5f) * 2f).coerceIn(0f, 1f)
    val stableColors = levelColors(PowerLevel.Stable)
    val balancedColors = levelColors(PowerLevel.Balanced)
    val neon = levelColors(PowerLevel.Maximum).neon
    val accent = lerp(stableColors.accent, balancedColors.accent, g)
    val onAccent = lerp(stableColors.onAccent, balancedColors.onAccent, g)
    val live = state != TunnelUiState.Off
    val on = state == TunnelUiState.On
    val motion = rememberMotionScale()
    val reduced = motion < 1f

    // Shared clock for every rotating layer. Speed (turns per second) eases toward the target.
    val speed by animateFloatAsState(
        targetValue = if (live) 1f else 0.24f,
        animationSpec = tween(1400),
        label = "speed",
    )
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L) phase += (t - last) / 1e9f * speed * motion
                last = t
            }
        }
    }

    val surface = MaterialTheme.colorScheme.surface
    val onF by animateFloatAsState(if (on) 1f else 0f, tween(400), label = "on")
    val core = lerp(lerp(surface, Neon.Night, k), lerp(accent, Neon.Night, k), onF)
    val glyph = lerp(lerp(accent, Neon.NightFaint, k), lerp(onAccent, Neon.NightText, k), onF)
    val neonGlow = k * (0.3f + 0.6f * onF)
    // Balanced level: a restrained motion of its own, strongest exactly in the middle.
    val bal = (1f - kotlin.math.abs(blend - 0.5f) * 2f).coerceIn(0f, 1f)
    val sparks = k

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.93f else 1f,
        spring(dampingRatio = 0.45f, stiffness = 600f), label = "press",
    )

    // Connecting arc: fills once per attempt, while the whole arc turns.
    val arc = remember { Animatable(0f) }
    LaunchedEffect(state) {
        if (state == TunnelUiState.Connecting) {
            arc.snapTo(0f)
            arc.animateTo(0.92f, tween(6000, easing = LinearEasing))
        } else {
            arc.snapTo(0f)
        }
    }

    // Stage ring: segments fill up to the reported stage; the fill glides between stages.
    val stageFill by animateFloatAsState(
        targetValue = stages?.let { if (it.failed) it.index + 1f else it.index.toFloat() } ?: 0f,
        animationSpec = tween(650),
        label = "stageFill",
    )

    val ripple = rememberInfiniteTransition(label = "ripple")
    val ripplePhase by ripple.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2700, easing = LinearEasing), RepeatMode.Restart), label = "rp",
    )

    val label = when (state) {
        TunnelUiState.Off -> "Подключиться"
        TunnelUiState.Connecting -> "Подключение"
        TunnelUiState.On -> "Отключиться"
    }

    Box(modifier.size(236.dp), contentAlignment = Alignment.Center) {
        // Radiating rings, three staggered.
        if (on && !reduced) {
            Canvas(Modifier.size(236.dp)) {
                for (i in 0 until 3) {
                    val p = (ripplePhase + i / 3f) % 1f
                    val r = size.minDimension / 2f * (0.71f + 0.29f * p)
                    drawCircle(
                        color = lerp(accent, neon[1], k),
                        radius = r,
                        alpha = 0.7f * (1f - p),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        // Balanced: soft breathing halo while connected.
        if (bal > 0f && onF > 0f && !reduced) {
            Canvas(Modifier.size(236.dp)) {
                val breath = 0.5f + 0.5f * sin(phase * 2.4f)
                val r = size.minDimension / 2f
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Transparent, 0.6f to Color.Transparent,
                        0.72f to accent.copy(alpha = 0.22f * bal * onF * (0.55f + 0.45f * breath)),
                        1f to Color.Transparent,
                        center = center, radius = r,
                    ),
                    radius = r,
                )
            }
        }
        // Neon glow behind the rim: a wide rotating sweep ring faded in and out by a radial
        // mask. Drawn, not blurred, so nothing is clipped to a square on any device.
        if (neonGlow > 0f) {
            Canvas(
                Modifier
                    .size(236.dp)
                    .alpha(neonGlow)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
            ) {
                val r = size.minDimension / 2f
                rotate(phase * 120f) {
                    drawCircle(Brush.sweepGradient(neon + neon.first()), radius = r * 0.83f, style = Stroke(width = r * 0.34f))
                }
                drawCircle(
                    Brush.radialGradient(
                        0f to Color.Transparent, 0.62f to Color.Transparent,
                        0.8f to Color.Black, 1f to Color.Transparent,
                        center = center, radius = r,
                    ),
                    radius = r,
                    blendMode = BlendMode.DstIn,
                )
            }
        }
        // Track ring: soft level tint, or a rotating neon rim at the maximum.
        Canvas(Modifier.size(196.dp)) {
            val w = 7.dp.toPx()
            val r = size.minDimension / 2f - w / 2f
            if (k < 1f) drawCircle(color = accent, radius = r, alpha = 0.16f * (1f - k), style = Stroke(width = w))
            // Balanced: one soft highlight gliding slowly along the ring.
            if (bal > 0f) {
                rotate(phase * 75f) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent, 0.72f to Color.Transparent,
                            0.97f to accent, 1f to Color.Transparent,
                        ),
                        radius = r,
                        alpha = bal * (0.35f + 0.4f * onF),
                        style = Stroke(width = w, cap = StrokeCap.Round),
                    )
                }
            }
            if (k > 0f) {
                rotate(phase * 120f) {
                    drawCircle(
                        brush = Brush.sweepGradient(neon + neon.first()),
                        radius = r,
                        alpha = k * (0.7f + 0.3f * onF),
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            if (stages != null && state == TunnelUiState.Connecting) {
                // One segment per stage: done ones full, the current one breathing and
                // filling, pending ones faint, a failed one red.
                val n = stages.total.coerceAtLeast(1)
                val gap = if (n > 1) 6f else 0f
                val seg = 360f / n
                val pulse = 0.5f + 0.5f * sin(phase * 7f)
                val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                val color = lerp(accent, Neon.NightText, k)
                for (i in 0 until n) {
                    val start = -90f + i * seg + gap / 2f
                    val sweep = seg - gap
                    drawArc(color.copy(alpha = 0.16f), start, sweep, false, style = stroke)
                    val fill = (stageFill - i).coerceIn(0f, 1f)
                    val isFailed = stages.failed && i == stages.index
                    when {
                        isFailed -> drawArc(Color(0xFFEF4444), start, sweep, false, style = stroke)
                        fill > 0f -> drawArc(color, start, sweep * fill, false, style = stroke)
                    }
                    if (!stages.failed && i == stages.index && fill < 1f) {
                        drawArc(color.copy(alpha = 0.25f + 0.45f * pulse), start, sweep, false, style = stroke)
                    }
                }
            } else if (arc.value > 0f) {
                rotate(-90f + phase * 360f * 0.6f) {
                    drawArc(
                        color = lerp(accent, Neon.NightText, k),
                        startAngle = 0f,
                        sweepAngle = 360f * arc.value,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        // Orbiting sparks, maximum level only.
        if (sparks > 0f) {
            Canvas(Modifier.size(208.dp).alpha(sparks)) {
                val outer = size.minDimension / 2f
                val inner = outer - 12.dp.toPx()
                val c = Offset(size.width / 2f, size.height / 2f)
                val spots = listOf(
                    Triple(outer, 0f, 0), Triple(outer, 120f, 2), Triple(outer, 240f, 0),
                    Triple(inner, 90f, 1), Triple(inner, 270f, 3),
                )
                spots.forEachIndexed { i, (radius, start, colorIndex) ->
                    val dir = if (radius == inner) -1f else 1f
                    val deg = start + dir * phase * 360f / 6.6f
                    val rad = Math.toRadians(deg.toDouble())
                    val twinkle = 0.5f + 0.5f * sin((phase * (2.2f + i * 0.4f) + i) * 2f * Math.PI.toFloat())
                    val base = if (i == 1) 2.5.dp.toPx() else 3.5.dp.toPx()
                    val p = c + Offset((radius * cos(rad)).toFloat(), (radius * sin(rad)).toFloat())
                    val color = if (radius == inner) Color.White else neon[colorIndex]
                    drawCircle(color.copy(alpha = 0.35f), radius = base * (1.4f + 1.6f * twinkle), center = p)
                    drawCircle(color, radius = base * (0.6f + 0.9f * twinkle), center = p, alpha = 0.45f + 0.55f * twinkle)
                }
            }
        }
        // Core.
        Box(
            Modifier
                .size(156.dp)
                .scale(press)
                .shadow((14f * onF * (1f - k)).dp, CircleShape, ambientColor = accent, spotColor = accent)
                .background(core, CircleShape)
                .border(2.dp, accent.copy(alpha = 0.35f * (1f - onF) * (1f - k)), CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            val glow = k > 0.5f && on
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = glyph,
                    modifier = Modifier
                        .size(46.dp)
                        .alpha(if (state == TunnelUiState.Connecting) 0.55f + 0.45f * ((sin(phase * 9f) + 1f) / 2f) else 1f),
                )
                Text(
                    label,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = glyph,
                        shadow = if (glow) Shadow(neon[1].copy(alpha = k), blurRadius = 16f) else null,
                    ),
                )
            }
        }
    }
}
