package com.wdtt.client.ui.components.control

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wdtt.client.ui.components.surface.GlassBackdrop
import com.wdtt.client.ui.components.surface.LiquidGlassPanel
import com.wdtt.client.ui.components.surface.LocalGlassBackdrop
import com.wdtt.client.ui.motion.magnetTo
import com.wdtt.client.ui.theme.isDarkSurface
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Switch of the app. With liquid glass off it is the standard Material switch. With glass
 * on the thumb is a white pill that swells into a clear glass capsule while it is held or
 * travelling: the track under it bends along the rim, as in the reference. The thumb can
 * be dragged; on release the magnet pulls it to the nearest side. Vibration is left to
 * the caller, as with the Material switch.
 *
 * [onCheckedChange] null: display only (the row around it handles the tap); the lens
 * still shows while the thumb travels.
 */
@Composable
fun LiteSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalGlassBackdrop.current == null) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, modifier = modifier)
        return
    }
    GlassSwitch(checked, onCheckedChange, modifier, enabled)
}

@Composable
private fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    enabled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(checked, dragging) {
        if (!dragging) pos.magnetTo(if (checked) 1f else 0f)
    }
    val moving = pos.isRunning && !(pos.value == 0f || pos.value == 1f)
    val lens by animateFloatAsState(
        if (enabled && (pressed || dragging || moving)) 1f else 0f,
        spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "switchLens",
    )
    val onTrack = if (dark) Color(0xFF4ADE80) else Color(0xFF34C759)
    val offTrack = colors.outline.copy(alpha = if (dark) 0.45f else 0.30f)
    val surface = colors.surface
    val current by rememberUpdatedState(checked)
    val change by rememberUpdatedState(onCheckedChange)

    val layer = rememberGraphicsLayer()
    val glass = remember(layer) { GlassBackdrop(layer) }
    val w = 52.dp
    val h = 32.dp

    Box(
        modifier
            .size(w, h)
            .alpha(if (enabled) 1f else 0.45f)
            .onGloballyPositioned { glass.origin = it.positionInRoot() }
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "Включено" else "Выключено"
            }
            .then(
                if (onCheckedChange == null || !enabled) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                            onTap = { change?.invoke(!current) },
                        )
                    }
                    .pointerInput(Unit) {
                        val travel = size.width - size.height.toFloat()
                        var start = 0f
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true; start = pos.value; dx = 0f },
                            onDragEnd = {
                                val on = pos.value >= 0.5f
                                scope.launch {
                                    pos.magnetTo(if (on) 1f else 0f)
                                    dragging = false
                                }
                                if (on != current) change?.invoke(on)
                            },
                            onDragCancel = { dragging = false },
                        ) { c, amount ->
                            c.consume()
                            dx += amount
                            scope.launch { pos.snapTo((start + dx / travel).coerceIn(0f, 1f)) }
                        }
                    }
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val p = pos.value
            val trackColor = lerp(offTrack, onTrack, p)
            val drawTrack: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {
                drawRoundRect(trackColor, Offset.Zero, size, CornerRadius(size.height / 2f))
            }
            if (lens > 0.01f) {
                // The glass bends the track over the page color.
                layer.record(Density(density, fontScale), layoutDirection, IntSize(size.width.toInt(), size.height.toInt())) {
                    drawRect(surface)
                    drawTrack()
                }
            }
            drawTrack()
            // Solid white thumb, fading out as the lens takes over.
            val solid = 1f - lens
            if (solid > 0.01f) {
                val r = size.height / 2f - 2.dp.toPx()
                val cx = size.height / 2f + (size.width - size.height) * p
                drawCircle(Color.Black.copy(alpha = 0.18f * solid), r, Offset(cx, size.height / 2f + 1.dp.toPx()))
                drawCircle(Color.White.copy(alpha = solid), r, Offset(cx, size.height / 2f))
            }
        }
        if (lens > 0.01f) {
            // Glass capsule, larger than the thumb, over the track.
            val lh = (28f + 12f * lens).dp
            val lw = lh * (1f + 0.45f * lens)
            Box(
                Modifier
                    // Placed by hand: the capsule is larger than the switch, and a plain
                    // offset would be re-centred by the overflow handling.
                    .layout { measurable, constraints ->
                        val lwPx = lw.roundToPx()
                        val lhPx = lh.roundToPx()
                        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(lwPx, lhPx))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val travel = w.toPx() - h.toPx()
                            val cx = h.toPx() / 2f + travel * pos.value
                            placeable.place((cx - lwPx / 2f).roundToInt(), ((h.toPx() - lhPx) / 2f).roundToInt())
                        }
                    }
                    .shadow((5f * lens).dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f)),
            ) {
                LiquidGlassPanel(
                    backdrop = glass,
                    cornerRadius = lh / 2,
                    tint = Color.White.copy(alpha = if (dark) 0.05f else 0.12f),
                    dark = dark,
                    bezel = lh * 0.22f,
                    depth = lh * 0.42f,
                    zoom = 0.04f,
                    blur = 0.dp,
                    underlay = surface,
                    fringe = 0.3f,
                    modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = lens.coerceIn(0f, 1f) },
                ) {}
            }
        }
    }
}
