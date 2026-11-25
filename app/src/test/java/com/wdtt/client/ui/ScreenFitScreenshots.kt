package com.wdtt.client.ui

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
 * navigation). The shape is injected; PNGs are for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenFitScreenshots {
    @get:Rule val compose = createEmptyComposeRule()

    @After fun reset() {
        screenShapeOverride = null
        com.wdtt.client.ui.festive.festiveDateOverride = null
    }

    private fun settle(rounds: Int = 8) = repeat(rounds) {
        Thread.sleep(100); ShadowLooper.idleMainLooper(); compose.mainClock.advanceTimeBy(160)
    }

    private fun run(dir: String, shape: ScreenShape, level: PowerLevel, glass: Boolean, clear: Float = 0.5f, newYear: Boolean = false, theme: String? = null) {
        screenShapeOverride = shape
        if (newYear) com.wdtt.client.ui.festive.festiveDateOverride = java.time.LocalDate.of(2026, 12, 31)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            FakeAndroidKeyStore.install()
            LiteSettings(ctx).savePowerLevel(level)
            LiteSettings(ctx).saveLiquidGlass(glass)
            LiteSettings(ctx).saveGlassTransparency(clear)
            if (theme != null) com.wdtt.client.SettingsStore(ctx).saveThemeMode(theme)
        }
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java)
        settle(10)
        captureScreenRoboImage("build/outputs/roborazzi/screenfit/$dir/home.png")
        if (newYear) {
            // Let the snow in the glass settle, for a look at the layer on the bottom.
            settle(40)
            captureScreenRoboImage("build/outputs/roborazzi/screenfit/$dir/home_settled.png")
        }
        compose.onNodeWithText("Обход").performClick()
        settle(10)
        captureScreenRoboImage("build/outputs/roborazzi/screenfit/$dir/apps.png")
        compose.onNodeWithText("Настройки").performClick()
        settle(10)
        captureScreenRoboImage("build/outputs/roborazzi/screenfit/$dir/settings_top.png")
        compose.onRoot().performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.7f - 700f, durationMillis = 700) }
        settle(8)
        captureScreenRoboImage("build/outputs/roborazzi/screenfit/$dir/settings.png")
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun gesture44Glass() = run("g44_glass", ScreenShape(44.dp, true), PowerLevel.Balanced, glass = true)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun gesture32GlassMax() = run("g32_glass_max", ScreenShape(32.dp, true), PowerLevel.Maximum, glass = true, clear = 1f)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun gesture56Plain() = run("g56_plain", ScreenShape(56.dp, true), PowerLevel.Stable, glass = false)

    // The values a ColorOS phone reported: 58 dp corners, gesture navigation.
    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun gesture58GlassColorOs() = run("g58_glass_coloros", ScreenShape(58.dp, true), PowerLevel.Balanced, glass = true, clear = 0.9f)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun newYearGlassMax() = run("ny_glass_max", ScreenShape(58.dp, true), PowerLevel.Maximum, glass = true, clear = 0.9f, newYear = true, theme = "dark")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun newYearPlainStableLight() = run("ny_plain_stable_light", ScreenShape(58.dp, true), PowerLevel.Stable, glass = false, newYear = true, theme = "light")

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun buttons44() = run("b44", ScreenShape(44.dp, false), PowerLevel.Balanced, glass = true)
}
