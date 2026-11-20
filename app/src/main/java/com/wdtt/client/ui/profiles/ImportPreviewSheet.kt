package com.wdtt.client.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.PeerAddress
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.ParsedImport
import com.wdtt.client.lite.ProfileStatusClient
import com.wdtt.client.lite.SubscriptionText
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.surface.ArdttBottomSheet
import com.wdtt.client.ui.home.ToneColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

private val Good = Color(0xFF22C55E)

/**
 * Card shown before a profile is added: the server is asked about each profile
 * (reachable, free places for devices, subscription end, VK call link) and only then
 * «Добавить» saves it. Adding is allowed in any case; the card only informs.
 */
@Composable
fun ImportPreviewSheet(
    parsed: ParsedImport,
    onAdd: (checks: Map<String, ProfileStatusClient.Check>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val checks = remember(parsed) { mutableStateMapOf<String, ProfileStatusClient.Check>() }
    LaunchedEffect(parsed) {
        coroutineScope {
            parsed.profiles.forEach { p ->
                launch { checks[p.id] = ProfileStatusClient.check(context, p.peer, p.password) }
            }
        }
    }
    val single = parsed.profiles.size == 1
    val done = checks.size == parsed.profiles.size

    ArdttBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.padding(horizontal = 20.dp)) {
        Text(
            if (single) "Новый профиль" else "Новые профили: ${parsed.profiles.size}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        )
        parsed.profiles.forEach { p -> ProfileCheckCard(p, checks[p.id]) }
        ArdttButton(
            text = if (single) "Добавить" else "Добавить все",
            onClick = { onAdd(checks.toMap()) },
            busy = !done,
            enabled = done,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProfileCheckCard(profile: ConnectionProfile, check: ProfileStatusClient.Check?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column {
            Text(profile.name.ifBlank { "Профиль" }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(PeerAddress.host(profile.peer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (check) {
            null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Проверка сервера…", style = MaterialTheme.typography.bodyMedium)
            }
            ProfileStatusClient.Check.Unreachable -> CheckLine(ToneColors.Warn, "Сервер не отвечает.\nПроверка повторится при подключении.")
            ProfileStatusClient.Check.Rejected -> CheckLine(ToneColors.Critical, "Сервер не принял профиль: срок подписки истёк или профиль недействителен.")
            is ProfileStatusClient.Check.Ok -> {
                CheckLine(Good, "Сервер доступен.")
                val info = check.info
                val free = (info.maxDevices - info.boundDevices).coerceAtLeast(0)
                when {
                    check.thisDeviceBound -> CheckLine(Good, "Это устройство уже привязано к профилю.")
                    free > 0 -> CheckLine(Good, "Свободно мест для устройств: $free из ${info.maxDevices}.")
                    else -> CheckLine(
                        ToneColors.Critical,
                        "Свободных мест для устройств нет.\nОсвободите место переносом профиля с другого устройства.",
                    )
                }
                val sub = SubscriptionText.view(info, LiteSettings.DEFAULT_WARN_DAYS)
                CheckLine(
                    when (sub.tone) {
                        com.wdtt.client.lite.SubscriptionTone.Warn -> ToneColors.Warn
                        com.wdtt.client.lite.SubscriptionTone.Critical, com.wdtt.client.lite.SubscriptionTone.Expired -> ToneColors.Critical
                        else -> Good
                    },
                    if (sub.untilText.isEmpty()) sub.hint else "${sub.hint}\nДействует ${sub.untilText}.",
                )
            }
        }
        if (profile.vkHashes.isBlank()) {
            CheckLine(ToneColors.Warn, "Ссылка на звонок VK не указана.\nЕё можно добавить в настройках профиля.")
        } else {
            CheckLine(Good, "Ссылка на звонок VK указана.")
        }
    }
}

@Composable
private fun CheckLine(color: Color, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 6.dp).size(8.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
