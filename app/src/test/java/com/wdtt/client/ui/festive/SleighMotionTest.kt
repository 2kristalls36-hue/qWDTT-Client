package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Under Robolectric: the hooves are found through the figures' paths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SleighMotionTest {
    private val dt = 1f / 60f

    /** Runs [seconds] as the app does: frame by frame while busy, asleep otherwise. */
    private fun run(m: SleighMotion, seconds: Float, travel: Boolean, lens: Float = 0f, each: () -> Unit = {}): Int {
        var frames = 0
        var left = seconds
        while (left > 0f) {
            if (m.busy || travel != m.travellingNow || m.tabUnder(lens) != m.lensTabNow) {
                m.advance(dt, travel, lens)
                left -= dt
                frames++
                each()
            } else {
                val s = minOf(left, maxOf(dt, m.secondsToNext()))
                m.advance(s, travel, lens)
                left -= s
            }
        }
        return frames
    }

    @Test fun standingOnlyTheFigureUnderTheDropletStirs() {
        val m = SleighMotion(seed = 5)
        var deerMoved = false
        var santaMoved = false
        val frames = run(m, 120f, travel = false, lens = 1f) {
            if (m.pose.joints[2].withIndex().any { (k, v) -> v != (if (k % 5 >= 3) 1f else 0f) }) deerMoved = true
            if (m.pose.joints[1].withIndex().any { (k, v) -> v != (if (k % 5 >= 3) 1f else 0f) }) santaMoved = true
        }
        val share = frames / (120f * 60f)
        println("standing, droplet on Santa: frames drawn ${(share * 100).toInt()}% of the time")
        assertEquals(0f, m.speed, 0f)
        assertEquals("all in the silhouette", 0f, m.pose.colour, 0f)
        assertTrue("Santa stirs", santaMoved)
        assertFalse("the reindeer away from the droplet keep still", deerMoved)
        assertTrue("mostly at rest: $share", share < 0.3f)
    }

    @Test fun itSetsOffASecondAfterTheConnectionStartsAndTakesOnItsColours() {
        val m = SleighMotion(seed = 2)
        run(m, 3f, travel = false)
        run(m, 0.95f, travel = true)
        assertEquals("waits a second", 0f, m.speed, 0f)
        assertTrue("the bird still sits", m.perched)
        run(m, 1.5f, travel = true)
        assertTrue("off they go: ${m.speed}", m.speed > 0f)
        assertTrue("colours coming: ${m.pose.colour}", m.pose.colour in 0.001f..0.9f)
        assertFalse("the bird took off", m.perched)
        run(m, 5f, travel = true)
        assertTrue("full speed: ${m.speed}", m.speed > 0.99f)
        assertEquals("all in colour", 1f, m.pose.colour, 0.001f)
        assertTrue("the bird is gone", m.birdAway)
        assertTrue("the trees go by", m.pose.distance > 3f)
    }

    @Test fun snowFliesFromTheHoovesWhereTheyStrike() {
        val m = SleighMotion(seed = 7)
        var atHoof = 0
        var elsewhere = 0
        val hoof = FloatArray(2)
        val legs = listOf(
            Triple(J.FETLOCK_FORE_NEAR, true, false), Triple(J.FETLOCK_FORE_FAR, true, true),
            Triple(J.FETLOCK_HIND_NEAR, false, false), Triple(J.FETLOCK_HIND_FAR, false, true),
        )
        run(m, 10f, travel = true) {
            for (k in 0 until TrainPose.MAX_DUST) {
                val f = m.pose.dustFigure[k]
                // Just made, by a reindeer: next to one of its hooves.
                if (f < 2 || m.pose.dustAge[k] != 0f) continue
                val near = legs.any { (j, fore, far) ->
                    hoofAt(SleighTrain.figures[f], m.pose.joints[f], j, fore, far, hoof)
                    Math.hypot((hoof[0] - m.pose.dustX[k]).toDouble(), (hoof[1] - m.pose.dustY[k]).toDouble()) < 0.9
                }
                if (near) atHoof++ else elsewhere++
            }
        }
        println("hoof snow: $atHoof at a hoof, $elsewhere elsewhere")
        assertTrue("snow from the hooves: $atHoof", atHoof > 20)
        assertEquals("none anywhere else", 0, elsewhere)
    }

    @Test fun itStopsTheColoursFadeAndTheBirdComesBack() {
        val m = SleighMotion(seed = 3)
        run(m, 12f, travel = true)
        run(m, 3f, travel = false)
        assertEquals("stopped", 0f, m.speed, 0f)
        assertEquals("a silhouette again", 0f, m.pose.colour, 0f)
        assertTrue("the bird is not back yet", m.birdAway)
        run(m, 5f, travel = false)
        assertTrue("the bird is back", m.perched)
    }

    @Test fun theReindeerWagsItsTailAtTheDroplet() {
        val m = SleighMotion(seed = 8)
        run(m, 2f, travel = false, lens = 0f)
        var wag = 0f
        run(m, 1.5f, travel = false, lens = 2f) { wag = maxOf(wag, kotlin.math.abs(m.pose.joints[2][J.TAIL * 5])) }
        println("tail wag up to $wag°")
        assertTrue("it wags: $wag", wag > 20f)
    }

    @Test fun aBlipOfConnectionDoesNotStartIt() {
        val m = SleighMotion(seed = 4)
        run(m, 2f, travel = false)
        run(m, 0.5f, travel = true)
        run(m, 3f, travel = false)
        assertEquals(0f, m.speed, 0f)
        assertTrue(m.perched)
    }

    @Test fun startedAgainWhileSlowingItGoesOnWithoutWaiting() {
        val m = SleighMotion(seed = 6)
        run(m, 8f, travel = true)
        run(m, 1f, travel = false)
        val slowing = m.speed
        run(m, 0.3f, travel = true)
        assertTrue("picks up again at once: $slowing -> ${m.speed}", m.speed > slowing)
    }
}
