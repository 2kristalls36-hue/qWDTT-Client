package com.wdtt.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.ui.home.PowerCard
import com.wdtt.client.ui.theme.ArdttTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The card captured mid-drag at several thumb positions, to see the colour blend. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h500dp-xxhdpi")
class SliderBlendScreenshots {
    @get:Rule val compose = createComposeRule()

    @Test fun blendAlongTheTrack() {
        var level by mutableStateOf(PowerLevel.Stable)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme("light") {
                Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    PowerCard(level = level, locked = false, onLevelChange = { level = it })
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        val stops = listOf(0f, 0.25f, 0.5f, 0.62f, 0.75f, 0.88f, 1f)
        compose.onNodeWithTag("power-slider").performTouchInput {
            val pad = 14.dp.toPx()
            fun x(f: Float) = pad + (width - 2 * pad) * f
            down(Offset(x(0f), centerY))
        }
        stops.forEachIndexed { i, f ->
            compose.onNodeWithTag("power-slider").performTouchInput {
                val pad = 14.dp.toPx()
                moveTo(Offset(pad + (width - 2 * pad) * f, centerY))
            }
            compose.mainClock.advanceTimeBy(400)
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/blend_$i.png")
        }
        compose.onNodeWithTag("power-slider").performTouchInput { up() }
    }
}
