package com.wdtt.client.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.ui.home.PowerCard
import com.wdtt.client.ui.theme.ArdttTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xxhdpi")
class PowerSliderTest {
    @get:Rule val compose = createComposeRule()

    private var level by mutableStateOf(PowerLevel.Balanced)

    private fun setUp(locked: Boolean = false) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme("dark") { PowerCard(level = level, locked = locked, onLevelChange = { level = it }) }
        }
        compose.mainClock.advanceTimeBy(500)
    }

    /** Fraction of the track; the track is inset 14 dp on each side. */
    private fun drag(from: Float, to: Float) {
        compose.onNodeWithTag("power-slider").performTouchInput {
            val pad = 14.dp.toPx()
            fun x(f: Float) = pad + (width - 2 * pad) * f
            val y = centerY
            down(androidx.compose.ui.geometry.Offset(x(from), y))
            val steps = 20
            for (i in 1..steps) {
                moveTo(androidx.compose.ui.geometry.Offset(x(from + (to - from) * i / steps), y))
                advanceEventTime(16)
            }
            up()
        }
        compose.mainClock.advanceTimeBy(1500)
    }

    private val Number.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())

    @Test fun dragAcrossLevelsAndReleaseBetweenStopsSnaps() {
        setUp()
        // Crosses into «Максимальный» mid-drag, then comes back and stops between stops.
        drag(0.5f, 0.9f)
        assertEquals(PowerLevel.Maximum, level)
        drag(1f, 0.62f)
        assertEquals(PowerLevel.Balanced, level)
        compose.onNodeWithTag("power-slider").captureRoboImage("build/outputs/roborazzi/slider_after_drag.png")
    }

    @Test fun dragToStartSelectsStable() {
        setUp()
        drag(0.5f, 0.1f)
        assertEquals(PowerLevel.Stable, level)
    }

    @Test fun lockedSliderIgnoresDrag() {
        setUp(locked = true)
        drag(0.5f, 1f)
        assertEquals(PowerLevel.Balanced, level)
    }
}
