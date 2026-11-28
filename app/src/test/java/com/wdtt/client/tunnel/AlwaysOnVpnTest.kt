package com.wdtt.client.tunnel

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wdtt.client.RawTunVpnService
import com.wdtt.client.TunnelManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** «Постоянная VPN»: a start of the VPN service by the system connects; the app's own does not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlwaysOnVpnTest {
    private val starts = mutableListOf<Context>()
    private val original = RawTunVpnService.startSaved

    @Before fun hook() {
        RawTunVpnService.startSaved = { starts += it }
        TunnelManager.running.value = false
        TunnelManager.isConnecting.value = false
    }

    @After fun unhook() {
        RawTunVpnService.startSaved = original
        TunnelManager.isConnecting.value = false
    }

    private fun start(intent: Intent?) {
        Robolectric.buildService(RawTunVpnService::class.java, intent).create().startCommand(0, 1).destroy()
    }

    @Test fun theSystemStartConnects() {
        start(Intent(VpnService.SERVICE_INTERFACE))
        assertEquals(1, starts.size)
    }

    @Test fun theAppsOwnStartDoesNot() {
        start(null)
        start(Intent())
        assertEquals(0, starts.size)
    }

    @Test fun aSessionUnderWayIsLeftAsItIs() {
        TunnelManager.isConnecting.value = true
        start(Intent(VpnService.SERVICE_INTERFACE))
        assertEquals(0, starts.size)
    }
}
