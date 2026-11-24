package com.wdtt.client.ui

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.wdtt.client.MainActivity
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.testing.FakeAndroidKeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/** Frames of the selected tab accent at each power level, for review (GIFs are made from them). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavAccentPreview {
    @get:Rule val compose = createEmptyComposeRule()

    private fun frames(dir: String, level: PowerLevel, count: Int, stepMs: Long, skipMs: Long = 0) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            FakeAndroidKeyStore.install()
            LiteSettings(ctx).savePowerLevel(level)
            LiteSettings(ctx).saveLiquidGlass(true)
            LiteSettings(ctx).saveGlassTransparency(1f)
        }
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java)
        repeat(10) { Thread.sleep(100); ShadowLooper.idleMainLooper(); compose.mainClock.advanceTimeBy(160) }
        // Balanced: wait for the start of the next sheen cycle.
        if (skipMs > 0) { compose.mainClock.advanceTimeBy(skipMs); ShadowLooper.idleMainLooper() }
        repeat(count) { i ->
            captureScreenRoboImage("build/outputs/roborazzi/navaccent/$dir/%02d.png".format(i))
            compose.mainClock.advanceTimeBy(stepMs)
            ShadowLooper.idleMainLooper()
        }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun lightStable() = frames("light_stable", PowerLevel.Stable, 1, 100)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun lightBalanced() = frames("light_balanced", PowerLevel.Balanced, 24, 80, skipMs = 5200)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun lightMax() = frames("light_max", PowerLevel.Maximum, 26, 100)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkBalanced() = frames("dark_balanced", PowerLevel.Balanced, 24, 80, skipMs = 5200)

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkMax() = frames("dark_max", PowerLevel.Maximum, 26, 100)
}
