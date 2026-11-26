package com.wdtt.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.wdtt.client.ui.theme.ArdttTheme
import com.wdtt.client.ui.theme.Brand
import com.wdtt.client.ui.theme.BrandCandyClient
import com.wdtt.client.ui.theme.BrandWordmark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The New Year logo, large and at its real size, in both themes, for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h380dp-xxhdpi")
class NewYearLogoShot {
    @get:Rule val compose = createComposeRule()

    private fun shot(theme: String, year: Int = 2027) {
        compose.setContent {
            ArdttTheme(themeMode = theme) {
                Column(Modifier.background(MaterialTheme.colorScheme.background).padding(20.dp)) {
                    for (h in listOf(150f, 50f)) {
                        val box = Modifier.size((h * Brand.LOGO_WIDTH / Brand.LOGO_HEIGHT).dp, h.dp)
                        Box(Modifier.padding(bottom = (h * 0.45f).dp)) {
                            BrandWordmark(box, withClient = false)
                            BrandCandyClient(year, box)
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/newyear_logo_$theme${if (year == 2027) "" else "_$year"}.png")
    }

    @Test fun dark() = shot("dark")
    @Test fun light() = shot("light")
    /** The same build a year on: every digit has its outline. */
    @Test fun nextYear() = shot("dark", 2028)
}
