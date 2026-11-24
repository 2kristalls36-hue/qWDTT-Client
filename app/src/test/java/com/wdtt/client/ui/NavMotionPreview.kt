package com.wdtt.client.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

/** Frames of the tab bar in motion (profiles icon, dragging the droplet), for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavMotionPreview {
    @get:Rule val compose = createEmptyComposeRule()

    private fun start(level: PowerLevel, clear: Float = 0.5f) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        runBlocking {
            FakeAndroidKeyStore.install()
            LiteSettings(ctx).savePowerLevel(level)
            LiteSettings(ctx).saveLiquidGlass(true)
            LiteSettings(ctx).saveGlassTransparency(clear)
        }
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java)
        repeat(10) { Thread.sleep(100); ShadowLooper.idleMainLooper(); compose.mainClock.advanceTimeBy(160) }
    }

    private fun frame(dir: String, i: Int) =
        captureScreenRoboImage("build/outputs/roborazzi/navmotion/$dir/%02d.png".format(i))

    private fun step(ms: Long) { compose.mainClock.advanceTimeBy(ms); ShadowLooper.idleMainLooper() }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun profilesIcon() {
        start(PowerLevel.Balanced)
        compose.onNodeWithText("Профили").performClick()
        repeat(20) { i -> frame("folder", i); step(60) }
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun dragAcross() {
        start(PowerLevel.Maximum, clear = 1f)
        val from = compose.onNodeWithText("Главная").fetchSemanticsNode().boundsInRoot.center
        val to = compose.onNodeWithText("Настройки").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(from) }
        step(100)
        repeat(16) { i ->
            val f = (i + 1) / 16f
            compose.onRoot().performTouchInput { moveTo(Offset(from.x + (to.x - from.x) * f, from.y)) }
            step(60)
            frame("drag", i)
        }
        compose.onRoot().performTouchInput { up() }
        repeat(6) { i -> step(80); frame("drag", 16 + i) }
    }
}
