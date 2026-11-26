package com.wdtt.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.wdtt.client.ui.festive.SleighTrain
import com.wdtt.client.ui.festive.drawSleighTab
import com.wdtt.client.ui.theme.ArdttTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The New Year tabs (Santa's train) for review: twice the size, then at their real size as
 * silhouettes, in colour, and with the droplet halfway over the sleigh; dark and light.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h900dp-xxhdpi")
class SleighIconsShot {
    @get:Rule val compose = createComposeRule()

    @Test fun icons() {
        compose.setContent {
            Column {
                for (theme in listOf("dark", "light")) {
                    ArdttTheme(themeMode = theme) {
                        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(8.dp)) {
                            val plain = MaterialTheme.colorScheme.onSurfaceVariant
                            if (theme == "dark") Train(plain, 2f, colour = 0f to 1e5f)
                            Train(plain, 1f, colour = -1f to -1f)
                            Train(plain, 1f, colour = 0f to 1e5f)
                            // The droplet over the sleigh's front half and the first reindeer's back.
                            Train(plain, 1f, colour = 1.55f to 2.55f)
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/sleigh_icons.png")
    }

    /** Four tabs 80 dp wide and 64 high, times [zoom]; colour from tab [colour] to tab. */
    @androidx.compose.runtime.Composable
    private fun Train(plain: Color, zoom: Float, colour: Pair<Float, Float>) {
        val slot = 80.dp * zoom
        Canvas(
            Modifier
                .size(slot * 4, 64.dp * zoom)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val w = size.width / 4f
            for (i in 0 until 4) {
                translate(left = w * i) {
                    drawSleighTab(
                        i, w, size.height, SleighTrain.height.toPx() * zoom, plain,
                        (colour.first - i) * w, (colour.second - i) * w,
                    )
                }
            }
        }
    }
}
