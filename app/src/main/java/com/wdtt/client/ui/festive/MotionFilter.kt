package com.wdtt.client.ui.festive

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot

/**
 * Turns raw accelerometer readings (device axes, m/s²) into what the snow globe needs:
 * the direction of gravity on the screen and the phone's own acceleration.
 *
 * A phone in the hand never keeps still: it trembles (a fraction of a degree, 8–12 times
 * a second) and drifts by a degree or two. Fed straight into the snow, that kept slopes at
 * the edge of giving way and the drift twitching. So:
 * - gravity is smoothed (about a third of a second) and passed on only when the tilt has
 *   moved on by more than [TILT_STEP] — the tremor and the slow wander stay below it;
 * - the phone's own acceleration below [SHAKE_FLOOR] (the hand's tremor, walking) is
 *   dropped, and above it only the excess counts, so a real shake still does.
 */
class MotionFilter {
    private val g = floatArrayOf(0f, 9.81f, 0f)
    /** A quick estimate of gravity that keeps up with a hand turning the phone. */
    private val quick = floatArrayOf(0f, 9.81f, 0f)
    private var primed = false

    /** Direction of gravity on the screen (x right, y down), unit length when upright. */
    var tiltX = 0f
        private set
    var tiltY = 1f
        private set

    /** The phone's own acceleration on the screen, m/s², with the hand's tremor removed. */
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set

    /**
     * Takes one reading [ax], [ay], [az] taken [dt] seconds after the last. Returns
     * whether the globe should wake: a shake, or the tilt passed on has changed.
     */
    fun feed(ax: Float, ay: Float, az: Float, dt: Float): Boolean {
        if (!primed) {
            g[0] = ax; g[1] = ay; g[2] = az
            quick[0] = ax; quick[1] = ay; quick[2] = az
            primed = true
        } else {
            val k = 1f - exp(-dt.coerceIn(0f, 0.2f) / GRAVITY_SMOOTHING)
            val q = 1f - exp(-dt.coerceIn(0f, 0.2f) / QUICK_SMOOTHING)
            for (a in 0..2) {
                val v = when (a) { 0 -> ax; 1 -> ay; else -> az }
                g[a] += (v - g[a]) * k
                quick[a] += (v - quick[a]) * q
            }
        }
        // Device axes to the screen: x the same, y up on the device is down on the screen.
        val sx = -g[0] / 9.81f
        val sy = g[1] / 9.81f
        var moved = false
        if (hypot(sx, sy) < 0.15f) {
            // Lying flat: no direction on the screen; keep what was passed on.
        } else {
            val now = atan2(sx, sy)
            val was = atan2(tiltX, tiltY)
            var d = now - was
            if (d > Math.PI) d -= (2 * Math.PI).toFloat()
            if (d < -Math.PI) d += (2 * Math.PI).toFloat()
            if (abs(d) > TILT_STEP || abs(hypot(sx, sy) - hypot(tiltX, tiltY)) > 0.2f) {
                tiltX = sx
                tiltY = sy
                moved = true
            }
        }
        // The phone's own acceleration: against the quick gravity, so turning the phone
        // (which only changes the direction of gravity) is not taken for a shake. A shake
        // also changes the length of the reading; a turn does not.
        val lx = ax - quick[0]
        val ly = ay - quick[1]
        val stretch = abs(kotlin.math.sqrt(ax * ax + ay * ay + az * az) - 9.81f)
        val m = hypot(lx, ly)
        if (m > SHAKE_FLOOR && stretch > SHAKE_FLOOR * 0.5f) {
            val keep = (m - SHAKE_FLOOR) / m
            // On the screen: x the same, y flipped.
            shakeX = lx * keep
            shakeY = -ly * keep
        } else {
            shakeX = 0f
            shakeY = 0f
        }
        return moved || shakeX != 0f || shakeY != 0f
    }

    /** No readings for now (the app went to the background). */
    fun pause() {
        shakeX = 0f
        shakeY = 0f
    }

    companion object {
        /** Time constant of the gravity smoothing, seconds. */
        const val GRAVITY_SMOOTHING = 0.3f
        /** Time constant of the quick gravity estimate, seconds. */
        const val QUICK_SMOOTHING = 0.06f
        /** Tilt changes passed on in steps of this, radians (about 2.3°). */
        const val TILT_STEP = 0.04f
        /** The hand's own movements stay under this, m/s². */
        const val SHAKE_FLOOR = 1.4f
    }
}
