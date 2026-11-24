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
    var shape by remember { mutableStateOf(ScreenShape.Unknown) }
    // The insets are only known once the window is attached and laid out: read right
    // away (the effect runs after attach) and again after every layout pass, so the shape
    // is never left unknown on a screen that does not change size afterwards.
    DisposableEffect(view, density) {
        fun refresh() {
            val next = readScreenShape(view, density)
            if (next != shape) shape = next
        }
        refresh()
        view.post { refresh() }
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener { refresh() }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return shape
}

private fun readScreenShape(view: View, density: Density): ScreenShape {
    val raw = view.rootWindowInsets ?: return ScreenShape.Unknown
    val insets = WindowInsetsCompat.toWindowInsetsCompat(raw, view)
    val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
    val tappableBottom = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
    // Gesture navigation: nothing at the bottom can be tapped (some firmware also hides
    // the gesture handle, leaving no navigation inset at all).
    val gesture = tappableBottom == 0

    // Corners: the system API (Android 12+); firmware that leaves it empty usually still
    // has the radius in the system resources, the same values the system UI draws with.
    val apiCorner = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val l = raw.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
        val r = raw.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0
        if (l > 0 && r > 0) minOf(l, r) else 0
    } else 0
    val resCorner = if (apiCorner > 0) 0 else systemCornerRadiusPx(view)
    val cornerPx = if (apiCorner > 0) apiCorner else resCorner
    val corner = with(density) { cornerPx.toDp() }

    val cutout = insets.displayCutout
    // The exact cutout outline (Android 12+) is better than the bounding rects, which some
    // firmware pads; a round hole has a square outline.
    val pathBounds: android.graphics.RectF? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        raw.displayCutout?.cutoutPath?.let { path -> android.graphics.RectF().also { path.computeBounds(it, true) } }
    } else null
    val rects = cutout?.boundingRects.orEmpty()
    val screenW = view.rootView.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels
    fun roundHole(l: Float, t: Float, r: Float, b: Float): Rect? {
        val w = r - l
        val h = b - t
        if (w <= 0f || h <= 0f) return null
        // A hole, not a notch: small, about as wide as tall, not spanning the screen top.
        if (maxOf(w, h) / minOf(w, h) > 1.6f) return null
        if (with(density) { minOf(w, h).toDp() } > 64.dp || w > screenW * 0.3f) return null
        val d = minOf(w, h)
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        return Rect(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f)
    }
    val hole = pathBounds?.let { roundHole(it.left, it.top, it.right, it.bottom) }
        ?: rects.firstNotNullOfOrNull { roundHole(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }

    val shape = ScreenShape(bottomCorner = corner, gestureNav = gesture, cameraHole = hole)
    // Once per distinct reading: what the phone reported, for the connection log.
    val report = "[ЭКРАН] углы: API ${apiCorner}px, ресурсы ${resCorner}px → ${corner.value.toInt()}dp; " +
        "навигация: ${if (gesture) "жесты" else "кнопки"} (снизу ${navBottom}px, нажимаемые ${tappableBottom}px); " +
        "вырез: ${rects.joinToString { "${it.left},${it.top}-${it.right},${it.bottom}" }.ifEmpty { "нет" }}" +
        (pathBounds?.let { "; контур ${it.left.toInt()},${it.top.toInt()}-${it.right.toInt()},${it.bottom.toInt()}" } ?: "") +
        "; камера: ${if (hole != null) "круглая" else "нет"}"
    if (report != lastReport) {
        lastReport = report
        runCatching { com.wdtt.client.lite.RawLog.add(report) }
    }
    return shape
}

@Volatile private var lastReport: String? = null

/** Radius of the bottom display corners from the system resources (0 when not set). */
private fun systemCornerRadiusPx(view: View): Int {
    val res = android.content.res.Resources.getSystem()
    for (name in listOf("rounded_corner_radius_bottom", "rounded_corner_radius")) {
        val id = res.getIdentifier(name, "dimen", "android")
        if (id != 0) {
            val px = runCatching { res.getDimensionPixelSize(id) }.getOrDefault(0)
            if (px > 0) return px
        }
    }
    return 0
}
