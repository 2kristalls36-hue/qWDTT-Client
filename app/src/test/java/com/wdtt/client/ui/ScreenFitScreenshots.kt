package com.wdtt.client.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.wdtt.client.MainActivity
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.testing.FakeAndroidKeyStore
import com.wdtt.client.ui.components.layout.ScreenShape
import com.wdtt.client.ui.components.layout.screenShapeOverride
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * The interface on screens of different shapes (rounded corners, gesture or button
 * navigation, a round camera hole). The shape is injected; PNGs are for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenFitScreenshots {
    @get:Rule val compose = createEmptyComposeRule()

    @After fun reset() { screenShapeOverride = null }

    private fun settle(rounds: Int = 8) = repeat(rounds) {
        Thread.sleep(100); ShadowLooper.idleMainLooper(); compose.mainClock.advanceTimeBy(160)
    }

    // A camera hole of 26 dp at the top centre of a 411 dp wide xxhdpi screen.
    private val hole = Rect(616f - 39f, 30f, 616f + 39f, 108f)

    private fun run(dir: String, shape: ScreenShape, level: PowerLevel, glass: Boolean, clear: Float = 0.5f) {
        screenShapeOverride = shape
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            FakeAndroidKeyStore.install()
            LiteSettings(ctx).savePowerLevel(level)
            LiteSettings(ctx).saveLiquidGlass(glass)
            LiteSettings(ctx).saveGlassTransparency(clear)
        }
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java)
        settle(10)
        captureScreenRoboImage("build/outputs/roborazzi/screenfit/$dir/home.png")
        compose.onNodeWithText("Настройки").performClick()
        settle(10)
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.7f - 700f, durationMillis = 700) }
        settle(8)
        captureScreenRoboImage("build/outputs/roborazzi/screenfit/$dir/settings.png")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun gesture44Glass() = run("g44_glass", ScreenShape(44.dp, true, hole), PowerLevel.Balanced, glass = true)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun gesture32GlassMax() = run("g32_glass_max", ScreenShape(32.dp, true, hole), PowerLevel.Maximum, glass = true, clear = 1f)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun gesture56Plain() = run("g56_plain", ScreenShape(56.dp, true, null), PowerLevel.Stable, glass = false)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun buttons44() = run("b44", ScreenShape(44.dp, false, hole), PowerLevel.Balanced, glass = true)
}
