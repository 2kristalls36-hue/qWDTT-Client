package com.wdtt.client

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.wdtt.client.lite.StartupSettings
import kotlinx.coroutines.launch
import com.wdtt.client.ui.AppRoot
import com.wdtt.client.ui.theme.ArdttTheme

class MainActivity : ComponentActivity() {

    private val batteryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        checkAndRequestBattery()
    }

    /** VPN consent lives on the Activity so it survives tab switches. */
    @Volatile
    private var pendingAfterVpnGranted: (() -> Unit)? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val cont = pendingAfterVpnGranted
        pendingAfterVpnGranted = null
        if (cont == null) return@registerForActivityResult
        if (android.net.VpnService.prepare(this) == null) {
            cont()
        } else {
            TunnelManager.cancelConnectingIfNeeded()
            Toast.makeText(this, "Разрешение на VPN не выдано.", Toast.LENGTH_SHORT).show()
        }
    }

    fun prepareVpnThen(onGranted: () -> Unit) {
        val vpnIntent = android.net.VpnService.prepare(this)
        if (vpnIntent != null) {
            pendingAfterVpnGranted = onGranted
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            onGranted()
        }
    }

    companion object {
        var activeActivities = 0
        var isForeground: Boolean
            get() = activeActivities > 0
            @Suppress("UNUSED_PARAMETER") set(value) {}

        var currentActivity: MainActivity? = null

        /** Link or file opened from outside (qwdtt://, .qwdtt, .conf), waiting for import. */
        val pendingFileUri = mutableStateOf<Uri?>(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        when (intent?.action) {
            AppShortcuts.ACTION_START_TUNNEL -> {
                if (!TunnelManager.running.value && !TunnelManager.isConnecting.value) {
                    prepareVpnThen { TunnelControl.startFromSavedSettings(applicationContext) }
                }
            }
            AppShortcuts.ACTION_STOP_TUNNEL -> TunnelControl.stop(applicationContext)
            Intent.ACTION_VIEW -> intent.data?.let { pendingFileUri.value = it }
        }
    }

    override fun onStart() {
        super.onStart()
        activeActivities++
        currentActivity = this
        ManlCaptchaWebViewManager.checkAndShowPendingCaptcha(this)
        VkAuthWebViewManager.checkAndShowPendingAuth(this)
    }

    override fun onStop() {
        super.onStop()
        activeActivities--
        if (currentActivity == this) currentActivity = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestNotifications()
        handleIncomingIntent(intent)

        // The first frame waits for the saved theme, power level and glass (at most a second;
        // the window's own background shows meanwhile): the app opens as it was left, without
        // showing the defaults first and jumping to the saved values.
        lifecycleScope.launch { StartupSettings.load(applicationContext) }
        val started = android.os.SystemClock.uptimeMillis()
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val go = StartupSettings.ready || android.os.SystemClock.uptimeMillis() - started > 1000
                if (go) content.viewTreeObserver.removeOnPreDrawListener(this)
                return go
            }
        })

        setContent {
            val settingsStore = remember { SettingsStore(this) }
            val savedTheme by settingsStore.themeMode.collectAsStateWithLifecycle(initialValue = null)
            val themeMode = savedTheme ?: StartupSettings.themeMode ?: "system"
            ArdttTheme(themeMode = themeMode) {
                AppRoot(settingsStore = settingsStore)
            }
        }
    }

    private fun checkAndRequestNotifications() {
        NotificationHelper.ensureTunnelChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            checkAndRequestBattery()
        }
    }

    private fun checkAndRequestBattery() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                batteryLauncher.launch(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            } catch (_: Exception) {
                // The system dialog is not available on this device.
            }
        }
    }
}
