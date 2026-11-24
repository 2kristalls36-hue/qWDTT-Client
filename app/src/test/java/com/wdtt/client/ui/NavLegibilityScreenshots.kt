package com.wdtt.client.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToIndex
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
 * Tab labels over clear glass: the settings page is scrolled step by step under the
 * navigation bar, so its text passes right behind the labels. PNGs are for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavLegibilityScreenshots {
    @get:Rule val compose = createEmptyComposeRule()

    private fun settle(rounds: Int = 6) {
        repeat(rounds) {
            Thread.sleep(100)
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeBy(160)
        }
    }

    private fun run(dir: String, clear: Float) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            FakeAndroidKeyStore.install()
            LiteSettings(ctx).saveLiquidGlass(true)
            LiteSettings(ctx).saveGlassTransparency(clear)
        }
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java)
        settle(10)
        compose.onNodeWithText("Настройки").performClick()
        settle(12)
        repeat(6) { i ->
            captureScreenRoboImage("build/outputs/roborazzi/navlegible/$dir/$i.png")
            // Each step brings the next item of the list to the top.
            compose.onAllNodes(hasScrollToIndexAction())[0].performScrollToIndex(i + 1)
            settle(6)
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun lightClear() = run("light_100", 1f)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkClear() = run("dark_100", 1f)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun lightSeventyFive() = run("light_75", 0.75f)
}
