package com.wdtt.client.ui.apps

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InstalledAppsTest {
    @Test fun theListIsReadOnceAndKept() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Kept for the whole process: another test may have read it a moment ago.
        InstalledApps.forget()
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = "org.example.maps"
                applicationInfo = ApplicationInfo().apply { packageName = "org.example.maps"; name = "Maps" }
            },
        )
        InstalledApps.refresh(context)
        val first = InstalledApps.list.value
        assertNotNull("read", first)
        assertTrue("with the installed app", first!!.any { it.packageName == "org.example.maps" })
        // Opened again a moment later: the list kept, not read again.
        InstalledApps.refresh(context)
        assertSame("kept", first, InstalledApps.list.value)
    }
}
