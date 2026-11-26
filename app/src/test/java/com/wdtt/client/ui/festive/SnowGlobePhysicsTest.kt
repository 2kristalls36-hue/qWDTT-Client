package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SnowGlobePhysicsTest {
    private val dt = 1f / 60f

    private fun run(sim: SnowGlobePhysics, seconds: Float, shakeX: Float = 0f, shakeY: Float = 0f) {
        repeat((seconds * 60).toInt()) { sim.step(dt, shakeX = shakeX, shakeY = shakeY) }
    }

    /** Hanging flakes in each of [cols] x 2 parts of the glass's straight middle. */
    private fun spread(sim: SnowGlobePhysics, cols: Int = 6): IntArray {
        val counts = IntArray(cols * 2)
        val x0 = sim.corner
        val w = sim.aspect - 2 * sim.corner
        for (i in 0 until sim.flakes) {
            if (!sim.suspended[i] || sim.lying[i]) continue
            val c = ((sim.x[i] - x0) / w * cols).toInt()
            if (c < 0 || c >= cols) continue
            val r = if (sim.y[i] < 0.5f) 0 else 1
            counts[r * cols + c]++
        }
        return counts
    }

    private fun meanHangingSpeed(sim: SnowGlobePhysics, before: FloatArray): Float {
        var sum = 0.0
        var n = 0
        for (i in 0 until sim.flakes) {
            if (!sim.suspended[i]) continue
            sum += hypot((sim.x[i] - before[2 * i]).toDouble(), (sim.y[i] - before[2 * i + 1]).toDouble()) / dt
            n++
        }
        return (sum / n).toFloat()
    }

    private fun positions(sim: SnowGlobePhysics) = FloatArray(sim.flakes * 2) { if (it % 2 == 0) sim.x[it / 2] else sim.y[it / 2] }

    @Test fun theSnowHangsAllThroughTheGlassWithNoGaps() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        run(sim, 30f)
        val counts = spread(sim)
        val mean = counts.average()
        println("upright: ${counts.joinToString()}")
        assertTrue("no part of the glass is bare: ${counts.joinToString()}", counts.min() > 0.5 * mean)
        assertEquals("the hanging snow never settles", sim.flakes - sim.crustCount, (0 until sim.flakes).count { sim.suspended[it] && !sim.lying[it] })
    }

    @Test fun aQuickMoveWhirlsItUpAndItCalmsDownAgain() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        run(sim, 5f)
        var p = positions(sim)
        sim.step(dt)
        val calm = meanHangingSpeed(sim, p)
        repeat(30) { sim.step(dt, shakeX = 14f, shakeY = -10f) }
        p = positions(sim)
        sim.step(dt)
        val stirred = meanHangingSpeed(sim, p)
        run(sim, 15f)
        p = positions(sim)
        sim.step(dt)
        val after = meanHangingSpeed(sim, p)
        println("speeds: calm $calm, stirred $stirred, 15 s after $after")
        assertTrue("whirled up: $stirred vs $calm", stirred > 5f * calm)
        assertTrue("calm again: $after vs $calm", after < 2.5f * calm)
        assertTrue(sim.consistent())
    }

    @Test fun theDropletDragsTheSnowAlong() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        run(sim, 2f)
        val near = (0 until sim.flakes).filter { sim.suspended[it] && sim.x[it] in 1.0f..2.5f && sim.y[it] in 0.3f..0.7f }
        val before = near.map { sim.x[it] }.average()
        var x = 0.6f
        repeat(40) {
            x += 6f * dt
            sim.step(dt, x, 0.7f, 6f)
        }
        run(sim, 1f)
        val after = near.map { sim.x[it] }.average()
        println("snow behind the droplet: $before -> $after")
        assertTrue("carried along after it: $before -> $after", after - before > 0.05)
        assertTrue("the liquid moves behind it", sim.liquidAt(x - 0.8f, 0.5f) > 0.05f)
    }

    @Test fun noFlakeIsEverLostOrMadeAndEachStaysInTheGlass() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        var x = 0.5f
        repeat(1500) { i ->
            val v = if ((i / 120) % 2 == 0) 4f else -4f
            x = (x + v * dt).coerceIn(0.5f, 5f)
            val shake = if (i % 400 in 0..25) 14f else 0f
            sim.step(dt, x, 0.7f, v, shake, -shake)
            if (i % 100 == 0) assertTrue("step $i: ${sim.describe()}", sim.consistent())
        }
        for (i in 0 until sim.flakes) {
            assertTrue("x of $i: ${sim.x[i]}", sim.x[i] in 0f..sim.aspect)
            assertTrue("y of $i: ${sim.y[i]} lying ${sim.lying[i]}", sim.y[i] in 0f..1f)
        }
    }

    @Test fun theCrustLiesStillOnTheGlass() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        val moves = sim.moves
        run(sim, 20f)
        // Only the odd loose grain at the edge of the top row settles a step lower.
        assertTrue("the crust stays put: ${sim.moves - moves}", sim.moves - moves <= 30)
        assertEquals("all of it on the glass", sim.crustCount, (0 until sim.crustCount).count { sim.lying[it] && sim.y[it] > 0.85f })
        assertEquals(0, sim.awakeNow())
    }

    @Test fun theQuietSnowIsWorkedOutAtHalfTheFrames() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        run(sim, 3f)
        assertTrue("only drifting: not lively", !sim.lively)
        sim.step(dt, shakeX = 14f, shakeY = 10f)
        assertTrue("shaken: lively", sim.lively)
    }

    @Test fun aShakeLiftsTheCrustAndItSettlesOnTheBottomAgain() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        run(sim, 1f)
        repeat(36) { sim.step(dt, shakeX = if ((it / 6) % 2 == 0) 16f else -16f, shakeY = if ((it / 6) % 2 == 0) 10f else -10f) }
        val lifted = (0 until sim.crustCount).count { !sim.lying[it] }
        run(sim, 150f)
        val back = (0 until sim.crustCount).count { sim.lying[it] && sim.y[it] > 0.75f }
        println("shaken: lifted $lifted, back on the bottom $back of ${sim.crustCount}, ${sim.describe()}")
        assertTrue("the shake lifted some of the crust: $lifted", lifted > 0.05f * sim.crustCount)
        assertTrue("it settled on the bottom again: $back", back > 0.95f * sim.crustCount)
        assertTrue(sim.consistent())
        val moves = sim.moves
        run(sim, 5f)
        assertTrue("and rests there: ${sim.moves - moves}", sim.moves - moves <= 3)
    }
}
