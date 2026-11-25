package com.wdtt.client.lite

import com.wdtt.client.lite.TrustedWifi.Transition
import com.wdtt.client.lite.TrustedWifi.WifiState
import com.wdtt.client.ui.home.accessNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedWifiTest {
    private val home = setOf("Home")
    private fun on(ssid: String) = WifiState(connected = true, ssid = ssid)

    @Test
    fun pausesOnTrustedNetworkWhileConnected() {
        assertEquals(Transition.Pause, TrustedWifi.decide(true, tunnelActive = true, paused = false, wifi = on("Home"), trusted = home))
        // Case differs between APIs on some firmware.
        assertEquals(Transition.Pause, TrustedWifi.decide(true, true, false, on("HOME"), home))
    }

    @Test
    fun nothingWhenNotConnectedOrOtherNetwork() {
        assertEquals(Transition.None, TrustedWifi.decide(true, tunnelActive = false, paused = false, wifi = on("Home"), trusted = home))
        assertEquals(Transition.None, TrustedWifi.decide(true, true, false, on("Cafe"), home))
        assertEquals(Transition.None, TrustedWifi.decide(true, true, false, WifiState(false), home))
    }

    @Test
    fun unknownNameNeverPauses() {
        assertEquals(Transition.None, TrustedWifi.decide(true, true, false, WifiState(true, ""), home))
        assertEquals(
            Transition.None,
            TrustedWifi.decide(true, true, false, WifiState(true, "Home", TrustedWifi.AccessProblem.Permission), home),
        )
    }

    @Test
    fun resumesOnLeavingTheNetwork() {
        assertEquals(Transition.Resume, TrustedWifi.decide(true, false, paused = true, wifi = on("Cafe"), trusted = home))
        assertEquals(Transition.Resume, TrustedWifi.decide(true, false, true, WifiState(false), home))
    }

    @Test
    fun staysPausedWhileStillOnTheNetworkOrNameFlickers() {
        assertEquals(Transition.None, TrustedWifi.decide(true, false, true, on("Home"), home))
        assertEquals(Transition.None, TrustedWifi.decide(true, false, true, WifiState(true, ""), home))
    }

    @Test
    fun turningOffOrEmptyingTheListResumes() {
        assertEquals(Transition.Resume, TrustedWifi.decide(false, false, true, on("Home"), home))
        assertEquals(Transition.Resume, TrustedWifi.decide(true, false, true, on("Home"), emptySet()))
        assertEquals(Transition.None, TrustedWifi.decide(false, true, false, on("Home"), home))
    }

    @Test
    fun sanitizeKeepsTheVisibleName() {
        assertEquals("Home", TrustedWifi.sanitize("\"Home\""))
        assertEquals("", TrustedWifi.sanitize("   "))
        assertEquals("Дом 5G", TrustedWifi.sanitize("Дом\u0007 5G"))
        assertTrue(TrustedWifi.sanitize("я".repeat(40)).toByteArray().size <= 32)
    }

    @Test
    fun accessNotes() {
        assertEquals("Устройство не подключено к Wi‑Fi.", accessNote(WifiState(false), background = true))
        assertEquals(
            "Для определения сети Wi‑Fi требуется доступ к геолокации.",
            accessNote(WifiState(true, problem = TrustedWifi.AccessProblem.Permission), background = false),
        )
        assertNull(accessNote(on("Home"), background = true))
    }

    @Test
    fun pausedLooksAgainWhileTheNewNetworkHasNoNameYet() {
        // Moved from the trusted network to another one: its name comes a little later,
        // and the pause must be re-checked until it does (not left as it is).
        val noName = WifiState(connected = true)
        assertEquals(Transition.None, TrustedWifi.decide(true, false, true, noName, home))
        assertTrue(TrustedWifi.shouldLookAgain(true, false, true, noName, home, sinceWifiEventMs = 1_000))
        // Once the name is known, the pause ends.
        assertEquals(Transition.Resume, TrustedWifi.decide(true, false, true, on("Work"), home))
        // Not forever: only shortly after a Wi‑Fi event, and not without access.
        assertTrue(!TrustedWifi.shouldLookAgain(true, false, true, noName, home, sinceWifiEventMs = TrustedWifi.SSID_WAIT_MS))
        assertTrue(
            !TrustedWifi.shouldLookAgain(
                true, false, true,
                WifiState(true, problem = TrustedWifi.AccessProblem.Permission), home, sinceWifiEventMs = 1_000,
            ),
        )
        // Neither paused nor connected: nothing to look for.
        assertTrue(!TrustedWifi.shouldLookAgain(true, false, false, noName, home, sinceWifiEventMs = 1_000))
    }
}
