package com.wdtt.client

import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.lite.RawLog
import com.wdtt.client.lite.SubscriptionInfo
import com.wdtt.client.testing.FakeAndroidKeyStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Walks the whole interface of the real app with seeded profiles and captures every
 * screen. Any crash fails the test; the PNGs (build/outputs/roborazzi/tour) are for review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiTour {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun seed(level: PowerLevel, daysLeft: Long) = runBlocking {
        FakeAndroidKeyStore.install()
        val store = ProfilesStore(ctx)
        // The DataStore instance lives for the whole test process: start from an empty list.
        store.profiles.first().forEach { store.deleteProfile(it.id) }
        val home = store.createProfile("Дом", "203.0.113.10:56002", "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789abcdefg", 18, 9000, "secret")
        store.createProfile("Работа и длинное название профиля", "198.51.100.7", "", 9, 9000, "secret2")
        store.applyProfile(ctx, home.id)
        val lite = LiteSettings(ctx)
        lite.savePowerLevel(level)
        lite.saveSubscription(
            home.id,
            SubscriptionInfo(
                expiresAt = System.currentTimeMillis() / 1000 + daysLeft * 86400 + 3600,
                maxDevices = 2, boundDevices = 1, checkedAt = System.currentTimeMillis(),
            ),
        )
        RawLog.clear()
        RawLog.marker("запуск клиента: режим rawtun, потоков 18")
        RawLog.add("2026/11/21 08:00:01.000 [КЛИЕНТ] Режим: rawtun")
        RawLog.add("2026/11/21 08:00:03.000 [СТАТИСТИКА] Активных: 18 | Трафик: 0.55 МБ | ↓0.32 МБ / ↑0.23 МБ | Пинг: 25 мс")
    }

    private fun settle(rounds: Int = 6) {
        repeat(rounds) {
            Thread.sleep(120)
            ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeBy(160)
        }
    }

    private fun shot(dir: String, name: String) =
        captureScreenRoboImage("build/outputs/roborazzi/tour/$dir/$name.png")

    @OptIn(ExperimentalTestApi::class)
    private fun tour(dir: String, connected: Boolean) {
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java)
        settle(10)
        if (connected) {
            TunnelManager.running.value = true
            TunnelManager.connectedSinceMs.value = System.currentTimeMillis() - 754_000
            TunnelManager.pingMs.value = 34
            TunnelManager.downlinkMbps.value = 23.4
            settle(8)
        }
        shot(dir, "01_home")

        // Profile sheet from the chip, top and bottom.
        compose.onAllNodesWithText("Дом")[0].performClick()
        settle(10)
        shot(dir, "02_profile_sheet")
        // Swipes, not performScrollTo: that waits for idle, which never comes while connected.
        swipeSheetUp("ПОДПИСКА")
        shot(dir, "03_profile_sheet_bottom")
        compose.onNodeWithText("Перенести на другое устройство").performClick()
        settle(10)
        shot(dir, "04_transfer")
        swipeSheetUp("Перенос на другое устройство", optional = true)
        shot(dir, "04b_transfer_bottom")
        if (!connected) {
            // While connected the button is disabled by design; the dialog only exists when off.
            compose.onNodeWithText("Завершить перенос").performClick()
            settle(6)
            compose.onNode(androidx.compose.ui.test.isDialog())
                .captureRoboImage("build/outputs/roborazzi/tour/$dir/05_transfer_confirm.png")
            compose.onNodeWithText("Отмена").performClick()
            settle(6)
        }
        pressBack(); settle(8) // transfer → profile sheet
        pressBack(); settle(8) // profile sheet → home

        // Hidden log.
        compose.onNodeWithText("qWDTT Lite").performTouchInput { longClick() }
        settle(8)
        shot(dir, "06_logs")
        pressBack(); settle(8)

        // Profiles tab, add sheet, import preview.
        compose.onNodeWithText("Профили").performClick()
        settle(8)
        shot(dir, "07_profiles")
        compose.onAllNodesWithContentDescription("Добавить профиль")[0].performClick()
        settle(8)
        shot(dir, "08_add_sheet")
        pressBack(); settle(8)
        MainActivity.pendingFileUri.value = Uri.parse(
            "qwdtt://config?name=%D0%A2%D0%B5%D1%81%D1%82&peer=127.0.0.1%3A1&hashes=&workers=18&port=9000&pass=x"
        )
        settle(20)
        shot(dir, "09_import_preview")
        pressBack(); settle(8)
        compose.onNodeWithText("Работа и длинное название профиля").performClick()
        settle(10)
        shot(dir, "10_other_profile_sheet")
        pressBack(); settle(8)

        compose.onNodeWithText("Приложения").performClick()
        settle(14)
        shot(dir, "11_apps")
        compose.onNodeWithText("Главная").performClick()
        settle(8)
        shot(dir, "12_home_again")
        TunnelManager.running.value = false
    }

    private fun swipeSheetUp(anchor: String, optional: Boolean = false) {
        val nodes = compose.onAllNodesWithText(anchor).fetchSemanticsNodes()
        if (nodes.isEmpty()) {
            if (optional) return else error("no node $anchor")
        }
        compose.onAllNodesWithText(anchor)[0].performTouchInput { swipeUp(startY = centerY, endY = centerY - 1200f, durationMillis = 300) }
        settle(6)
    }

    private fun pressBack() {
        androidx.test.espresso.Espresso.pressBackUnconditionally()
    }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    fun lightBalanced() { seed(PowerLevel.Balanced, 23); tour("light_411", connected = false) }

    @Test @Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun darkMaxConnected() { seed(PowerLevel.Maximum, 3); tour("dark_411_max_on", connected = true) }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h740dp-xhdpi")
    fun smallStable() { seed(PowerLevel.Stable, 1); tour("light_360_stable", connected = true) }

    @Test @Config(sdk = [35], qualifiers = "w360dp-h740dp-night-xhdpi", fontScale = 1.3f)
    fun smallDarkLargeFont() { seed(PowerLevel.Balanced, 0); tour("dark_360_font130", connected = false) }
}
