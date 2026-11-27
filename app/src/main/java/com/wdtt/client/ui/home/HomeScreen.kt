package com.wdtt.client.ui.home

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.layout.layout
import androidx.compose.animation.togetherWith
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.text.TextStyle
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
    val trustedPause by TunnelManager.trustedPause.collectAsStateWithLifecycle()
    val vkWait by TunnelManager.vkWait.collectAsStateWithLifecycle()
    val state = when {
        trustedPause != null -> TunnelUiState.Paused
        connecting || reconnecting -> TunnelUiState.Connecting
        running -> TunnelUiState.On
        else -> TunnelUiState.Off
    }

    // The saved level; until it is read here, as it was read when the app started (the
    // first frame waits for that): the card and the button appear with the right level and
    // colours at once instead of sliding over from the default one.
    val levelRead by lite.powerLevel.collectAsStateWithLifecycle(null)
    val levelLoaded = levelRead ?: com.wdtt.client.lite.StartupSettings.powerLevel
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
    var aboutOpen by remember { mutableStateOf(false) }

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

    // Real connection stages from the client, for the ring and the line under the button.
    val pipeline by TunnelManager.connectionPipeline.collectAsStateWithLifecycle()
    val stageView = com.wdtt.client.lite.ConnectionStages.view(pipeline)
        // While paused on a trusted network the line names the pause, not the last attempt;
        // while the engine waits out a VK limit, the line says that instead of a stage.
        ?.takeIf { state != TunnelUiState.Paused && !(vkWait && state == TunnelUiState.Connecting) && (state == TunnelUiState.Connecting || it.failed) }

    // Vibration on results: connected, or an attempt that ended without connecting.
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    var prevState by remember { mutableStateOf(state) }
    LaunchedEffect(state) {
        when {
            state == TunnelUiState.On && prevState != TunnelUiState.On -> haptics.perform(com.wdtt.client.ui.motion.Haptic.Confirm)
            state == TunnelUiState.Off && prevState == TunnelUiState.Connecting -> haptics.perform(com.wdtt.client.ui.motion.Haptic.Reject)
        }
        prevState = state
    }

    // Refresh the subscription once the tunnel is up: the server is surely reachable then.
    LaunchedEffect(state, profileId) {
        if (state == TunnelUiState.On && profileId.isNotBlank()) TunnelActions.refreshSubscription(context, profileId)
    }

    HomeColumn(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp)
            .padding(top = 20.dp, bottom = ArdttBottomChrome.navigationReserve() + 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().widthIn(max = 460.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandTitle(
                blend = powerPos.value,
                modifier = Modifier
                    .weight(1f)
                    // A little air between the logo and the screen edge.
                    .padding(start = 6.dp, end = 8.dp)
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { haptics.perform(com.wdtt.client.ui.motion.Haptic.LongPress); aboutOpen = true }) },
            )
            ProfileChip(
                name = profile?.name ?: "Нет профиля",
                value = if (profile == null) null else subView.short,
                tone = if (profile == null) SubscriptionTone.Unknown else subView.tone,
                ping = if (state == TunnelUiState.On) ping else 0L,
                onClick = { if (profile == null) onOpenProfiles() else sheetOpen = true },
            )
        }

        ConnectButton(
            state = state,
            blend = powerPos.value,
            stages = stageView,
            onClick = {
                haptics.perform(com.wdtt.client.ui.motion.Haptic.Click)
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
        val notice by TunnelManager.stopNotice.collectAsStateWithLifecycle()
        StatusLine(
            state, level, stageView,
            notice = notice.takeIf { state == TunnelUiState.Off }
                ?: trustedPause?.let { "Пауза: доверенная сеть «$it»." }
                ?: VK_WAIT_NOTICE.takeIf { vkWait && state == TunnelUiState.Connecting },
        )
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

        PowerCard(
            level = level,
            locked = state != TunnelUiState.Off,
            onLevelChange = { scope.launch { lite.savePowerLevel(it) } },
            modifier = Modifier
                .widthIn(max = 460.dp)
                // Closer to the status line: the place kept for the statistics made the gap
                // look large. When they are shown, the card stays as far from them as the
                // tiles are from each other.
                .layout { measurable, constraints ->
                    val p = measurable.measure(constraints)
                    val up = PowerCardLift.roundToPx()
                    layout(p.width, (p.height - up).coerceAtLeast(0)) { p.place(0, -up) }
                },
            pos = powerPos,
        )
    }

    // A long press on the logo: about the app. The journal is in the settings.
    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })


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
            // Another profile or a new term: the chip eases to its new width.
            .animateContentSize(tween(260))
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
private fun StatusLine(state: TunnelUiState, level: PowerLevel, stage: com.wdtt.client.lite.StageView?, notice: String? = null) {
    val colors = levelColors(level)
    val scheme = MaterialTheme.colorScheme
    val led = when (state) {
        TunnelUiState.Off -> scheme.outline.copy(alpha = 0.5f)
        else -> if (level == PowerLevel.Maximum) colors.neon[1] else colors.accent
    }
    val failed = stage?.failed == true
    val ledColor by animateColorAsState(if (failed) PingColors.Bad else led, tween(300), label = "led")
    val text = when {
        stage != null -> com.wdtt.client.lite.ConnectionStages.line(stage)
        // Stopped by the system (e.g. another VPN took over): say why.
        notice != null -> notice
        state == TunnelUiState.Off -> "Не подключено"
        state == TunnelUiState.Connecting -> "Подключение…"
        else -> "Подключено"
    }
    // Shown while connecting: which stage out of how many.
    val counter = stage?.takeIf { !it.failed && it.index < it.total }?.let { "${it.index + 1} из ${it.total}" }
    // The LED by the first line when the text runs to more.
    val line = with(androidx.compose.ui.platform.LocalDensity.current) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.padding(top = ((line - 8.dp) / 2).coerceAtLeast(0.dp)).size(8.dp).background(ledColor, CircleShape))
        androidx.compose.animation.AnimatedContent(
            targetState = text,
            transitionSpec = {
                (androidx.compose.animation.fadeIn(tween(260, 100)) togetherWith androidx.compose.animation.fadeOut(tween(160)))
                    .using(androidx.compose.animation.SizeTransform(clip = false))
            },
            label = "stage",
        ) { t ->
            Text(
                t,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) PingColors.Bad else scheme.onSurfaceVariant,
            )
        }
        if (counter != null) {
            Text(counter, style = MaterialTheme.typography.bodySmall, color = scheme.outline)
        }
    }
}

/**
 * «qWDTT» in the colour of the power level and the handwritten «Client» of the app icon.
 * Follows the slider like the rest of the screen: blue, green, and at the maximum level
 * the neon of its card flows through the letters.
 */
@Composable
private fun BrandTitle(blend: Float, modifier: Modifier = Modifier) {
    val g = (blend * 2f).coerceIn(0f, 1f)
    val k = ((blend - 0.5f) * 2f).coerceIn(0f, 1f)
    val accent = lerp(levelColors(PowerLevel.Stable).accent, levelColors(PowerLevel.Balanced).accent, g)
    val neon = levelColors(PowerLevel.Maximum).neon
    val motion = rememberMotionScale()
    // The neon drifts only while it is visible.
    val drift = if (k > 0.01f && motion > 0.01f) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "brand")
        t.animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween((2600 / motion).toInt(), easing = androidx.compose.animation.core.LinearEasing)),
            label = "brandDrift",
        )
    } else null
    val period = with(androidx.compose.ui.platform.LocalDensity.current) { 220.dp.toPx() }
    // Two-tone level colour, deeper at the start of the word; neon laid over it by k.
    val fill = Modifier
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.linearGradient(listOf(lerp(accent, Color.Black, 0.12f), lerp(accent, Color.White, 0.18f)), end = Offset(size.width, size.height)),
                blendMode = androidx.compose.ui.graphics.BlendMode.SrcIn,
            )
            if (k > 0.01f) {
                val shift = (drift?.value ?: 0.3f) * period
                drawRect(
                    Brush.linearGradient(
                        colors = neon + neon.first(),
                        start = Offset(shift, 0f),
                        end = Offset(shift + period, period * 0.35f),
                        tileMode = androidx.compose.ui.graphics.TileMode.Repeated,
                    ),
                    alpha = k,
                    blendMode = androidx.compose.ui.graphics.BlendMode.SrcAtop,
                )
            }
        }
    // The logo of the brandbook, in two lines as on the app icon, 50 dp tall.
    Box(modifier.clearAndSetSemantics { contentDescription = "qWDTT Client" }) {
        com.wdtt.client.ui.theme.BrandWordmark(
            Modifier
                .size(
                    width = (50f * com.wdtt.client.ui.theme.Brand.LOGO_WIDTH / com.wdtt.client.ui.theme.Brand.LOGO_HEIGHT).dp,
                    height = 50.dp,
                )
                .then(fill),
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
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text("Трафик", style = tileLabelStyle(), color = scheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
                FlowArrow(down = true, active = traffic.downActive, accent = accent, drift = drift)
                FlowArrow(down = false, active = traffic.upActive, accent = accent, drift = drift)
            }
            Text(
                total?.let { formatMb(it) } ?: "—",
                style = tileValueStyle(), color = scheme.onSurface, maxLines = 1,
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
            .padding(vertical = 7.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(label, style = tileLabelStyle(), color = scheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dot != null) Box(Modifier.size(7.dp).background(dot, CircleShape))
            Text(value, style = tileValueStyle(), color = scheme.onSurface, maxLines = 1)
        }
    }
}

// Tile texts without the font's own top and bottom padding: the label sits right on the value.
private val TightLines = androidx.compose.ui.text.style.LineHeightStyle(
    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
)

private fun tileLabelStyle() = TextStyle(
    fontSize = 11.sp,
    lineHeight = 13.sp,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = TightLines,
)

private fun tileValueStyle() = TextStyle(
    fontSize = 14.sp,
    lineHeight = 17.sp,
    fontWeight = FontWeight.SemiBold,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = TightLines,
)

/** How much the power card is drawn up into the gap above it. */
private val PowerCardLift = 16.dp

/** The connect button is made no smaller than this on a short screen. */
private const val ButtonMinScale = 0.75f

/**
 * The main screen top to bottom: the header, the connect button, the status line, the
 * statistics, the power card. On a short screen (or with a large font) the button is drawn
 * smaller, as much as it takes for the power card to fit above the tab bar (down to
 * [ButtonMinScale]; beyond that the screen scrolls). The status line takes the place of one
 * line: a longer one (a notice, a failed stage) runs over the place of the statistics, which
 * are hidden then, so the card does not move and the button does not change its size.
 * Everything is worked out in one layout pass, from the height the screen leaves without
 * scrolling (the minimum height the scroll passes on).
 */
@Composable
private fun HomeColumn(modifier: Modifier, content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val line = MaterialTheme.typography.bodySmall.lineHeight
    androidx.compose.ui.layout.Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
        val (header, button, status, stats, card) = measurables.map { it.measure(loose) }
        val gap = 24.dp.roundToPx()
        val small = 14.dp.roundToPx()
        val oneLine = with(density) { line.toPx() }
        // One line of status; more runs over the statistics.
        val statusPlace = if (status.height < oneLine * 1.5f) status.height else kotlin.math.ceil(oneLine).toInt()
        val rest = header.height + gap + small + statusPlace + small + stats.height + gap + card.height
        val room = constraints.minHeight
        val scale = if (room <= 0) 1f else ((room - rest).toFloat() / button.height).coerceIn(ButtonMinScale, 1f)
        val buttonPlace = (button.height * scale).roundToInt()
        val width = constraints.maxWidth
        val height = maxOf(rest + buttonPlace, constraints.minHeight)
        layout(width, height) {
            fun androidx.compose.ui.layout.Placeable.centre() = (width - this.width) / 2
            var y = 0
            header.place(header.centre(), y)
            y += header.height + gap
            // Scaled about its centre, its top where the place for it begins.
            button.placeWithLayer(button.centre(), y - (button.height - buttonPlace) / 2) {
                scaleX = scale
                scaleY = scale
            }
            y += buttonPlace + small
            status.place(status.centre(), y)
            y += statusPlace + small
            stats.place(stats.centre(), y)
            y += stats.height + gap
            card.place(card.centre(), y)
        }
    }
}

/** Under the button while the engine waits out a VK limit and retries by itself. */
internal const val VK_WAIT_NOTICE = "VK временно ограничил запросы.\nПриложение повторит подключение автоматически."
