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
            val c = kotlin.math.floor((sim.x[i] - x0) / w * cols).toInt()
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
            if (i % 100 == 0) assertTrue("step $i", sim.consistent())
        }
        for (i in 0 until sim.flakes) {
            assertTrue("x of $i: ${sim.x[i]}", sim.x[i] in 0f..sim.aspect)
            assertTrue("y of $i: ${sim.y[i]} lying ${sim.lying[i]}", sim.y[i] in 0f..1f)
        }
    }

    @Test fun theDriftLiesEvenAndNothingStirsIt() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        val depths = (0..14).map { sim.columnDepth(1f + it * 0.25f) }
        println("drift depths: ${depths.joinToString { "%.3f".format(it) }}")
        assertTrue("even: ${depths.min()}..${depths.max()}", depths.max() - depths.min() < 1.2f * sim.grain)
        val x0 = sim.x.copyOf()
        val y0 = sim.y.copyOf()
        // The droplet swept to and fro, the phone shaken hard now and then.
        var x = 0.6f
        repeat(60 * 20) { k ->
            val v = if ((k / 90) % 2 == 0) 5f else -5f
            x = (x + v * dt).coerceIn(0.6f, 4.9f)
            val shake = if (k % 300 < 36) (if ((k / 6) % 2 == 0) 16f else -16f) else 0f
            sim.step(dt, x, 0.7f, v, shake, shake * 0.6f)
        }
        val moved = (0 until sim.crustCount).count { sim.x[it] != x0[it] || sim.y[it] != y0[it] }
        assertEquals("the drift stays as it lies", 0, moved)
        assertTrue(sim.consistent())
    }

    @Test fun aHighDriftKeepsTheHangingSnowOverIt() {
        val sim = SnowGlobePhysics(5.5f, level = 0.74f)
        sim.settleAll()
        repeat(60 * 4) { k ->
            val shake = if (k % 80 < 30) (if ((k / 6) % 2 == 0) 16f else -16f) else 0f
            sim.step(dt, shakeX = shake, shakeY = shake * 0.6f)
        }
        assertTrue(sim.consistent())
        run(sim, 8f)
        assertTrue(sim.consistent())
        // Over the drift, in 6 x 2 parts of the straight middle.
        val counts = IntArray(12)
        val w = sim.aspect - 2 * sim.corner
        for (i in 0 until sim.flakes) {
            if (!sim.suspended[i]) continue
            val c = kotlin.math.floor((sim.x[i] - sim.corner) / w * 6).toInt()
            if (c < 0 || c >= 6) continue
            counts[(if (sim.y[i] < sim.driftTop / 2) 0 else 6) + c]++
        }
        println("over a high drift: ${counts.joinToString()}")
        assertTrue("no part of it bare: ${counts.joinToString()}", counts.min() > 0.5 * counts.average())
    }

    @Test fun theQuietSnowIsWorkedOutAtHalfTheFrames() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        run(sim, 3f)
        assertTrue("only drifting: not lively", !sim.lively)
        sim.step(dt, shakeX = 14f, shakeY = 10f)
        assertTrue("shaken: lively", sim.lively)
    }

    @Test fun afterAShakeTheSnowSpreadsOutAgainSoon() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        run(sim, 5f)
        repeat(36) { sim.step(dt, shakeX = if ((it / 6) % 2 == 0) 16f else -16f, shakeY = if ((it / 6) % 2 == 0) 10f else -10f) }
        val shaken = spread(sim)
        var even = -1f
        for (k in 1..60 * 20) {
            sim.step(dt)
            if (even < 0f && k % 30 == 0) {
                val c = spread(sim)
                if (c.min() > 0.5 * c.average()) even = k * dt
            }
        }
        val after = spread(sim)
        println("shaken: ${shaken.joinToString()}; even again after $even s; 20 s on: ${after.joinToString()}")
        assertTrue("even again within 6 s: $even", even in 0f..6f)
        assertTrue("and stays so: ${after.joinToString()}", after.min() > 0.5 * after.average())
        assertTrue(sim.consistent())
    }
}
