package com.wdtt.client.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.TrustedWifi
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import kotlinx.coroutines.launch

/**
 * «Пауза в доверенных сетях Wi‑Fi»: the switch, the list of networks and adding the
 * current one. Only the network the device is on can be added: its name is read from
 * the system, which needs access to location (and, for the background, «all the time»).
 */
@Composable
internal fun TrustedWifiSettings(lite: LiteSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by lite.trustedWifi.collectAsStateWithLifecycle(false)
    val ssids by lite.trustedSsids.collectAsStateWithLifecycle(emptySet())
    var wifi by remember { mutableStateOf(TrustedWifi.read(context, requireBackground = false)) }
    var background by remember { mutableStateOf(TrustedWifi.hasBackgroundPermission(context)) }
    fun refresh() {
        wifi = TrustedWifi.read(context, requireBackground = false)
        background = TrustedWifi.hasBackgroundPermission(context)
    }

    // Back from the system settings or from another network: read again.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(enabled, ssids) { refresh() }

    var addAfterPermission by remember { mutableStateOf(false) }
    var backgroundAfterPermission by remember { mutableStateOf(false) }

    fun addCurrent() {
        val now = TrustedWifi.read(context, requireBackground = false)
        wifi = now
        if (now.ssidKnown) scope.launch { lite.addTrustedSsid(now.ssid) }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        refresh()
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && addAfterPermission) addCurrent()
        if (granted && backgroundAfterPermission && !TrustedWifi.hasBackgroundPermission(context) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        addAfterPermission = false
        backgroundAfterPermission = false
    }

    /** Location first; the background access is a separate step (Android shows it on its own page). */
    fun askAccess(wantBackground: Boolean, addAfter: Boolean) {
        when {
            !TrustedWifi.hasLocationPermission(context) -> {
                addAfterPermission = addAfter
                backgroundAfterPermission = wantBackground
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            wantBackground && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !TrustedWifi.hasBackgroundPermission(context) ->
                backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            addAfter -> addCurrent()
        }
    }

    SwitchLine("Пауза в доверенных сетях Wi‑Fi", enabled) { on ->
        scope.launch { lite.saveTrustedWifi(on) }
        if (on) askAccess(wantBackground = true, addAfter = false)
    }
    Hint(
        if (enabled) "Приложение приостанавливает подключение в доверенных сетях Wi‑Fi.\nПосле выхода из сети подключение восстанавливается."
        else "Приложение не приостанавливает подключение в сетях Wi‑Fi."
    )
    AnimatedVisibility(enabled) {
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ssids.isEmpty()) {
                Hint("Доверенные сети не добавлены.")
            }
            ssids.sortedBy { it.lowercase() }.forEach { ssid ->
                TrustedNetworkRow(ssid, current = wifi.ssidKnown && wifi.ssid.equals(ssid, ignoreCase = true)) {
                    scope.launch { lite.removeTrustedSsid(ssid) }
                }
            }
            accessNote(wifi, background)?.let { Hint(it) }
            val already = wifi.ssidKnown && TrustedWifi.isTrusted(wifi.ssid, ssids)
            ArdttButton(
                text = if (wifi.ssidKnown) "Добавить текущую сеть «${wifi.ssid}»" else "Добавить текущую сеть",
                variant = ArdttButtonVariant.Tonal,
                enabled = wifi.connected && !already,
                onClick = { askAccess(wantBackground = false, addAfter = true) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (!background && TrustedWifi.hasLocationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ArdttButton(
                    text = "Доступ к геолокации в фоновом режиме",
                    variant = ArdttButtonVariant.Tonal,
                    onClick = { askAccess(wantBackground = true, addAfter = false) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Why the current network cannot be read or added; null when nothing to say. */
internal fun accessNote(wifi: TrustedWifi.WifiState, background: Boolean): String? = when {
    !wifi.connected -> "Устройство не подключено к Wi‑Fi."
    wifi.problem == TrustedWifi.AccessProblem.Permission -> "Для определения сети Wi‑Fi требуется доступ к геолокации."
    wifi.problem == TrustedWifi.AccessProblem.LocationOff -> "Сеть Wi‑Fi не определяется: геолокация выключена."
    !wifi.ssidKnown -> "Имя сети Wi‑Fi недоступно."
    !background -> "Без доступа к геолокации в фоновом режиме сеть определяется только при открытом приложении."
    else -> null
}

@Composable
private fun TrustedNetworkRow(ssid: String, current: Boolean, onRemove: () -> Unit) {
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Rounded.Wifi,
            contentDescription = null,
            tint = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(ssid, style = MaterialTheme.typography.bodyMedium)
            if (current) {
                Text("Текущая сеть", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Icon(
            Icons.Rounded.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable {
                    haptics.perform(com.wdtt.client.ui.motion.Haptic.Click)
                    onRemove()
                }
                .semantics { contentDescription = "Удалить сеть «$ssid»" }
                .padding(8.dp),
        )
    }
}
