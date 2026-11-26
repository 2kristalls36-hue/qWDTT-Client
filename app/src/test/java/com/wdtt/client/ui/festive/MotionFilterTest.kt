package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class MotionFilterTest {
    private val g = 9.81f

    /** Accelerometer of a phone turned [deg] in its plane, leaning back 35°. */
    private fun reading(deg: Double, extraX: Float = 0f, extraY: Float = 0f): FloatArray {
        val a = Math.toRadians(deg)
        val b = Math.toRadians(35.0)
        return floatArrayOf(
            (-g * sin(a) * cos(b)).toFloat() + extraX,
            (g * cos(a) * cos(b)).toFloat() + extraY,
            (g * sin(b)).toFloat(),
        )
    }

    private fun angle(f: MotionFilter) = Math.toDegrees(atan2(f.tiltX, f.tiltY).toDouble())

    @Test fun theHandsTremorMovesNothing() {
        val f = MotionFilter()
        val rnd = java.util.Random(3)
        var woke = 0
        repeat(50 * 10) { k ->
            val t = k * 0.02
            val tremor = 0.3 * sin(2 * PI * 9.3 * t) + 0.4 * sin(2 * PI * 0.4 * t)
            val r = reading(tremor, (rnd.nextGaussian() * 0.25).toFloat(), (rnd.nextGaussian() * 0.25).toFloat())
            if (f.feed(r[0], r[1], r[2], 0.02f) && k > 10) woke++
        }
        assertEquals("woken by the tremor", 0, woke)
        assertEquals(0f, f.shakeX, 0f)
        assertEquals(0.0, angle(f), 1.0)
    }

    @Test fun aTurnIsNotTakenForAShake() {
        val f = MotionFilter()
        var shook = 0
        // Turned 40° over half a second, as a hand does.
        repeat(50 * 2) { k ->
            val deg = (k * 0.02 / 0.5).coerceAtMost(1.0) * 40.0
            val r = reading(deg)
            f.feed(r[0], r[1], r[2], 0.02f)
            if (hypot(f.shakeX, f.shakeY) > 0f) shook++
        }
        assertEquals("a turn read as a shake", 0, shook)
        assertEquals(40.0, angle(f), 3.0)
    }

    @Test fun aShakeIsAShake() {
        val f = MotionFilter()
        var strongest = 0f
        repeat(50) { k ->
            val push = if ((k / 5) % 2 == 0) 14f else -14f
            val r = reading(0.0, push, push * 0.6f)
            f.feed(r[0], r[1], r[2], 0.02f)
            strongest = maxOf(strongest, hypot(f.shakeX, f.shakeY))
        }
        assertTrue("felt the shake: $strongest", strongest > 8f)
    }
}
