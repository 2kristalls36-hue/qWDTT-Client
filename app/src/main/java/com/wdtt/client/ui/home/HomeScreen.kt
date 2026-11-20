package com.wdtt.client.ui.home

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.MainActivity
import com.wdtt.client.ProfilesStore
import com.wdtt.client.SettingsStore
import com.wdtt.client.TunnelManager
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.PowerLevel
import com.wdtt.client.lite.SubscriptionText
import com.wdtt.client.lite.SubscriptionTone
import com.wdtt.client.lite.TunnelActions
import com.wdtt.client.ui.components.layout.ArdttBottomChrome
import com.wdtt.client.ui.profiles.ProfileSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

internal object ToneColors {
    val Warn = Color(0xFFF59E0B)
    val Critical = Color(0xFFEF4444)
}

@Composable
fun HomeScreen(settingsStore: SettingsStore, onOpenProfiles: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lite = remember { LiteSettings(context) }
    val profilesStore = remember { ProfilesStore(context) }

    val running by TunnelManager.running.collectAsStateWithLifecycle()
    val connecting by TunnelManager.isConnecting.collectAsStateWithLifecycle()
    val reconnecting by TunnelManager.isReconnecting.collectAsStateWithLifecycle()
    val since by TunnelManager.connectedSinceMs.collectAsStateWithLifecycle()
    val ping by TunnelManager.pingMs.collectAsStateWithLifecycle()
    val speed by TunnelManager.downlinkMbps.collectAsStateWithLifecycle()
    val state = when {
        connecting || reconnecting -> TunnelUiState.Connecting
        running -> TunnelUiState.On
        else -> TunnelUiState.Off
    }

    val level by lite.powerLevel.collectAsStateWithLifecycle(PowerLevel.Default)
    val warnDays by lite.warnDays.collectAsStateWithLifecycle(LiteSettings.DEFAULT_WARN_DAYS)
    val profileId by settingsStore.currentProfileId.collectAsStateWithLifecycle("")
    val profiles by profilesStore.profiles.collectAsStateWithLifecycle(emptyList())
    val profile = profiles.firstOrNull { it.id == profileId }
    val subFlow = remember(profileId) { if (profileId.isBlank()) flowOf(null) else lite.subscription(profileId) }
    val sub by subFlow.collectAsState(null)
    val subView = SubscriptionText.view(sub, warnDays)

    var sheetOpen by remember { mutableStateOf(false) }

    // Refresh the subscription once the tunnel is up: the server is surely reachable then.
    LaunchedEffect(state, profileId) {
        if (state == TunnelUiState.On && profileId.isNotBlank()) TunnelActions.refreshSubscription(context, profileId)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = ArdttBottomChrome.navigationReserve() + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(Modifier.fillMaxWidth().widthIn(max = 460.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("qWDTT Lite", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            ProfileChip(
                name = profile?.name ?: "Нет профиля",
                value = if (profile == null) null else subView.short,
                tone = if (profile == null) SubscriptionTone.Unknown else subView.tone,
                ping = if (state == TunnelUiState.On) ping else 0L,
                onClick = { if (profile == null) onOpenProfiles() else sheetOpen = true },
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ConnectButton(
                state = state,
                level = level,
                onClick = {
                    when (state) {
                        TunnelUiState.Off -> {
                            val activity = context as? MainActivity ?: return@ConnectButton
                            TunnelActions.connect(activity) {
                                Toast.makeText(context, "Профиль не выбран.", Toast.LENGTH_SHORT).show()
                                onOpenProfiles()
                            }
                        }
                        else -> TunnelActions.disconnect(context)
                    }
                },
            )
            StatusLine(state, level)
            StatsRow(state == TunnelUiState.On, since, speed, ping)
        }

        PowerCard(
            level = level,
            locked = state != TunnelUiState.Off,
            onLevelChange = { scope.launch { lite.savePowerLevel(it) } },
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }

    if (sheetOpen && profile != null) {
        ProfileSheet(
            profile = profile,
            isActive = true,
            ping = if (state == TunnelUiState.On) ping else 0L,
            onDismiss = { sheetOpen = false },
            onOtherProfiles = { sheetOpen = false; onOpenProfiles() },
        )
    }
}

@Composable
private fun ProfileChip(name: String, value: String?, tone: SubscriptionTone, ping: Long, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val toneColor = when (tone) {
        SubscriptionTone.Warn -> ToneColors.Warn
        SubscriptionTone.Critical, SubscriptionTone.Expired -> ToneColors.Critical
        else -> null
    }
    val bg by animateColorAsState(
        toneColor?.copy(alpha = 0.3f)?.compositeOver(scheme.surface) ?: scheme.surfaceVariant,
        tween(300), label = "chip",
    )
    val dot by animateColorAsState(PingColors.of(ping) ?: scheme.outline.copy(alpha = 0.5f), tween(300), label = "dot")
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(role = Role.Button, onClickLabel = "Открыть сведения о профиле", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(
            name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
        if (value != null) {
            Text("· $value", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun StatusLine(state: TunnelUiState, level: PowerLevel) {
    val colors = levelColors(level)
    val scheme = MaterialTheme.colorScheme
    val led = when (state) {
        TunnelUiState.Off -> scheme.outline.copy(alpha = 0.5f)
        else -> if (level == PowerLevel.Maximum) colors.neon[1] else colors.accent
    }
    val ledColor by animateColorAsState(led, tween(300), label = "led")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(ledColor, CircleShape))
        Text(
            when (state) {
                TunnelUiState.Off -> "Не подключено"
                TunnelUiState.Connecting -> "Подключение…"
                TunnelUiState.On -> "Подключено"
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

/** Uptime, speed, ping. Space is reserved even when hidden, so the screen does not jump. */
@Composable
private fun StatsRow(visible: Boolean, sinceMs: Long, mbps: Double?, ping: Long) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(400), label = "stats")
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(visible) {
        while (visible) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val uptime = if (visible && sinceMs > 0) {
        val s = ((now - sinceMs) / 1000).coerceAtLeast(0)
        if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    } else "00:00"
    Row(
        Modifier.widthIn(max = 340.dp).fillMaxWidth().alpha(alpha),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile("Время", uptime, null, Modifier.weight(1f))
        StatTile("Скорость", mbps?.let { "↓ ${formatMbps(it)} Мбит/с" } ?: "—", null, Modifier.weight(1f))
        StatTile("Пинг", if (ping > 0) "$ping мс" else "—", PingColors.of(ping), Modifier.weight(1f))
    }
}

private fun formatMbps(v: Double): String = if (v < 10) "%.1f".format(v).replace('.', ',') else v.toInt().toString()

@Composable
private fun StatTile(label: String, value: String, dot: Color?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, fontSize = 11.sp, color = scheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dot != null) Box(Modifier.size(7.dp).background(dot, CircleShape))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = 1)
        }
    }
}
