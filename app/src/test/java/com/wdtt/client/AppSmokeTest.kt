package com.wdtt.client

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Launches the real activity and walks the main screens; any crash fails the test. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xxhdpi")
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun step(ms: Long = 800) = compose.mainClock.advanceTimeBy(ms)

    @Test fun walkThroughScreens() {
        compose.mainClock.autoAdvance = false
        step()
        compose.onNodeWithText("Подключиться").assertExists()
        // Connect without a VK call link: must explain, not crash.
        compose.onNodeWithText("Подключиться").performClick()
        // The check runs on a background thread and posts back to the main thread.
        Thread.sleep(1500)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        step(2000)
        // No profile: the connect button leads to the profiles tab.
        compose.onNodeWithText("Профилей нет").assertExists()
        compose.onNodeWithText("Добавить профиль").performClick()
        step(1500)
        compose.onNodeWithText("Вставить из буфера обмена").assertExists()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        step(1000)
        compose.onNodeWithText("Обход").performClick()
        step(3000)
        compose.onNodeWithText("Главная").performClick()
        step(1500)
    }
}
