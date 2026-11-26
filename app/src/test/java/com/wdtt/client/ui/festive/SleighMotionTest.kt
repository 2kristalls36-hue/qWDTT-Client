package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleighMotionTest {
    private val dt = 1f / 60f

    /** Runs [seconds] as the app does: frame by frame while busy, asleep otherwise. */
    private fun run(m: SleighMotion, seconds: Float, travel: Boolean): Int {
        var frames = 0
        var left = seconds
        while (left > 0f) {
            if (m.busy || travel != m.travellingNow) {
                m.advance(dt, travel)
                left -= dt
                frames++
            } else {
                val s = minOf(left, maxOf(dt, m.secondsToNext()))
                m.advance(s, travel)
                left -= s
            }
        }
        return frames
    }

    @Test fun standingItStirsNowAndThenAndRestsInBetween() {
        val m = SleighMotion(seed = 5)
        val frames = run(m, 120f, travel = false)
        val share = frames / (120f * 60f)
        println("standing: frames drawn ${(share * 100).toInt()}% of the time")
        assertEquals(0f, m.speed, 0f)
        assertTrue("the bird stays", m.perched)
        assertTrue("something stirs: $share", share > 0.05f)
        assertTrue("mostly at rest: $share", share < 0.4f)
    }

    @Test fun itSetsOffASecondAfterTheConnectionStartsAndTheBirdFliesOff() {
        val m = SleighMotion(seed = 2)
        run(m, 3f, travel = false)
        run(m, 0.95f, travel = true)
        assertEquals("waits a second", 0f, m.speed, 0f)
        assertTrue("the bird still sits", m.perched)
        run(m, 1.5f, travel = true)
        assertTrue("off they go: ${m.speed}", m.speed > 0f)
        assertFalse("the bird took off", m.perched)
        run(m, 5f, travel = true)
        assertTrue("full speed: ${m.speed}", m.speed > 0.99f)
        assertTrue("the bird is gone", m.birdAway)
        assertTrue("the trees go by", m.pose.distance > 3f)
    }

    @Test fun itStopsAndTheBirdComesBack() {
        val m = SleighMotion(seed = 3)
        run(m, 12f, travel = true)
        run(m, 3f, travel = false)
        assertEquals("stopped", 0f, m.speed, 0f)
        assertTrue("the bird is not back yet", m.birdAway)
        run(m, 5f, travel = false)
        assertTrue("the bird is back", m.perched)
        // And then it rests again.
        run(m, 3f, travel = false)
        assertTrue(m.secondsToNext() >= 0f)
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
