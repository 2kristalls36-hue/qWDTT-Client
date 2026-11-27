package com.wdtt.client.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.wdtt.client.MainActivity
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.testing.FakeAndroidKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Liquid glass where there is no lens (Android 12 and older, HarmonyOS): the simpler glass
 * of the tab bar and the page header. PNGs (build/outputs/roborazzi/glassold) for review;
 * any crash fails.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassOlderAndroidScreenshots {
    @get:Rule val compose = createEmptyComposeRule()

    private fun settle(rounds: Int = 6) {
        repeat(rounds) {
            Thread.sleep(100)
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeBy(160)
        }
    }

    private fun run(dir: String) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            FakeAndroidKeyStore.install()
            LiteSettings(ctx).saveLiquidGlass(true)
            LiteSettings(ctx).saveGlassTransparency(LiteSettings.DEFAULT_GLASS_TRANSPARENCY)
        }
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java)
        settle(10)
        captureScreenRoboImage("build/outputs/roborazzi/glassold/$dir/home.png")
        compose.onNodeWithText("Настройки").performClick()
        settle(12)
        captureScreenRoboImage("build/outputs/roborazzi/glassold/$dir/settings.png")
    }

    @Test @Config(sdk = [31], qualifiers = "w393dp-h873dp-xxhdpi")
    fun android12Light() = run("a12_light")

    @Test @Config(sdk = [31], qualifiers = "w393dp-h873dp-night-xxhdpi")
    fun android12Dark() = run("a12_dark")

    @Test @Config(sdk = [29], qualifiers = "w393dp-h873dp-xxhdpi")
    fun android10Light() = run("a10_light")
}
