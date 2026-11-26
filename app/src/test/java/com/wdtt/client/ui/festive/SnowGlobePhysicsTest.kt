package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SnowGlobePhysicsTest {
    private val dt = 1f / 60f

    private fun run(sim: SnowGlobePhysics, seconds: Float, degrees: Double = 0.0, shakeX: Float = 0f, shakeY: Float = 0f) {
        val a = Math.toRadians(degrees)
        repeat((seconds * 60).toInt()) {
            sim.step(dt, tiltX = Math.sin(a).toFloat(), tiltY = Math.cos(a).toFloat(), shakeX = shakeX, shakeY = shakeY)
        }
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

    @Test fun aTiltLetsTheSnowDriftOverButLeavesNoGaps() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        run(sim, 5f)
        fun centre() = (0 until sim.flakes).filter { sim.suspended[it] }.map { sim.x[it] }.average()
        val before = centre()
        run(sim, 30f, degrees = 30.0)
        val after = centre()
        val counts = spread(sim)
        println("30°: centre $before -> $after, ${counts.joinToString()}")
        assertTrue("it drifts the way the phone leans: $before -> $after", after - before > 0.05)
        assertTrue("still no bare part: ${counts.joinToString()}", counts.min() > 0.3 * counts.average())
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
            val turn = (i / 300) * 1.3
            sim.step(dt, x, 0.7f, v, Math.sin(turn).toFloat(), Math.cos(turn).toFloat(), shake, -shake)
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
        run(sim, 10f)
        // Held at a slight tilt too: it clings.
        run(sim, 10f, degrees = 12.0)
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

    @Test fun upsideDownTheCrustComesOffAndSettlesOnTheOtherWall() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        run(sim, 120f, degrees = 180.0)
        val up = (0 until sim.crustCount).count { sim.lying[it] && sim.y[it] < 0.5f }
        println("crust on the top wall: $up of ${sim.crustCount}, ${sim.describe()}")
        assertTrue("on the top wall now: $up", up > 0.9f * sim.crustCount)
        // The hanging snow went with it a little, but still fills the glass.
        val counts = spread(sim)
        assertTrue("no bare part: ${counts.joinToString()}", counts.min() > 0.3 * counts.average())
    }

    @Test fun turnedFarTheCrustSlidesDownTheGlass() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        fun crustX() = (0 until sim.crustCount).map { sim.x[it] }.average()
        val before = crustX()
        run(sim, 60f, degrees = -80.0)
        val after = crustX()
        println("crust on its side: $before -> $after, ${sim.describe()}")
        assertTrue("slid to the lower end: $after", after < 1.5)
        val moves = sim.moves
        run(sim, 5f, degrees = -80.0)
        assertTrue("and rests there: ${sim.moves - moves}", sim.moves - moves <= 3)
    }
}
