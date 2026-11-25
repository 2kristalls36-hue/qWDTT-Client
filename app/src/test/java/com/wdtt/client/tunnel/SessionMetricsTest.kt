package com.wdtt.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionMetricsTest {
    @Test
    fun summaryOfASession() {
        var t = 1_000_000L
        val m = SessionMetrics { t }
        assertNull(m.stop())
        m.start()
        t += 7_400
        assertEquals(7_400L, m.ready())
        assertNull(m.ready()) // once per session
        m.join(); m.join(); m.join()
        m.reconnect("смена сети"); m.reconnect("смена сети"); m.reconnect("зомби-процесс")
        m.vkLimit()
        t += 2 * 3_600_000L + 13 * 60_000L
        assertEquals(
            "[ИТОГ] Сессия 2 ч 13 мин; подключение за 7,4 с; переподключений 3 (смена сети 2, зомби-процесс 1); " +
                "входов в звонок 3; капч 0; ограничений VK 1",
            m.stop(),
        )
        assertNull(m.stop())
    }

    @Test
    fun notConnectedAndCountsIgnoredOutsideASession() {
        var t = 0L
        val m = SessionMetrics { t }
        m.join(); m.reconnect("x") // before start: ignored
        m.start()
        t += 90_000
        val s = m.stop()!!
        assertTrue(s, s.contains("не подключено"))
        assertTrue(s, s.contains("переподключений 0;"))
        assertTrue(s, s.contains("входов в звонок 0"))
    }
}
