package com.wdtt.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.lite.RawLog
import com.wdtt.client.ui.home.LogsSheet
import com.wdtt.client.ui.profiles.ProfileSheet
import com.wdtt.client.ui.theme.ArdttTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Profile and log sheets rendered for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1100dp-xxhdpi")
class SheetScreenshots {
    @get:Rule val compose = createComposeRule()

    private val profile = ConnectionProfile(
        id = "p1", name = "111", peer = "203.0.113.10:56000",
        vkHashes = "abcDEF123", workersPerHash = 18, listenPort = 9000, password = "secret-pass",
    )

    @Test fun profileSheetDark() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme("dark") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ProfileSheet(profile = profile, isActive = true, ping = 0L, onDismiss = {}, onOtherProfiles = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        captureScreenRoboImage("build/outputs/roborazzi/sheet_profile.png")
    }

    @Test fun logsSheetDark() {
        RawLog.clear()
        RawLog.marker("запуск клиента: режим rawtun, потоков 18")
        listOf(
            "2026/11/20 23:35:01.118 [Основной] Хешей=1, Потоков=18",
            "2026/11/20 23:35:01.120 [СЕТЬ] Режим: VPN (raw-IP, без WireGuard)",
            "2026/11/20 23:35:01.402 [СЕТЬ] DNS доступен: Яндекс DNS [OK: 77.88.8.8, 77.88.8.1]",
            "2026/11/20 23:35:01.403 [СЕТЬ] Маскировка: Аудиозвонок (OPUS)",
            "2026/11/20 23:35:01.404 [СЕТЬ] TURN: TCP",
            "2026/11/20 23:35:01.405 [КЛИЕНТ] Режим VK: vkcalls",
            "2026/11/20 23:35:01.406 [WRAP] Ключ выведен из пароля, режим RTP AEAD активен",
            "2026/11/20 23:35:02.911 [ВК] Учетные данные проверены ✓",
            "2026/11/20 23:35:03.507 [ВОРКЕР #1] Ошибка: FATAL_AUTH: неверный пароль подключения",
        ).forEach { RawLog.add(it) }
        RawLog.marker("приложение остановило туннель: Неверный пароль подключения. Воркеры остановлены.")
        RawLog.marker("клиент завершился, код 0")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ArdttTheme("dark") {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    LogsSheet(onDismiss = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(2000)
        captureScreenRoboImage("build/outputs/roborazzi/sheet_logs.png")
    }
}
