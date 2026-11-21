package com.wdtt.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.wdtt.client.ui.components.layout.LiteTabIcons
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tab glyphs at rest and through their tap motion, large for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h400dp-xhdpi")
class TabGlyphScreenshots {
    @get:Rule val compose = createComposeRule()

    @Test fun glyphs() {
        compose.setContent {
            Column(
                Modifier.background(Color(0xFFF4F6FA)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf(LiteTabIcons.Power, LiteTabIcons.Cards, LiteTabIcons.Tiles).forEach { g ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        g(0f, 0f, Color(0xFF64748B), Modifier.size(56.dp))
                        listOf(0f, 0.25f, 0.5f, 0.75f).forEach { p -> g(p, 1f, Color(0xFF1565C0), Modifier.size(56.dp)) }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/tab_glyphs.png")
    }
}
