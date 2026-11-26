package com.wdtt.client.ui.festive

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * How the snow inside the bar is seen through its glass. The glass is flat in the middle
 * and curves round at the rim, so the room inside reaches a little past the edge as drawn
 * ([depth] on every side), and what lies near the glass is seen squeezed into the curved
 * rim, about three times at the very edge: near the edges there is less room. The middle
 * is seen as it is, not magnified (the snow would rise into the tab labels). The rim is as
 * wide as the bezel of the bar's own glass, which bends the page behind it alike.
 *
 * Lengths in pixels: the bar as drawn is [width] by [height] with corners of [corner]; the
 * room the snow moves in is [roomWidth] by [roomHeight] with corners of [roomCorner].
 */
class GlassWarp(
    val width: Float,
    val height: Float,
    corner: Float,
    /** How far the room reaches past the drawn edge: the glass rim's depth. */
    val depth: Float,
    /** Width of the curved rim. */
    bezel: Float,
) {
    val roomWidth = width + 2f * depth
    val roomHeight = height + 2f * depth
    val roomCorner = corner + depth
    /** The rim as drawn (no wider than the glass makes it, see LiquidGlass). */
    private val band = min(bezel, min(width, height) / 2f * 0.7f).coerceAtLeast(1f)
    /** The room behind it: the rim and the hidden depth. */
    private val reach = band + depth
    private val seen = FloatArray(TABLE + 1)
    private val squeeze = FloatArray(TABLE + 1)

    init {
        // Seen at u from the drawn edge is what lies at s(u) from the glass:
        // s = u + depth · (1 − (1 − u/band)^P): at the edge the room comes in about
        // 1 + P·depth/band times as fast, at the inner end of the rim it is as it is.
        val steps = TABLE * 8
        var k = 0
        var lastU = 0f
        var lastS = 0f
        for (m in 0..steps) {
            val u = band * m / steps
            val s = u + depth * (1f - (1f - u / band).pow(P))
            while (k <= TABLE && reach * k / TABLE <= s) {
                val target = reach * k / TABLE
                val f = if (s > lastS) (target - lastS) / (s - lastS) else 0f
                seen[k] = lastU + (u - lastU) * f
                squeeze[k] = 1f / (1f + depth * P / band * (1f - seen[k] / band).pow(P - 1f))
                k++
            }
            lastU = u
            lastS = s
        }
        while (k <= TABLE) {
            seen[k] = band
            squeeze[k] = 1f
            k++
        }
    }

    /** Signed distance to the room's glass (negative inside), its outward normal in [out]. */
    private fun glass(px: Float, py: Float, out: FloatArray): Float {
        val hw = roomWidth / 2f
        val hh = roomHeight / 2f
        val r = min(roomCorner, min(hw, hh))
        val qx = abs(px - hw) - (hw - r)
        val qy = abs(py - hh) - (hh - r)
        val sx = if (px < hw) -1f else 1f
        val sy = if (py < hh) -1f else 1f
        val ox = max(qx, 0f)
        val oy = max(qy, 0f)
        val outside = sqrt(ox * ox + oy * oy)
        val d = outside + min(max(qx, qy), 0f) - r
        if (outside > 0f) {
            out[2] = sx * ox / outside
            out[3] = sy * oy / outside
        } else if (qx > qy) {
            out[2] = sx; out[3] = 0f
        } else {
            out[2] = 0f; out[3] = sy
        }
        return d
    }

    /**
     * Where the point ([px], [py]) of the room (pixels from its corner) is seen on the bar:
     * into [out] (0, 1); [out] (2, 3) is the outward normal of the nearest glass. Returns how
     * much the view is squeezed there across the rim (1 in the middle, about 1/3 at the
     * very edge).
     */
    fun map(px: Float, py: Float, out: FloatArray): Float {
        val s = max(0f, -glass(px, py, out))
        if (s >= reach) {
            out[0] = px - depth
            out[1] = py - depth
            return 1f
        }
        val f = s / reach * TABLE
        val i = f.toInt().coerceAtMost(TABLE - 1)
        val t = f - i
        val u = seen[i] + (seen[i + 1] - seen[i]) * t
        val q = squeeze[i] + (squeeze[i + 1] - squeeze[i]) * t
        // From the room's glass at s to the drawn edge at u: out along the normal.
        val shift = (s - depth) - u
        out[0] = px - depth + out[2] * shift
        out[1] = py - depth + out[3] * shift
        return q
    }

    private companion object {
        const val TABLE = 256
        /** How sharply the rim squeezes towards the very edge. */
        const val P = 4.5f
    }
}
