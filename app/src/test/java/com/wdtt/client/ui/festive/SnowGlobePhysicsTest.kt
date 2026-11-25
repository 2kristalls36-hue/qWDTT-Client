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
            m += sim.depth[c]
            mx += sim.depth[c] * (c + 0.5f) * sim.columnWidth
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
        val hi = middle.maxOf { sim.depth[it] }
        val lo = middle.minOf { sim.depth[it] }
        assertTrue("bumps within the drift: $lo..$hi", hi - lo < 0.09f)
        assertTrue("there is a drift: $lo", lo > 0.05f)
    }

    @Test fun aSlightTiltMakesTheSnowCreepToTheLowerSide() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        settle(sim)
        val before = centreOfSnow(sim)
        // About 15° to the right.
        repeat(60 * 4) { sim.step(dt, tiltX = 0.26f, tiltY = 0.97f) }
        assertTrue("moves right: $before -> ${centreOfSnow(sim)}", centreOfSnow(sim) > before + 0.15f)
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
}
