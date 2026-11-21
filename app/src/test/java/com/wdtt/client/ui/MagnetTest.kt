package com.wdtt.client.ui

import com.wdtt.client.ui.home.magnet
import com.wdtt.client.ui.motion.Magnet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetTest {
    @Test fun dragPull() {
        // Caught near the stop.
        assertEquals(0.5f, magnet(0.52f), 1e-6f)
        assertEquals(0f, magnet(0.03f), 1e-6f)
        // Outside the reach the thumb follows the finger exactly; continuous at the edge.
        assertEquals(0.3f, magnet(0.3f), 1e-6f)
        assertEquals(0.63f, magnet(0.6299f), 1e-3f)
        // Drawn in faster the closer it is: equal finger steps give growing thumb steps.
        val far = magnet(0.62f) - magnet(0.61f)
        val near = magnet(0.545f) - magnet(0.535f)
        assertTrue("near $near > far $far", near > far)
        // Monotonic: never jumps backwards.
        var last = -1f
        for (i in 0..1000) { val v = magnet(i / 1000f); assertTrue(v >= last - 1e-6f); last = v }
    }

    @Test fun releaseAccelerates() {
        // Slow start, fast finish.
        assertTrue(Magnet.Easing.transform(0.5f) < 0.2f)
        assertTrue(Magnet.Easing.transform(1f) - Magnet.Easing.transform(0.9f) > Magnet.Easing.transform(0.1f) * 10)
        // Farther trips take longer.
        assertTrue(Magnet.durationMillis(0.5f, 1f) > Magnet.durationMillis(0.1f, 1f))
    }
}
