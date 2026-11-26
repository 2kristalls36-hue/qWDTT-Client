package com.wdtt.client.ui.festive

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Turns raw accelerometer readings (device axes, m/s²) into what the snow globe needs: the
 * phone's own acceleration on the screen, a shake. How the phone is held plays no part.
 *
 * The reading holds gravity too; it is taken out against a quick estimate of it that keeps
 * up with a hand turning the phone, so a turn is not taken for a shake. The phone's own
 * acceleration below [SHAKE_FLOOR] (the hand's tremor, walking) is dropped, and above it
 * only the excess counts, so a real shake still does.
 */
class MotionFilter {
    /** A quick estimate of gravity that keeps up with a hand turning the phone. */
    private val quick = floatArrayOf(0f, 9.81f, 0f)
    private var primed = false

    /** The phone's own acceleration on the screen, m/s², with the hand's tremor removed. */
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set

    /**
     * Takes one reading [ax], [ay], [az] taken [dt] seconds after the last. Returns
     * whether the globe should wake: a shake.
     */
    fun feed(ax: Float, ay: Float, az: Float, dt: Float): Boolean {
        if (!primed) {
            quick[0] = ax; quick[1] = ay; quick[2] = az
            primed = true
        } else {
            val q = 1f - exp(-dt.coerceIn(0f, 0.2f) / QUICK_SMOOTHING)
            quick[0] += (ax - quick[0]) * q
            quick[1] += (ay - quick[1]) * q
            quick[2] += (az - quick[2]) * q
        }
        // The phone's own acceleration: against the quick gravity, so turning the phone
        // (which only changes the direction of gravity) is not taken for a shake. A shake
        // also changes the length of the reading; a turn does not.
        val lx = ax - quick[0]
        val ly = ay - quick[1]
        val stretch = abs(sqrt(ax * ax + ay * ay + az * az) - 9.81f)
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
        return shakeX != 0f || shakeY != 0f
    }

    /** No readings for now (the app went to the background). */
    fun pause() {
        shakeX = 0f
        shakeY = 0f
    }

    companion object {
        /** Time constant of the quick gravity estimate, seconds. */
        const val QUICK_SMOOTHING = 0.06f
        /** The hand's own movements stay under this, m/s². */
        const val SHAKE_FLOOR = 1.4f
    }
}
