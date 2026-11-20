package com.wdtt.client.ui.profiles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.PeerAddress
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.SubscriptionText
import com.wdtt.client.lite.SubscriptionTone
import com.wdtt.client.lite.TunnelActions
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import com.wdtt.client.ui.components.control.ArdttChoiceChip
import com.wdtt.client.ui.components.control.ArdttTextField
import com.wdtt.client.ui.components.surface.ArdttBottomSheet
import com.wdtt.client.ui.home.PingColors
import com.wdtt.client.ui.home.ToneColors
import kotlinx.coroutines.launch

/**
 * Profile details: subscription end, expiry warning setting, server facts.
 * A standard bottom sheet: closes by swiping down, tapping outside or the system back.
 */
@Composable
fun ProfileSheet(
    profile: ConnectionProfile,
    isActive: Boolean,
    ping: Long,
    onDismiss: () -> Unit,
    onOtherProfiles: (() -> Unit)? = null,
    onMakeActive: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lite = remember { LiteSettings(context) }
    val sub by remember(profile.id) { lite.subscription(profile.id) }.collectAsState(null)
    val warnEnabled by lite.warnEnabled.collectAsStateWithLifecycle(true)
    val warnDays by lite.warnDays.collectAsStateWithLifecycle(LiteSettings.DEFAULT_WARN_DAYS)
    val view = SubscriptionText.view(sub, warnDays)

    LaunchedEffect(profile.id) { TunnelActions.refreshSubscription(context, profile.id) }
    var transferOpen by remember { mutableStateOf(false) }
    if (transferOpen) {
        TransferSheet(profile = profile, onDismiss = { transferOpen = false }, onFinished = onDismiss)
        return
    }

    ArdttBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                profile.name.ifBlank { "Профиль" },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isActive) {
                Text(
                    "Активный",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Block("Подписка") {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    view.short.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                if (view.untilText.isNotEmpty()) {
                    Text(view.untilText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            view.barFraction?.let { SubscriptionBar(it, view.tone) }
            Text(view.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Block(null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Предупреждать об окончании",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = warnEnabled, onCheckedChange = { scope.launch { lite.saveWarnEnabled(it) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiteSettings.WARN_DAY_CHOICES.forEach { d ->
                    ArdttChoiceChip(
                        label = SubscriptionText.days(d.toLong()),
                        selected = d == warnDays,
                        enabled = warnEnabled,
                        onClick = { scope.launch { lite.saveWarnDays(d) } },
                    )
                }
            }
            Text(
                SubscriptionText.warnSentence(warnEnabled, warnDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Block("Звонок VK") {
            var link by remember(profile.id, profile.vkHashes) { mutableStateOf(profile.vkHashes) }
            val saved = profile.vkHashes.isNotBlank()
            Text(
                if (saved) "Ссылка указана." else "Для подключения нужна ссылка на звонок VK.",
                style = MaterialTheme.typography.bodySmall,
                color = if (saved) MaterialTheme.colorScheme.onSurfaceVariant else ToneColors.Critical,
            )
            ArdttTextField(
                value = link,
                onValueChange = { link = it },
                placeholder = "https://vk.com/call/join/…",
                modifier = Modifier.fillMaxWidth(),
            )
            if (link.trim() != profile.vkHashes.trim()) {
                ArdttButton(
                    text = "Сохранить",
                    onClick = { scope.launch { TunnelActions.saveCallLink(context, profile.id, link) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Block("Сервер") {
            InfoRow("Адрес", PeerAddress.host(profile.peer))
            InfoRow("Пинг", if (ping > 0) "$ping мс" else "нет подключения", PingColors.of(ping))
            InfoRow("Устройства", sub?.let { "${it.boundDevices} из ${it.maxDevices}" } ?: "нет данных")
        }

        ArdttButton(
            text = "Перенести на другое устройство",
            variant = ArdttButtonVariant.Outlined,
            onClick = { transferOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )

        if (onMakeActive != null && !isActive) {
            ArdttButton(text = "Сделать активным", onClick = onMakeActive, modifier = Modifier.fillMaxWidth())
        }
        if (onDelete != null) {
            ArdttButton(
                text = "Удалить профиль",
                onClick = onDelete,
                variant = ArdttButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (onOtherProfiles != null) {
            ArdttButton(
                text = "Другие профили",
                onClick = onOtherProfiles,
                variant = ArdttButtonVariant.Tonal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Block(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        content()
    }
}

@Composable
private fun SubscriptionBar(fraction: Float, tone: SubscriptionTone) {
    val target = when (tone) {
        SubscriptionTone.Warn -> ToneColors.Warn
        SubscriptionTone.Critical, SubscriptionTone.Expired -> ToneColors.Critical
        else -> Color(0xFF22C55E)
    }
    val color by animateColorAsState(target, tween(300), label = "bar")
    val width by animateFloatAsState(fraction, tween(400), label = "barw")
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Box(Modifier.fillMaxWidth(width).height(8.dp).clip(RoundedCornerShape(8.dp)).background(color))
    }
}

@Composable
private fun InfoRow(label: String, value: String, dot: Color? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (dot != null) Box(Modifier.padding(end = 6.dp).size(7.dp).background(dot, CircleShape))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}
