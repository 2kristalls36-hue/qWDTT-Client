package com.wdtt.client.lite

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wdtt.client.TunnelControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Connects after the device boots when «Подключаться при включении устройства» is on.
 * Only when the VPN permission is already granted: the consent dialog needs the user.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.intent.action.QUICKBOOT_POWERON") return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val enabled = LiteSettings(app).connectOnBoot.first()
                val granted = runCatching { VpnService.prepare(app) == null }.getOrDefault(false)
                if (enabled && granted) TunnelControl.startFromSavedSettings(app)
            } finally {
                pending.finish()
            }
        }
    }
}
