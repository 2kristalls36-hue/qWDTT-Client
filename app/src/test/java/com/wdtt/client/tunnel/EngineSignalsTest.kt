package com.wdtt.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineSignalsTest {
    @Test
    fun vkWaitLine() {
        assertEquals(60, EngineSignals.vkWaitSeconds("VK_WAIT|60"))
        assertEquals(0, EngineSignals.vkWaitSeconds("VK_WAIT|0"))
        assertEquals(0, EngineSignals.vkWaitSeconds("VK_WAIT|-3"))
        assertNull(EngineSignals.vkWaitSeconds("VK_WAIT|abc"))
        assertNull(EngineSignals.vkWaitSeconds("[ГРУППА #1] VK ограничил запросы — повтор через 1m0s"))
        assertNull(EngineSignals.vkWaitSeconds("VK_AUTH_REQUIRED|hash"))
    }

    @Test
    fun zombieRestartsSlowDown() {
        assertEquals(90_000L, EngineSignals.zombieRestartDelayMs(0))
        assertEquals(180_000L, EngineSignals.zombieRestartDelayMs(1))
        assertEquals(360_000L, EngineSignals.zombieRestartDelayMs(2))
        assertEquals(600_000L, EngineSignals.zombieRestartDelayMs(3))
        assertEquals(600_000L, EngineSignals.zombieRestartDelayMs(10))
        assertEquals(90_000L, EngineSignals.zombieRestartDelayMs(-1))
    }
}
