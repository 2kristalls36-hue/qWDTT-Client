package com.wdtt.client.ui

import com.wdtt.client.ui.home.magnet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetTest {
    @Test fun clingsNearStops() {
        assertEquals(0.5f, magnet(0.5f), 1e-6f)
        // 0.05 away from the stop shows much closer to it.
        assertTrue(magnet(0.55f) - 0.5f < 0.01f)
        // Outside the reach the thumb follows the finger exactly.
        assertEquals(0.3f, magnet(0.3f), 1e-6f)
        // Continuous at the edge of the reach.
        assertEquals(0.63f, magnet(0.63f), 1e-3f)
        // Monotonic: never jumps backwards.
        var last = -1f
        for (i in 0..1000) { val v = magnet(i / 1000f); assertTrue(v >= last - 1e-6f); last = v }
    }
}
