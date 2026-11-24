package com.wdtt.client.ui.components.surface

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.wdtt.client.ui.components.layout.LocalScreenShape
import com.wdtt.client.ui.theme.isDarkSurface
import kotlin.math.roundToInt

/**
 * A thin glass ring around a round camera hole: a small round lens centred on the
 * camera whose rim bends the interface passing under it. The middle of the lens lies
 * under the camera itself, so only the ring shows. Liquid glass only.
 */
@Composable
fun CameraRing() {
    val hole = LocalScreenShape.current.cameraHole ?: return
    val backdrop = LocalGlassBackdrop.current ?: return
    if (!LiquidGlass.supported) return
    val density = LocalDensity.current
    val ring = 3.5.dp
    val radiusPx = maxOf(hole.width, hole.height) / 2f + with(density) { ring.toPx() }
    val diameter = with(density) { (radiusPx * 2f).toDp() }
    Box(
        Modifier
            .offset { IntOffset((hole.center.x - radiusPx).roundToInt(), (hole.center.y - radiusPx).roundToInt()) }
            .size(diameter),
    ) {
        LiquidGlassPanel(
            backdrop = backdrop,
            cornerRadius = diameter / 2,
            tint = Color.White.copy(alpha = 0.04f),
            dark = isDarkSurface(),
            // The whole visible ring is rim: it bends steeply, like the edge of a lens.
            bezel = ring,
            depth = 1.8.dp,
            steepRim = true,
            zoom = 0f,
            blur = 0.dp,
            fringe = 0.5f,
            dispersion = 0.25f,
            modifier = Modifier.fillMaxSize(),
        ) {}
    }
}
