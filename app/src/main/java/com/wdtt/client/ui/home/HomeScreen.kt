package com.wdtt.client.ui.home

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.graphicsLayer
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
    val downMb by TunnelManager.trafficDownMb.collectAsStateWithLifecycle()
    val upMb by TunnelManager.trafficUpMb.collectAsStateWithLifecycle()
    val downActive by TunnelManager.downActive.collectAsStateWithLifecycle()
    val upActive by TunnelManager.upActive.collectAsStateWithLifecycle()
    val state = when {
        connecting || reconnecting -> TunnelUiState.Connecting
        running -> TunnelUiState.On
        else -> TunnelUiState.Off
    }

    // Null until the saved level is read: the card and the button appear with the right
    // colors at once instead of sliding over from the default level.
    val levelLoaded by lite.powerLevel.collectAsStateWithLifecycle(null)
    val level = levelLoaded ?: PowerLevel.Default
    // Slider thumb position, shared by the power card and the connect button.
    val powerPos = remember(levelLoaded != null) { androidx.compose.animation.core.Animatable(level.ordinal / 2f) }
    val warnDays by lite.warnDays.collectAsStateWithLifecycle(LiteSettings.DEFAULT_WARN_DAYS)
    val profileId by settingsStore.currentProfileId.collectAsStateWithLifecycle("")
    val profiles by profilesStore.profiles.collectAsStateWithLifecycle(emptyList())
    val profile = profiles.firstOrNull { it.id == profileId }
    val subFlow = remember(profileId) { if (profileId.isBlank()) flowOf(null) else lite.subscription(profileId) }
    val sub by subFlow.collectAsState(null)
    val subView = SubscriptionText.view(sub, warnDays)

    var sheetOpen by remember { mutableStateOf(false) }
    var logsOpen by remember { mutableStateOf(false) }

    // A failed start ends quietly in the engine; show its reason once.
    val fatal by TunnelManager.lastFatalError.collectAsStateWithLifecycle()
    LaunchedEffect(fatal) {
        val reason = fatal ?: return@LaunchedEffect
        Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
        // The binding can be reset right in the profile sheet.
        if (reason.contains("другому устройству")) sheetOpen = true
    }

    // The home screen widget mirrors level, profile and state.
    LaunchedEffect(level, profile?.name, state, ping) { com.wdtt.client.TunnelWidgetProvider.refresh(context) }

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
            Text(
                "qWDTT Client",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { logsOpen = true }) },
            )
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
                blend = powerPos.value,
                onClick = {
                    when (state) {
                        TunnelUiState.Off -> {
                            val activity = context as? MainActivity ?: return@ConnectButton
                            TunnelActions.connect(activity) { problem ->
                                when (problem) {
                                    TunnelActions.Problem.NoProfile -> {
                                        Toast.makeText(context, "Профиль не выбран.", Toast.LENGTH_SHORT).show()
                                        onOpenProfiles()
                                    }
                                    TunnelActions.Problem.NoCallLink -> {
                                        Toast.makeText(context, "Для подключения нужна ссылка на звонок VK.", Toast.LENGTH_LONG).show()
                                        sheetOpen = true
                                    }
                                }
                            }
                        }
                        else -> TunnelActions.disconnect(context)
                    }
                },
            )
            StatusLine(state, level)
            StatsRow(
                visible = state == TunnelUiState.On,
                sinceMs = since,
                traffic = Traffic(downMb, upMb, downActive, upActive),
                arrowColor = lerp(
                    lerp(levelColors(PowerLevel.Stable).accent, levelColors(PowerLevel.Balanced).accent, (powerPos.value * 2f).coerceIn(0f, 1f)),
                    levelColors(PowerLevel.Maximum).neon[1],
                    ((powerPos.value - 0.5f) * 2f).coerceIn(0f, 1f),
                ),
                ping = ping,
            )
        }

        PowerCard(
            level = level,
            locked = state != TunnelUiState.Off,
            onLevelChange = { scope.launch { lite.savePowerLevel(it) } },
            modifier = Modifier.widthIn(max = 460.dp),
            pos = powerPos,
        )
    }

    if (logsOpen) LogsSheet(onDismiss = { logsOpen = false })

    // The app crashed last time: show what happened so it can be sent to the developer.
    var crash by remember { mutableStateOf(com.wdtt.client.lite.CrashLog.read(context)) }
    crash?.let { text ->
        com.wdtt.client.ui.components.surface.ArdttDialog(
            title = "Приложение было закрыто из-за ошибки",
            onDismissRequest = { com.wdtt.client.lite.CrashLog.clear(context); crash = null },
            confirmAction = com.wdtt.client.ui.components.surface.ArdttDialogAction("Скопировать", onClick = {
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Ошибка qWDTT Client", text))
                Toast.makeText(context, "Текст ошибки скопирован.", Toast.LENGTH_SHORT).show()
                com.wdtt.client.lite.CrashLog.clear(context)
                crash = null
            }),
            dismissAction = com.wdtt.client.ui.components.surface.ArdttDialogAction("Закрыть", onClick = {
                com.wdtt.client.lite.CrashLog.clear(context); crash = null
            }),
        ) {
            Text(
                text.lineSequence().take(14).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
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

/** Session traffic for the middle tile. */
private data class Traffic(val downMb: Double?, val upMb: Double?, val downActive: Boolean, val upActive: Boolean)

/** Uptime, traffic, ping. Space is reserved even when hidden, so the screen does not jump. */
@Composable
private fun StatsRow(visible: Boolean, sinceMs: Long, traffic: Traffic, arrowColor: Color, ping: Long) {
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
        Modifier
            .widthIn(max = 340.dp)
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                translationY = (1f - alpha) * 10.dp.toPx()
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile("Время", uptime, null, Modifier.weight(1f))
        TrafficTile(traffic, arrowColor, Modifier.weight(1f))
        StatTile("Пинг", if (ping > 0) "$ping мс" else "—", PingColors.of(ping), Modifier.weight(1f))
    }
}

private fun formatMb(mb: Double): String = when {
    mb >= 1024 -> "%.1f ГБ".format(mb / 1024).replace('.', ',')
    mb >= 10 -> "${mb.toInt()} МБ"
    else -> "%.1f МБ".format(mb).replace('.', ',')
}

/**
 * Total session traffic with two arrows. An arrow lights up and keeps drifting in its
 * direction while data moves that way; idle, it fades back.
 */
@Composable
private fun TrafficTile(traffic: Traffic, accent: Color, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val total = traffic.downMb?.let { it + (traffic.upMb ?: 0.0) }
    val motion = rememberMotionScale()
    val t = rememberInfiniteTransition(label = "traffic")
    val drift by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween((900 / motion.coerceAtLeast(0.01f)).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Трафик", fontSize = 11.sp, color = scheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
                FlowArrow(down = true, active = traffic.downActive, accent = accent, drift = drift)
                FlowArrow(down = false, active = traffic.upActive, accent = accent, drift = drift)
            }
            Text(
                total?.let { formatMb(it) } ?: "—",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, maxLines = 1,
            )
        }
    }
}

@Composable
private fun FlowArrow(down: Boolean, active: Boolean, accent: Color, drift: Float) {
    val idle = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val on by animateFloatAsState(if (active) 1f else 0f, tween(500), label = "arrow")
    val color = lerp(idle, accent, on)
    // Moves a few px in its direction and fades at the end of each stroke, like a stream.
    val shift = (drift - 0.5f) * 5f * on * (if (down) 1f else -1f)
    val a = 1f - on * 0.55f * kotlin.math.abs(drift - 0.5f) * 2f
    Icon(
        if (down) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
        contentDescription = if (down) "Загрузка" else "Отдача",
        tint = color,
        modifier = Modifier
            .size(15.dp)
            .graphicsLayer {
                translationY = shift.dp.toPx()
                alpha = a
            },
    )
}

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
