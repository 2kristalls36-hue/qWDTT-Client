package com.wdtt.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.ui.home.ConnectButton
import com.wdtt.client.ui.home.PowerCard
import com.wdtt.client.ui.home.TunnelUiState
import com.wdtt.client.ui.theme.ArdttTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the home screen parts to PNG (build/outputs/roborazzi) for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xxhdpi")
class HomeScreenshots {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, theme: String, state: TunnelUiState, level: PowerLevel) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme(themeMode = theme) {
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    ConnectButton(state = state, level = level, onClick = {})
                    PowerCard(level = level, locked = state != TunnelUiState.Off, onLevelChange = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test fun offBalancedLight() = shot("off_balanced_light", "light", TunnelUiState.Off, PowerLevel.Balanced)
    @Test fun onStableLight() = shot("on_stable_light", "light", TunnelUiState.On, PowerLevel.Stable)
    @Test fun onMaxLight() = shot("on_max_light", "light", TunnelUiState.On, PowerLevel.Maximum)
    @Test fun offMaxDark() = shot("off_max_dark", "dark", TunnelUiState.Off, PowerLevel.Maximum)
    @Test fun connectingBalancedDark() = shot("connecting_balanced_dark", "dark", TunnelUiState.Connecting, PowerLevel.Balanced)
}
