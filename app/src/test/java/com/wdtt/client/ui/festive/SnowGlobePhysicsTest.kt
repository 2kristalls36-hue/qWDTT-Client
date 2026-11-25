package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnowGlobePhysicsTest {
    private val dt = 1f / 60f

    private fun settle(sim: SnowGlobePhysics, tiltX: Float = 0f, tiltY: Float = 1f, maxSteps: Int = 60 * 120): Int {
        var steps = 0
        while (sim.step(dt, tiltX = tiltX, tiltY = tiltY) && steps < maxSteps) steps++
        return steps
    }

    private fun centreOfSnow(sim: SnowGlobePhysics): Float {
        var m = 0f
        var mx = 0f
        for (c in 0 until sim.columns) {
            m += sim.bottomDepth(c)
            mx += sim.bottomDepth(c) * (c + 0.5f) * sim.columnWidth
        }
        return mx / m
    }

    @Test fun theShakenSnowSettlesSlowlyAndTheGlobeFallsAsleep() {
        val sim = SnowGlobePhysics(5.5f)
        sim.scatter()
        val steps = settle(sim)
        // Slow: still falling after three seconds, all down within two minutes.
        assertTrue("settles slowly but surely: $steps steps", steps in 180 until 60 * 120)
        assertEquals(0, sim.floating)
        assertFalse(sim.step(dt))
        assertTrue(sim.consistent())
    }

    @Test fun noFlakeIsEverLostOrMadeAndEachStaysInTheGlass() {
        val sim = SnowGlobePhysics(5.5f)
        sim.scatter()
        var x = 0.5f
        repeat(1500) { i ->
            val v = if ((i / 120) % 2 == 0) 4f else -4f
            x = (x + v * dt).coerceIn(0.5f, 5f)
            val shake = if (i % 400 in 0..25) 14f else 0f
            sim.step(dt, x, 0.7f, v, 0.3f, 0.95f, shake, -shake)
            if (i % 100 == 0) assertTrue("step $i: ${sim.describe()}", sim.consistent())
        }
        for (i in 0 until sim.flakes) {
            assertTrue("x of $i: ${sim.x[i]}", sim.x[i] in 0f..sim.aspect)
            assertTrue("y of $i: ${sim.y[i]} lying ${sim.lying[i]}", sim.y[i] in 0f..1f)
        }
    }

    @Test fun theDriftIsFairlyLevel() {
        val sim = SnowGlobePhysics(5.5f)
        sim.scatter()
        settle(sim)
        val middle = (sim.columns / 5) until (sim.columns * 4 / 5)
        val hi = middle.maxOf { sim.bottomDepth(it) }
        val lo = middle.minOf { sim.bottomDepth(it) }
        assertTrue("bumps within the drift: $lo..$hi", hi - lo < 0.12f)
        assertTrue("there is a drift: $lo", lo > 0.05f)
    }

    private fun shiftAfterTilt(degrees: Double): Float {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        val before = centreOfSnow(sim)
        val a = Math.toRadians(degrees)
        repeat(60 * 4) { sim.step(dt, tiltX = Math.sin(a).toFloat(), tiltY = Math.cos(a).toFloat()) }
        return centreOfSnow(sim) - before
    }

    @Test fun theSnowLetsGoByDegreesAsThePhoneTilts() {
        val at5 = shiftAfterTilt(5.0)
        val at15 = shiftAfterTilt(15.0)
        val at30 = shiftAfterTilt(30.0)
        assertEquals("5° holds", 0f, at5, 0.01f)
        assertTrue("15° moves some: $at15", at15 > 0.02f)
        assertTrue("30° moves much more: $at30 vs $at15", at30 > 0.15f && at30 > at15 * 1.5f)
    }

    @Test fun turnedPartlyOverOnlyTheLooseSnowFalls() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        // About 120° from upright: the bottom faces a little up.
        repeat(60 * 30) { sim.step(dt, tiltX = 0.87f, tiltY = -0.5f) }
        val onTop = (0 until sim.columns).sumOf { sim.topDepth(it).toDouble() }
        val onBottom = (0 until sim.columns).sumOf { sim.bottomDepth(it).toDouble() }
        val fell = onTop / (onTop + onBottom)
        assertTrue("some fell, some clings: $fell", fell in 0.05..0.85)
    }

    @Test fun theDropletSweepLiftsSnowAndStirsTheLiquid() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        settle(sim)
        var x = 0.6f
        var lifted = 0
        repeat(40) {
            x += 6f * dt
            sim.step(dt, x, 0.7f, 6f)
            lifted = maxOf(lifted, sim.floating)
        }
        assertTrue("flakes lifted by the droplet: $lifted", lifted > 5)
        assertTrue("the liquid moves behind it", sim.liquidAt(x - 0.8f, 0.5f) > 0.05f)
    }

    @Test fun aShakeRaisesABlizzard() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        settle(sim)
        repeat(30) { sim.step(dt, shakeX = 14f, shakeY = -10f) }
        assertTrue("most of the snow is up: ${sim.floating} of ${sim.flakes}", sim.floating > sim.flakes * 0.7f)
        // Half a second after the shake much of it is still flying.
        repeat(30) { sim.step(dt) }
        assertTrue(sim.floating > sim.flakes * 0.5f)
    }

    @Test fun atStartTheSnowLiesAndNothingMoves() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        var steps = 0
        while (sim.step(dt) && steps < 600) steps++
        assertTrue("asleep at once: $steps steps", steps < 30)
        assertEquals(0, sim.floating)
    }

    @Test fun aGentleTiltIsHeldByTheCrumblySnow() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        settle(sim)
        val before = centreOfSnow(sim)
        // About 5°: below the angle at which the snow gives way.
        repeat(60 * 3) { sim.step(dt, tiltX = 0.087f, tiltY = 0.996f) }
        assertEquals(before, centreOfSnow(sim), 0.01f)
        assertEquals(0, sim.floating)
    }

    @Test fun upsideDownTheSnowFallsToTheOtherWall() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        repeat(60 * 60) { sim.step(dt, tiltX = 0f, tiltY = -1f) }
        val onTop = (0 until sim.columns).sumOf { sim.topDepth(it).toDouble() }
        val onBottom = (0 until sim.columns).sumOf { sim.bottomDepth(it).toDouble() }
        assertTrue("on the top wall now: $onTop vs $onBottom", onTop > 0.95 * (onTop + onBottom))
        assertTrue(sim.consistent())
        // And back again.
        repeat(60 * 60) { sim.step(dt) }
        val back = (0 until sim.columns).sumOf { sim.bottomDepth(it).toDouble() }
        assertTrue("back on the bottom: $back", back > 0.95 * (onTop + onBottom))
    }
}
