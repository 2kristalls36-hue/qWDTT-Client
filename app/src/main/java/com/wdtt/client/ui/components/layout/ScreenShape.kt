package com.wdtt.client.ui.components.layout

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat

/**
 * The physical shape of this screen, as the system reports it: the radius of the bottom
 * corners (Android 12+), whether the system navigation is a gesture line (the tab bar may
 * then come down to the screen corners) and a round camera hole in the screen, if any.
 * Unknown parts are zero / null, and the interface keeps its usual layout for them.
 */
@Immutable
data class ScreenShape(
    /** Radius of the rounded bottom corners of the display; 0 — unknown or square. */
    val bottomCorner: Dp = 0.dp,
    /** Gesture navigation: no buttons at the bottom, only the thin handle. */
    val gestureNav: Boolean = false,
    /** Round camera hole in the display (punch hole), in window pixels; null — none or not round. */
    val cameraHole: Rect? = null,
) {
    companion object {
        val Unknown = ScreenShape()
    }
}

val LocalScreenShape = staticCompositionLocalOf { ScreenShape.Unknown }

/** For screenshot tests: a shape to use instead of the device's. */
internal var screenShapeOverride: ScreenShape? = null

/** Reads the shape from the window insets and follows changes (rotation, navigation mode). */
@Composable
fun rememberScreenShape(): ScreenShape {
    screenShapeOverride?.let { return it }
    val view = LocalView.current
    val density = LocalDensity.current
    var shape by remember { mutableStateOf(readScreenShape(view, density)) }
    DisposableEffect(view, density) {
        val listener = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val next = readScreenShape(v, density)
            if (next != shape) shape = next
        }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }
    return shape
}

private fun readScreenShape(view: View, density: Density): ScreenShape {
    val raw = view.rootWindowInsets ?: return ScreenShape.Unknown
    val insets = WindowInsetsCompat.toWindowInsetsCompat(raw, view)
    val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
    val tappableBottom = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
    // Gesture navigation: the bar inset is there, but nothing in it can be tapped.
    val gesture = navBottom > 0 && tappableBottom == 0

    val corner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val l = raw.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
        val r = raw.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
        // Both corners must be known; the smaller one decides (the bar has to fit both).
        if (l > 0 && r > 0) with(density) { minOf(l, r).toDp() } else 0.dp
    } else 0.dp

    // A punch hole: a small, nearly square cutout away from the screen edges' corners.
    val hole = insets.displayCutout?.boundingRects
        ?.firstOrNull { b ->
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            w > 0f && h > 0f && kotlin.math.abs(w - h) <= 0.25f * maxOf(w, h) &&
                with(density) { maxOf(w, h).toDp() } < 64.dp
        }
        ?.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }

    return ScreenShape(bottomCorner = corner, gestureNav = gesture, cameraHole = hole)
}
