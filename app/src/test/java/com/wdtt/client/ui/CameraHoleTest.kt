package com.wdtt.client.ui

import android.graphics.Path
import androidx.compose.ui.geometry.Rect
import com.wdtt.client.ui.components.layout.bandHolePlace
import com.wdtt.client.ui.components.layout.outlineWidths
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Finding the camera circle in a cutout reported as a band from the top edge (ColorOS). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CameraHoleTest {
    // The band a ColorOS phone reported: 507,0 - 573,120.
    private val band = Rect(507f, 0f, 573f, 120f)

    @Test fun pillRoundedAtTheBottom() {
        // Rectangle from the top, a half circle at the bottom: the hole is at the bottom.
        val path = Path().apply {
            addRect(507f, 0f, 573f, 87f, Path.Direction.CW)
            addCircle(540f, 87f, 33f, Path.Direction.CW)
        }
        val (place, how) = bandHolePlace(outlineWidths(path, band), band, statusTop = 120)
        assertEquals("снизу", how)
        assertEquals(87f, place.second, 2.5f)
    }

    @Test fun circleInTheBand() {
        val b = Rect(507f, 20f, 573f, 86f)
        val path = Path().apply { addCircle(540f, 53f, 33f, Path.Direction.CW) }
        val (place, how) = bandHolePlace(outlineWidths(path, b), b, statusTop = 120)
        assertEquals("середина контура", how)
        assertEquals(53f, place.second, 3f)
    }

    @Test fun plainRectangleUsesTheStatusBar() {
        val path = Path().apply { addRect(507f, 0f, 573f, 120f, Path.Direction.CW) }
        val (place, how) = bandHolePlace(outlineWidths(path, band), band, statusTop = 96)
        assertEquals("строка состояния", how)
        assertEquals(48f, place.second, 0.5f)
        assertEquals(66f, place.first, 2f)
    }
}
