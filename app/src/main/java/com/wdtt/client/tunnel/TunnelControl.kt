package com.wdtt.client

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import com.wdtt.client.lite.TunnelActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TunnelControl {

    fun stop(context: Context) {
        val stopIntent = Intent(context, TunnelService::class.java).apply { action = "STOP" }
        context.startService(stopIntent)
    }

    /**
     * Every entry point (button, widget, quick settings tile, shortcut) starts here:
     * the active profile, RAW mode and the chosen power are applied first. Without
     * a profile or a VK call link the app opens instead of failing silently.
     */
    fun startFromSavedSettings(context: Context) {
        val appContext = context.applicationContext
        TunnelManager.scope.launch {
            val problem = TunnelActions.prepareActive(appContext)
            withContext(Dispatchers.Main) {
                if (problem != null) {
                    Toast.makeText(appContext, TunnelActions.problemText(problem), Toast.LENGTH_LONG).show()
                    appContext.startActivity(
                        Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    TunnelManager.cancelConnectingIfNeeded()
                    return@withContext
                }
                val startIntent = Intent(appContext, TunnelService::class.java).apply { action = "START_SAVED" }
                if (Build.VERSION.SDK_INT >= 26) appContext.startForegroundService(startIntent)
                else appContext.startService(startIntent)
            }
        }
    }
}
