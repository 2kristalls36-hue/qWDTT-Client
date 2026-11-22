package com.wdtt.client.lite

import com.wdtt.client.ConnectionPipelineState
import com.wdtt.client.ConnectionStep
import com.wdtt.client.ConnectionTransportKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStagesTest {
    private fun raw(current: ConnectionStep, completed: Set<ConnectionStep> = emptySet(), failed: ConnectionStep? = null) =
        ConnectionPipelineState(current = current, completed = completed, failed = failed, visible = true,
            transportKind = ConnectionTransportKind.RAW, dtlsUsed = false)

    @Test fun rawHasSixStages() {
        val v = ConnectionStages.view(raw(ConnectionStep.DNS))!!
        assertEquals(0, v.index); assertEquals(6, v.total); assertEquals("Проверка сети", v.title)
    }

    @Test fun progressAndHiddenStep() {
        assertEquals(4, ConnectionStages.view(raw(ConnectionStep.TURN))!!.index - 0 + 1)
        // The handshake step is not shown in RAW: it counts as the next shown stage.
        val done = setOf(ConnectionStep.DNS, ConnectionStep.VK, ConnectionStep.WRAP, ConnectionStep.TURN)
        assertEquals(4, ConnectionStages.view(raw(ConnectionStep.DTLS, done))!!.index)
    }

    @Test fun failedAndDone() {
        val f = ConnectionStages.view(raw(ConnectionStep.TURN, failed = ConnectionStep.TURN))!!
        assertTrue(f.failed)
        assertEquals("Этап «Подключение через звонок» не выполнен.", ConnectionStages.line(f))
        assertEquals(6, ConnectionStages.view(raw(ConnectionStep.DONE))!!.index)
        assertNull(ConnectionStages.view(ConnectionPipelineState()))
    }

    @Test fun noTechnicalWords() {
        ConnectionStep.values().forEach { s ->
            val t = ConnectionStages.title(s)
            listOf("DNS", "TURN", "DTLS", "VPN", "WireGuard", "RTP", "SOCKS", "поток").forEach { w ->
                assertTrue("$s: $t", !t.contains(w, ignoreCase = true))
            }
        }
    }
}
