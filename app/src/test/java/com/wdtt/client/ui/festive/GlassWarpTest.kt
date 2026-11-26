package com.wdtt.client.ui.festive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassWarpTest {
    // A bar of 360 x 64 dp at 3 px a dp, corners of the full half height.
    private val warp = GlassWarp(1080f, 192f, 96f, depth = 21f, bezel = 48f)
    private val at = FloatArray(4)

    @Test fun theMiddleIsSeenAsItIs() {
        val q = warp.map(warp.roomWidth / 2f, warp.roomHeight / 2f, at)
        assertEquals(1f, q, 1e-4f)
        assertEquals(540f, at[0], 1e-3f)
        assertEquals(96f, at[1], 1e-3f)
    }

    @Test fun theGlassIsSeenAtTheEdge() {
        warp.map(warp.roomWidth / 2f, warp.roomHeight, at)
        assertEquals(192f, at[1], 0.5f)
        warp.map(warp.roomWidth / 2f, 0f, at)
        assertEquals(0f, at[1], 0.5f)
    }

    @Test fun nearTheEdgeThereIsLessRoomAndNothingFolds() {
        var last = -1f
        var y = warp.roomHeight / 2f
        while (y <= warp.roomHeight) {
            warp.map(warp.roomWidth / 2f, y, at)
            assertTrue("seen in order: ${at[1]} after $last", at[1] >= last - 1e-3f)
            last = at[1]
            y += 0.5f
        }
        val edge = warp.map(warp.roomWidth / 2f, warp.roomHeight - 0.1f, at)
        assertTrue("about a third at the very edge: $edge", edge in 0.25f..0.45f)
        // The rim's inner end joins the middle smoothly.
        val inner = warp.map(warp.roomWidth / 2f, warp.roomHeight - 48f - 21f - 0.5f, at)
        assertEquals(1f, inner, 0.02f)
    }
}
