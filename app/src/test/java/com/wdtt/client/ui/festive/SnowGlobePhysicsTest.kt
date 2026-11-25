package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnowGlobePhysicsTest {
    private val dt = 1f / 60f

    private fun settle(sim: SnowGlobePhysics, tiltX: Float = 0f, tiltY: Float = 1f, maxSteps: Int = 60 * 60): Int {
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

    @Test fun theShakenSnowSettlesAndTheGlobeFallsAsleep() {
        val sim = SnowGlobePhysics(5.5f)
        sim.scatter()
        val steps = settle(sim)
        assertTrue("settles within a minute: $steps", steps < 60 * 60)
        assertEquals(0, sim.count)
        assertFalse(sim.step(dt))
    }

    @Test fun snowIsNeitherLostNorMade() {
        val sim = SnowGlobePhysics(5.5f)
        sim.scatter()
        val before = sim.snowNow()
        var x = 0.5f
        repeat(900) { i ->
            // The droplet runs to and fro, the phone is shaken now and then.
            val v = if ((i / 120) % 2 == 0) 4f else -4f
            x = (x + v * dt).coerceIn(0.5f, 5f)
            val shake = if (i % 300 in 0..20) 12f else 0f
            sim.step(dt, x, 0.7f, v, 0.2f, 1f, shake, -shake)
        }
        settle(sim)
        assertEquals(before, sim.snowNow(), before * 0.002f)
    }

    @Test fun theDriftIsFairlyLevel() {
        val sim = SnowGlobePhysics(5.5f)
        sim.scatter()
        settle(sim)
        val middle = (sim.columns / 5) until (sim.columns * 4 / 5)
        val hi = middle.maxOf { sim.depth[it] }
        val lo = middle.minOf { sim.depth[it] }
        assertTrue("bumps within the drift: $lo..$hi", hi - lo < 0.08f)
        assertTrue("there is a drift", lo > 0.03f)
    }

    @Test fun tiltingThePhoneMakesTheSnowCreepToTheLowerSide() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        val before = centreOfSnow(sim)
        repeat(60 * 8) { sim.step(dt, tiltX = 0.6f, tiltY = 0.8f) }
        assertTrue("moves right: $before -> ${centreOfSnow(sim)}", centreOfSnow(sim) > before + 0.1f)
    }

    @Test fun theDropletSweepLiftsSnowAndStirsTheLiquid() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        var x = 0.6f
        var lifted = 0
        repeat(40) {
            x += 6f * dt
            sim.step(dt, x, 0.7f, 6f)
            lifted = maxOf(lifted, sim.count)
        }
        assertTrue("flakes lifted by the droplet: $lifted", lifted > 5)
        assertTrue("the liquid moves behind it", sim.liquidAt(x - 0.8f, 0.5f) > 0.05f)
    }

    @Test fun aShakeWhirlsTheSnowUp() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        repeat(20) { sim.step(dt, shakeX = 18f, shakeY = -14f) }
        assertTrue("snow in the liquid: ${sim.count}", sim.count > 40)
        assertTrue(sim.step(dt))
    }
}
