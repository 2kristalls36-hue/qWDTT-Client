package com.wdtt.client.ui.components.layout

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate

/**
 * Tab glyphs drawn on a 24-unit grid. Each takes the tap animation progress 0..1 and is
 * at rest at both ends; the motion says what the tab is about:
 * power — pressed like the connect button and released; profiles — the cards fan out like
 * flipping through them; apps — one tile steps out of the grid, as an app leaving the tunnel.
 * The selected tab also gets a soft fill that grows in, so state reads by shape, not only color.
 */
/** Tap progress 0..1, selection 0..1 (soft fill grows in), tint, modifier. */
typealias TabGlyph = @Composable (Float, Float, Color, Modifier) -> Unit

object LiteTabIcons {
    val Power: TabGlyph = { p, sel, tint, m -> Canvas(m) { power(tabIconSettle(p), sel, tint) } }
    val Cards: TabGlyph = { p, sel, tint, m -> Canvas(m) { cards(tabIconSettle(p), sel, tint) } }
    val Tiles: TabGlyph = { p, sel, tint, m -> Canvas(m) { tiles(tabIconSettle(p), sel, tint) } }
}

private fun DrawScope.u(v: Float) = v * size.minDimension / 24f

private fun DrawScope.power(s: Float, sel: Float, tint: Color) {
    val stroke = Stroke(width = u(2.1f), cap = StrokeCap.Round)
    // Pressed: the ring shrinks a little and its gap widens, the stem pops up; then all settles.
    val r = u(8f) * (1f - 0.12f * s)
    val c = Offset(u(12f), u(13f))
    val gap = 64f + 46f * s
    if (sel > 0f) drawCircle(tint.copy(alpha = tint.alpha * 0.18f), r * sel, c)
    drawArc(
        tint, startAngle = -90f + gap / 2f, sweepAngle = 360f - gap, useCenter = false,
        topLeft = c - Offset(r, r), size = Size(r * 2, r * 2), style = stroke,
    )
    val lift = u(2.2f) * s
    drawLine(tint, Offset(u(12f), u(3f) - lift), Offset(u(12f), u(11.5f) - lift), strokeWidth = u(2.1f), cap = StrokeCap.Round)
}

private fun DrawScope.cards(s: Float, sel: Float, tint: Color) {
    val stroke = Stroke(width = u(1.9f))
    val corner = CornerRadius(u(2.6f))
    val w = u(14f)
    val h = u(11f)
    val front = Offset(u(3f) - u(1.6f) * s, u(9.5f) + u(1.2f) * s)
    val back = Offset(u(7f) + u(1.8f) * s, u(4f) - u(1.4f) * s)
    // Back card is hidden where the front one lies over it.
    val frontPath = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(front.x - u(1.5f), front.y - u(1.5f), front.x + w + u(1.5f), front.y + h + u(1.5f), CornerRadius(u(3.6f))))
    }
    clipPath(frontPath, ClipOp.Difference) {
        drawRoundRect(tint.copy(alpha = tint.alpha * 0.75f), back, Size(w, h), corner, style = stroke)
    }
    if (sel > 0f) drawRoundRect(tint.copy(alpha = tint.alpha * 0.2f * sel), front, Size(w, h), corner)
    drawRoundRect(tint, front, Size(w, h), corner, style = stroke)
    // Profile marks on the front card: a dot and a line.
    translate(front.x, front.y) {
        drawCircle(tint, u(1.9f), Offset(u(4.2f), u(5.5f)))
        drawLine(tint, Offset(u(7.6f), u(5.5f)), Offset(u(11f), u(5.5f)), strokeWidth = u(1.9f), cap = StrokeCap.Round)
    }
}

private fun DrawScope.tiles(s: Float, sel: Float, tint: Color) {
    val stroke = Stroke(width = u(1.9f))
    val corner = CornerRadius(u(2.2f))
    val t = u(7.5f)
    val a = u(3f)
    val b = u(13.5f)
    if (sel > 0f) {
        val fill = tint.copy(alpha = tint.alpha * 0.2f * sel)
        listOf(Offset(a, a), Offset(a, b), Offset(b, b)).forEach { drawRoundRect(fill, it, Size(t, t), corner) }
    }
    drawRoundRect(tint, Offset(a, a), Size(t, t), corner, style = stroke)
    drawRoundRect(tint, Offset(a, b), Size(t, t), corner, style = stroke)
    drawRoundRect(tint, Offset(b, b), Size(t, t), corner, style = stroke)
    // The top-right tile steps out of the grid and comes back.
    val out = Offset(u(2.6f), -u(2.6f)) * s
    drawRoundRect(tint, Offset(b, a) + out, Size(t, t), corner)
}
