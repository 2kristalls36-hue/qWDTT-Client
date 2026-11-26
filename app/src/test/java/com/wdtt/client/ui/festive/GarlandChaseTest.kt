package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GarlandChaseTest {
    /** The top garland: two sags of four lights. */
    private val lights = 8
    private val dt = 1f / 240f

    @Test fun twoLightsSideBySideNeverShowTheSameColour() {
        var t = 0f
        while (t < 30f) {
            for (i in 0 until lights - 1) {
                assertNotEquals("lights $i and ${i + 1} at $t s", GarlandChase.colour(i, t), GarlandChase.colour(i + 1, t))
            }
            t += dt
        }
        for (i in 0 until lights - 1) assertNotEquals(GarlandChase.still(i), GarlandChase.still(i + 1))
    }

    @Test fun eachLightChangesOnceASecondOneAfterAnother() {
        val changes = Array(lights) { ArrayList<Float>() }
        var t = 0f
        while (t < 10f) {
            for (i in 0 until lights) if (GarlandChase.colour(i, t + dt) != GarlandChase.colour(i, t)) changes[i] += t
            t += dt
        }
        for (i in 0 until lights) {
            assertTrue("light $i: ${changes[i].size} changes in 10 s", changes[i].size in 9..10)
            changes[i].zipWithNext { a, b -> assertEquals("light $i a second apart", 1f, b - a, 0.01f) }
        }
        // Along the wire, in turn: each a moment after the one before it.
        for (i in 1 until lights) {
            val lag = changes[i][1] - changes[i - 1][1]
            assertEquals("light $i after ${i - 1}", GarlandChase.RUN, lag, 0.01f)
        }
    }

    @Test fun theColoursRunAlongTheWire() {
        // After a change each light shows what the light before it showed.
        for (i in 1 until lights) {
            assertEquals(GarlandChase.colour(i - 1, 2.5f), GarlandChase.colour(i, 3.9f))
        }
    }

    @Test fun aLightBlinksAsItChanges() {
        for (i in 0 until lights) {
            val at = 3f + i * GarlandChase.RUN
            assertTrue("dim at the change", GarlandChase.brightness(i, at) < 0.3f)
            assertEquals("full between changes", 1f, GarlandChase.brightness(i, at + 0.5f), 0f)
        }
    }
}
