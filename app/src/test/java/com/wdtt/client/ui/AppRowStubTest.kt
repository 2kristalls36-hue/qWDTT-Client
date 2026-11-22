package com.wdtt.client.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.wdtt.client.ui.apps.AppItem
import com.wdtt.client.ui.apps.AppRow
import com.wdtt.client.ui.apps.AppRowStub
import com.wdtt.client.ui.theme.ArdttTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The loading stub must be exactly as tall as a loaded app row (the ARDTT stubs were not). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xxhdpi")
class AppRowStubTest {
    @get:Rule val compose = createComposeRule()

    private fun check(fontScale: String) {
        compose.setContent {
            ArdttTheme {
                Column {
                    Box(Modifier.testTag("row")) { AppRow(AppItem("org.example.app", "Пример", false), checked = false) {} }
                    Box(Modifier.testTag("stub")) { AppRowStub(Brush.horizontalGradient(listOf(Color.Gray, Color.LightGray)), 0.6f, 0.4f) }
                }
            }
        }
        val row = compose.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val stub = compose.onNodeWithTag("stub").getUnclippedBoundsInRoot()
        assertEquals("height at $fontScale", (row.bottom - row.top).value, (stub.bottom - stub.top).value, 0.01f)
    }

    @Test fun sameHeight() = check("1.0")

    @Test @Config(fontScale = 1.3f) fun sameHeightLargeFont() = check("1.3")

    @Test @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    fun picture() {
        compose.setContent {
            ArdttTheme(themeMode = "dark") {
                Column(
                    Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    AppRow(AppItem("org.example.app", "Пример приложения", false), checked = true) {}
                    AppRow(AppItem("org.example.mail", "Почта", false), checked = false) {}
                    val b = Brush.horizontalGradient(listOf(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, androidx.compose.material3.MaterialTheme.colorScheme.surface, androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant), startX = 200f, endX = 620f)
                    AppRowStub(b, 0.62f, 0.4f)
                    AppRowStub(b, 0.48f, 0.52f)
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/app_stubs.png")
    }
}
