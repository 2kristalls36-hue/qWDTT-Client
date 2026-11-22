package com.wdtt.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.ui.components.surface.GlassBackdrop
import com.wdtt.client.ui.components.surface.LocalGlassBackdrop
import com.wdtt.client.ui.home.PowerCard
import com.wdtt.client.ui.theme.ArdttTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The power thumb held down with liquid glass on: it becomes a magnifying lens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h400dp-xxhdpi")
class SliderLensScreenshots {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, theme: String, level: PowerLevel) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme(themeMode = theme) {
                val glass = GlassBackdrop(rememberGraphicsLayer())
                CompositionLocalProvider(LocalGlassBackdrop provides glass) {
                    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                        PowerCard(level = level, locked = false, onLevelChange = {}, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lens_rest_$name.png")
        val node = compose.onNodeWithTag("power-slider")
        node.performTouchInput {
            val pad = 14.dp.toPx()
            down(Offset(pad + (width - 2 * pad) * level.ordinal / 2f, centerY))
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lens_$name.png")
        node.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(700)
    }

    @Test fun balancedLight() = shot("balanced_light", "light", PowerLevel.Balanced)
    @Test fun stableDark() = shot("stable_dark", "dark", PowerLevel.Stable)
    @Test fun maxDark() = shot("max_dark", "dark", PowerLevel.Maximum)

    private fun switchShot(name: String, theme: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme(themeMode = theme) {
                val glass = GlassBackdrop(rememberGraphicsLayer())
                CompositionLocalProvider(LocalGlassBackdrop provides glass) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.background(MaterialTheme.colorScheme.background).padding(24.dp),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(40.dp),
                    ) {
                        com.wdtt.client.ui.components.control.LiteSwitch(true, {}, Modifier.testTag("on"))
                        com.wdtt.client.ui.components.control.LiteSwitch(false, {}, Modifier.testTag("off"))
                        com.wdtt.client.ui.components.control.LiteSwitch(true, {})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("on").performTouchInput { down(center) }
        compose.onNodeWithTag("off").performTouchInput { down(1, center) }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/switch_$name.png")
    }

    @Test fun switchLight() = switchShot("light", "light")
    @Test fun switchDark() = switchShot("dark", "dark")

    @Test fun afterRelease() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme(themeMode = "light") {
                val glass = GlassBackdrop(rememberGraphicsLayer())
                val levelState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(PowerLevel.Stable) }
                CompositionLocalProvider(LocalGlassBackdrop provides glass) {
                    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                        PowerCard(level = levelState.value, locked = false, onLevelChange = { levelState.value = it }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
        val node = compose.onNodeWithTag("power-slider")
        node.performTouchInput {
            val pad = 14.dp.toPx()
            val track = width - 2 * pad
            down(Offset(pad, centerY))
            for (k in 1..20) { moveTo(Offset(pad + track * 0.46f * k / 20f, centerY)); }
            up()
        }
        compose.mainClock.advanceTimeBy(3000)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/lens_after_release.png")
    }
}
