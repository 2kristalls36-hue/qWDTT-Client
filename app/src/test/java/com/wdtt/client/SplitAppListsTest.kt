package com.wdtt.client

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplitAppListsTest {
    private val store get() = SettingsStore(ApplicationProvider.getApplicationContext())

    @Test fun selectionGoesToTheCurrentMode() {
        assertEquals("" to "a,b", store.splitAppLists(isWhitelist = true, current = "a,b"))
        assertEquals("a,b" to "", store.splitAppLists(isWhitelist = false, current = "a,b"))
    }

    @Test fun listsAreIndependent() = runBlocking {
        store.migrateSplitAppLists()
        store.saveExcludedApps("org.example.c")
        store.saveAllowedApps("org.example.a,org.example.b")
        store.saveExceptionsMode(true)
        store.saveExcludedApps("org.example.d")
        assertEquals("org.example.a,org.example.b", store.allowedApps.first())
        store.saveExceptionsMode(false)
        store.saveAllowedApps("org.example.e")
        assertEquals("org.example.d", store.excludedApps.first())
        assertEquals("org.example.e", store.allowedApps.first())
    }
}
