package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SnowGlobePhysicsTest {
    private val dt = 1f / 60f

    private fun settle(sim: SnowGlobePhysics, tiltX: Float = 0f, tiltY: Float = 1f, maxSteps: Int = 60 * 120): Int {
        var steps = 0
        while (sim.step(dt, tiltX = tiltX, tiltY = tiltY) && steps < maxSteps) steps++
        return steps
    }

    private fun cells(sim: SnowGlobePhysics) = FloatArray(sim.flakes * 2) { if (it % 2 == 0) sim.x[it / 2] else sim.y[it / 2] }

    /** Grains now more than a grain away from where they were. */
    private fun movedSince(sim: SnowGlobePhysics, before: FloatArray) =
        (0 until sim.flakes).count { Math.hypot((sim.x[it] - before[2 * it]).toDouble(), (sim.y[it] - before[2 * it + 1]).toDouble()) > 2 * sim.grain }

    @Test fun theShakenSnowSettlesSlowlyAndTheGlobeFallsAsleep() {
        val sim = SnowGlobePhysics(5.5f)
        sim.scatter()
        val steps = settle(sim)
        // Slow: still falling after three seconds, all down within two minutes.
        assertTrue("settles slowly but surely: $steps steps, ${sim.describe()}", steps in 180 until 60 * 120)
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
            val turn = (i / 300) * 1.3
            sim.step(dt, x, 0.7f, v, Math.sin(turn).toFloat(), Math.cos(turn).toFloat(), shake, -shake)
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
        // Over the middle of the bar, averaged over a few grains, as the eye sees it.
        val xs = (20..80).map { sim.aspect * it / 100f }
        val depth = xs.map { px -> (-2..2).map { sim.columnDepth(px + it * 2 * sim.grain) }.average().toFloat() }
        val hi = depth.max()
        val lo = depth.min()
        assertTrue("bumps within the drift: $lo..$hi", hi - lo < 0.1f)
        assertTrue("there is a drift: $lo", lo > 0.1f)
    }

    /** Shift of the snow and the grains that moved, after four seconds at [degrees]. */
    private fun afterTilt(degrees: Double, seconds: Int = 4): Pair<Float, Int> {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        val before = sim.meanLyingX()
        val start = cells(sim)
        val a = Math.toRadians(degrees)
        repeat(60 * seconds) { sim.step(dt, tiltX = Math.sin(a).toFloat(), tiltY = Math.cos(a).toFloat()) }
        println("$degrees°: moves ${sim.moves}")
        return (sim.meanLyingX() - before) to movedSince(sim, start)
    }

    @Test fun theSnowLetsGoByDegreesAsThePhoneTilts() {
        val (shift5, moved5) = afterTilt(5.0)
        val (shift12, moved12) = afterTilt(12.0)
        val (shift20, moved20) = afterTilt(20.0)
        val (shift30, moved30) = afterTilt(30.0)
        println("5°: $shift5 $moved5; 12°: $shift12 $moved12; 20°: $shift20 $moved20; 30°: $shift30 $moved30")
        assertTrue("5° holds: $shift5, $moved5 grains", abs(shift5) < 0.005f && moved5 <= 3)
        assertTrue("12° sets some grains rolling: $moved12", moved12 in 5..400)
        assertTrue("20° slides: $shift20, $moved20", shift20 > 0.01f && moved20 > moved12 * 3)
        assertTrue("30° slides much more: $shift30 vs $shift20", shift30 > shift20 * 1.5f && moved30 > moved20)
    }

    @Test fun turnedPartlyOverOnlyTheLooseSnowFalls() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        val start = cells(sim)
        // About 140° from upright: the bottom faces down, a little to the side.
        repeat(60 * 30) { sim.step(dt, tiltX = 0.64f, tiltY = -0.77f) }
        val fell = movedSince(sim, start).toFloat() / sim.flakes
        assertTrue("some fell, some clings: $fell", fell in 0.3f..0.93f)
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
        val before = sim.meanLyingX()
        // About 5°: below the angle at which the snow gives way.
        repeat(60 * 3) { sim.step(dt, tiltX = 0.087f, tiltY = 0.996f) }
        assertEquals(before, sim.meanLyingX(), 0.01f)
        assertEquals(0, sim.floating)
    }

    @Test fun upsideDownTheSnowFallsToTheOtherWall() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        repeat(60 * 60) { sim.step(dt, tiltX = 0f, tiltY = -1f) }
        val up = sim.lyingInTopHalf()
        assertTrue("on the top wall now: $up of ${sim.flakes}, ${sim.describe()}", up > 0.95f * sim.flakes)
        assertTrue(sim.consistent())
        // And back again.
        repeat(60 * 60) { sim.step(dt) }
        val down = sim.lyingInBottomHalf()
        assertTrue("back on the bottom: $down", down > 0.95f * sim.flakes)
    }

    @Test fun turnedBackTheSnowDoesNotHangFromTheTop() {
        val sim = SnowGlobePhysics(5.5f)
        sim.settleAll()
        repeat(60 * 40) { sim.step(dt, tiltX = 0f, tiltY = -1f) }
        val hanging = sim.lyingInTopHalf()
        // Upright again: within two seconds most of it has let go of the top wall.
        repeat(120) { sim.step(dt) }
        val left = sim.lyingInTopHalf()
        assertTrue("still on the top: $left of $hanging", left < 0.3f * hanging)
    }

    @Test fun heldSidewaysTheSnowPilesInTheLowerEnd() {
        for (degrees in listOf(-80.0, -95.0, 90.0)) {
            val sim = SnowGlobePhysics(5.5f)
            sim.settleAll()
            val a = Math.toRadians(degrees)
            repeat(60 * 20) { sim.step(dt, tiltX = Math.sin(a).toFloat(), tiltY = Math.cos(a).toFloat()) }
            val moves = sim.moves
            val sinking = sim.floating
            repeat(60 * 3) { sim.step(dt, tiltX = Math.sin(a).toFloat(), tiltY = Math.cos(a).toFloat()) }
            val landed = sinking - sim.floating
            val mean = sim.meanLyingX()
            val end = if (degrees < 0) mean else sim.aspect - mean
            println("$degrees°: mean x $mean, moves in the last 3 s ${sim.moves - moves} (landed $landed), ${sim.describe()}")
            assertTrue("$degrees°: piled in the lower end: $mean", end < 1.1f)
            // Only the odd flake still sinking through the liquid lands and rolls.
            assertTrue("$degrees°: the heap rests: ${sim.moves - moves}", sim.moves - moves <= landed + 2)
            assertTrue("$degrees°: asleep, but for flakes just landed: ${sim.awakeNow()}", sim.awakeNow() <= 3 + landed / 2)
            assertTrue(sim.consistent())
        }
    }
}
