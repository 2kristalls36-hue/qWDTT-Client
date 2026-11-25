package com.wdtt.client.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class JournalShareTest {
    @Test
    fun fileNameCarriesTheTime() {
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("qwdtt-client-log-20261231-235959.txt", JournalShare.fileName(Date(1798761599000L)))
        } finally {
            TimeZone.setDefault(tz)
        }
    }

    @Test
    fun textHasHeaderThenLinesUnchanged() {
        val lines = listOf("===== 15:42:20.901 запуск клиента =====", "[СТАТИСТИКА] Активных: 9")
        val text = JournalShare.text(lines, Date(0), "1.2.0 (100)", "Android 15 (SDK 35), Google Pixel")
        val out = text.lines()
        assertEquals("qWDTT Client 1.2.0 (100)", out[0])
        assertEquals("Android 15 (SDK 35), Google Pixel", out[1])
        assertTrue(out[2].startsWith("Сохранено: "))
        assertEquals("", out[3])
        assertEquals(lines, out.subList(4, 6))
    }
}
