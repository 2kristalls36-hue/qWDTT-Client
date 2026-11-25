package com.wdtt.client.lite

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Trusted Wi‑Fi networks: while the device is on one of them the connection is paused
 * (the tunnel and the VPN are down, the service waits), and it comes back when the
 * device leaves the network.
 *
 * The network name (SSID) is only readable with access to location; in the background
 * Android 10+ also asks for location «all the time».
 */
object TrustedWifi {
    /** After Wi‑Fi appears, how long to wait for its name before deciding. */
    const val SSID_WAIT_MS = 8_000L
    const val SSID_RETRY_MS = 1_000L
    /** Delay before pausing: Wi‑Fi often reports itself before it carries traffic. */
    const val ENTER_DELAY_MS = 2_000L
    /** Delay before resuming while paused: the name flickers while the network settles. */
    const val EXIT_DELAY_MS = 5_000L

    private const val UNKNOWN_SSID = "<unknown ssid>"
    private const val BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION"

    enum class AccessProblem { Permission, BackgroundPermission, LocationOff }

    data class WifiState(
        val connected: Boolean,
        val ssid: String = "",
        val problem: AccessProblem? = null,
    ) {
        val ssidKnown: Boolean get() = connected && ssid.isNotBlank() && problem == null
    }

    enum class Transition { None, Pause, Resume }

    fun decide(
        enabled: Boolean,
        tunnelActive: Boolean,
        paused: Boolean,
        wifi: WifiState,
        trusted: Set<String>,
    ): Transition {
        // The feature turned off or the list emptied while paused: resume.
        if (!enabled || trusted.isEmpty()) return if (paused) Transition.Resume else Transition.None
        if (paused) {
            if (!wifi.connected) return Transition.Resume
            // The name often disappears for a moment while paused; stay while Wi‑Fi is up.
            if (!wifi.ssidKnown) return Transition.None
            return if (isTrusted(wifi.ssid, trusted)) Transition.None else Transition.Resume
        }
        if (!tunnelActive || !wifi.ssidKnown) return Transition.None
        return if (isTrusted(wifi.ssid, trusted)) Transition.Pause else Transition.None
    }

    /**
     * Wi‑Fi is up but its name is not known yet, shortly after a Wi‑Fi event: look again.
     * Also while paused: after a move from the trusted network to another one the name
     * of the new network comes a little later, and the pause must end when it does.
     */
    fun shouldLookAgain(
        enabled: Boolean,
        tunnelActive: Boolean,
        paused: Boolean,
        wifi: WifiState,
        trusted: Set<String>,
        sinceWifiEventMs: Long,
    ): Boolean =
        enabled && trusted.isNotEmpty() && (paused || tunnelActive) &&
            wifi.connected && wifi.problem == null && !wifi.ssidKnown &&
            sinceWifiEventMs < SSID_WAIT_MS

    /** Case-insensitive: some firmware changes the case of the name between APIs. */
    fun isTrusted(ssid: String, trusted: Set<String>): Boolean =
        ssid.isNotBlank() && trusted.any { it.equals(ssid, ignoreCase = true) }

    /** Name as the user sees it: no quotes, no control characters, at most 32 bytes. */
    fun sanitize(value: String): String {
        val clean = value.filterNot { Character.isISOControl(it) }.trim().removeSurrounding("\"").trim()
        val out = StringBuilder()
        var bytes = 0
        for (ch in clean) {
            val n = ch.toString().toByteArray(Charsets.UTF_8).size
            if (bytes + n > 32) break
            out.append(ch)
            bytes += n
        }
        return out.toString()
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun accessProblem(context: Context, requireBackground: Boolean): AccessProblem? {
        if (!hasLocationPermission(context)) return AccessProblem.Permission
        if (requireBackground && !hasBackgroundPermission(context)) return AccessProblem.BackgroundPermission
        val lm = context.getSystemService(LocationManager::class.java)
        val on = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) lm?.isLocationEnabled == true
            else lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true || lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        }.getOrDefault(false)
        return if (on) null else AccessProblem.LocationOff
    }

    /** Current Wi‑Fi (not VPN) and its name, if it can be read. */
    @Suppress("DEPRECATION")
    fun read(context: Context, requireBackground: Boolean = true): WifiState {
        val app = context.applicationContext
        val wm = app.getSystemService(WifiManager::class.java)
        if (runCatching { wm?.isWifiEnabled == true }.getOrDefault(false).not()) return WifiState(false)
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val wifiCaps = runCatching {
            cm?.allNetworks?.mapNotNull { cm.getNetworkCapabilities(it) }?.firstOrNull {
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            }
        }.getOrNull() ?: return WifiState(false)

        accessProblem(app, requireBackground)?.let { return WifiState(true, problem = it) }

        val fromCaps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { (wifiCaps.transportInfo as? WifiInfo)?.ssid }.getOrNull()
        } else null
        val fromManager = runCatching { wm?.connectionInfo?.ssid }.getOrNull()
        val ssid = sanitize(
            listOf(fromCaps, fromManager)
                .firstOrNull { !it.isNullOrBlank() && !it.equals(UNKNOWN_SSID, ignoreCase = true) }
                .orEmpty(),
        )
        return WifiState(true, ssid)
    }
}
