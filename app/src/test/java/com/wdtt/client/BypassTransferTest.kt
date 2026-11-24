package com.wdtt.client

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wdtt.client.lite.BypassTransfer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bypass lists export/import, checked against qWDTT's own BypassShare code. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BypassTransferTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<Context>()
    private val s get() = SettingsStore(ctx)

    private suspend fun seed(bypass: String, allowed: String, onlySelected: Boolean, sites: String) {
        s.migrateSplitAppLists()
        s.saveExcludedApps(bypass)
        s.saveAllowedApps(allowed)
        s.saveExceptionsMode(onlySelected)
        s.saveBypassRoutes(sites)
    }

    @Test fun roundTripKeepsBothLists() = runBlocking {
        seed("org.a,org.b", "org.c", onlySelected = true, sites = "example.com\n203.0.113.0/24")
        val json = BypassTransfer.export(ctx)
        seed("", "", onlySelected = false, sites = "")
        BypassTransfer.apply(ctx, BypassTransfer.preview(json))
        assertEquals("org.a,org.b", s.excludedApps.first())
        assertEquals("org.c", s.allowedApps.first())
        assertEquals(true, s.isWhitelist.first())
        assertEquals("example.com\n203.0.113.0/24", s.bypassRoutes.first())
    }

    @Test fun fileFromQwdttGoesToItsMode() = runBlocking {
        // qWDTT writes one list and a mode: produce it with qWDTT's own exporter.
        seed("", "", onlySelected = true, sites = "example.org")
        s.saveExcludedApps("org.q1,org.q2") // qWDTT keeps its single list here
        val qwdttJson = BypassShare.exportJson(s)
        seed("org.keep", "org.old", onlySelected = false, sites = "")
        val p = BypassTransfer.preview(qwdttJson)
        assertTrue(p.fromQwdtt)
        assertNull(p.bypassApps) // the other mode's list is left as it is
        BypassTransfer.apply(ctx, p)
        assertEquals("org.q1,org.q2", s.allowedApps.first())
        assertEquals("org.keep", s.excludedApps.first())
        assertEquals(true, s.isWhitelist.first())
        assertEquals("example.org", s.bypassRoutes.first())
    }

    @Test fun ourFileOpensInQwdtt() = runBlocking {
        seed("org.a", "org.c,org.d", onlySelected = true, sites = "example.com")
        val json = BypassTransfer.export(ctx)
        seed("", "", onlySelected = false, sites = "")
        // qWDTT's importer: takes the current mode's list and the mode.
        val r = BypassShare.importJson(s, json)
        assertEquals(2, r.apps)
        assertEquals(true, r.isWhitelist)
        assertEquals("org.c,org.d", s.excludedApps.first())
        assertEquals("example.com", s.bypassRoutes.first())
    }

    @Test fun plainSitesAndRejects() {
        val p = BypassTransfer.preview("example.com\n203.0.113.7\nnot a site")
        assertEquals(listOf("example.com", "203.0.113.7"), p.sites)
        assertNull(p.bypassApps)
        val bad = runCatching { BypassTransfer.preview("{\"format\":\"something-else\"}") }
        assertEquals("Файл не является файлом обхода qWDTT.", bad.exceptionOrNull()?.message)
        assertTrue(runCatching { BypassTransfer.preview("   ") }.isFailure)
    }

    @Test fun profileLinkHasPriorityOverDefault() = runBlocking {
        com.wdtt.client.testing.FakeAndroidKeyStore.install()
        val store = ProfilesStore(ctx)
        val own = store.createProfile("Own", "203.0.113.10:56002", "ownhash123", 9, 9000, "x")
        val none = store.createProfile("None", "203.0.113.11:56002", "", 9, 9000, "x")
        s.saveGlobalVkHashes("defaulthash456")
        store.applyProfile(ctx, own.id)
        assertEquals("ownhash123", s.vkHashes.first())
        store.applyProfile(ctx, none.id)
        assertEquals("defaulthash456", s.vkHashes.first())
    }
}
