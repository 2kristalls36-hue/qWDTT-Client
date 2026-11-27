package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SnowGlobeFitTest {
    @Test fun theGlobeIsMadeAgainWhenTheBarChanges() {
        val h = GlobeHolder()
        // Fitted to the screen first (narrower, rounder), then the standard bar.
        h.fit(1000f, 180f, 0.5f, 2.75f, null)
        val fitted = h.sim!!
        h.fit(1000f, 180f, 0.5f, 2.75f, null)
        assertSame("the same bar: the same globe", fitted, h.sim)
        h.fit(1080f, 180f, 0.5f, 2.75f, null)
        val standard = h.sim!!
        assertNotSame("a wider bar: a new globe", fitted, standard)
        // Its walls where the glass is: as much wider as the bar.
        val warp = h.warp!!
        assertEquals("the room as wide as the new bar", warp.roomWidth / warp.roomHeight, standard.aspect, 1e-4f)
        assertEquals(1080f, warp.width, 0f)
        h.fit(1080f, 180f, 0.3f, 2.75f, null)
        assertNotSame("another rounding: a new globe", standard, h.sim)
    }
}
