package com.wdtt.client.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogErrorsTest {
    @Test fun errorsAreFoundWhateverTheirTagOrLanguage() {
        listOf(
            "[API] challenge: ConnectException: Connection refused (connect failed)",
            "[ВОРКЕР #3] Фатальная ошибка: dial timeout",
            "[STREAM 2] [Captcha] Solve failed: context deadline exceeded",
            "[КАПЧА] не удалось обновить browser_fp: EOF",
            "[VK] call link not created: HTTP 500",
            "[UPDATE] checksum mismatch: abc != def",
            "[RAW] VPN передан другому приложению — подключение остановлено",
            "[DTLS] read: connection reset by peer",
            "panic: runtime error: index out of range",
        ).forEach { assertTrue(it, LogErrors.isError(it)) }
    }

    @Test fun ordinaryLinesAreNotErrors() {
        listOf(
            "2026/11/21 08:00:01.000 [КЛИЕНТ] Режим: rawtun",
            "2026/11/21 08:00:03.000 [СТАТИСТИКА] Активных: 18 | Трафик: 0.55 МБ | Пинг: 25 мс",
            "[КАПЧА] AUTO: WBV Auto попытка 1/2 (timeout 30s)",
            "[VK] call link created for the profile",
            "===== 08:00:00.000 клиент завершился, код 1 =====",
        ).forEach { assertFalse(it, LogErrors.isError(it)) }
    }

    @Test fun onlyTheErrorsWithTheMarkersOfTheirRuns() {
        val start1 = "===== 08:00:00.000 запуск клиента: режим rawtun, потоков 18 ====="
        val stop1 = "===== 08:10:00.000 приложение остановило туннель: пользователь ====="
        val start2 = "===== 09:00:00.000 запуск клиента: режим rawtun, потоков 18 ====="
        val error = "[API] challenge: ConnectException: Connection refused (connect failed)"
        val exit2 = "===== 09:00:05.000 клиент завершился, код 1 ====="
        val lines = listOf(
            start1, "[КЛИЕНТ] Режим: rawtun", "[СТАТИСТИКА] Активных: 18", stop1,
            start2, "[КЛИЕНТ] Режим: rawtun", error, "[КЛИЕНТ] выход", exit2,
        )
        // The run without errors is left out whole; the one with an error keeps its markers.
        assertEquals(listOf(start2, error, exit2), LogErrors.only(lines))
        assertEquals(emptyList<String>(), LogErrors.only(lines.filter { it != error }))
    }
}
