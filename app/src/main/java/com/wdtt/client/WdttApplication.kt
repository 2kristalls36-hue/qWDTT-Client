package com.wdtt.client

import android.app.Application
import android.content.Context
import com.wireguard.android.backend.GoBackend

class WdttApplication : Application() {
    @Volatile
    private var backendInstance: GoBackend? = null

    val backend: GoBackend
        get() = getBackend(this)

    override fun onCreate() {
        super.onCreate()
        com.wdtt.client.lite.CrashLog.install(this)
        // The crash report process needs nothing else from the app.
        if (com.wdtt.client.lite.CrashLog.isCrashProcess(this)) return
        NotificationHelper.ensureTunnelChannel(this)
        AppShortcuts.refreshAsync(this)
        com.wdtt.client.lite.ExpiryWarningWorker.schedule(this)
    }

    fun getBackend(context: Context): GoBackend {
        return backendInstance ?: synchronized(this) {
            backendInstance ?: GoBackend(context.applicationContext).also { backendInstance = it }
        }
    }
}
