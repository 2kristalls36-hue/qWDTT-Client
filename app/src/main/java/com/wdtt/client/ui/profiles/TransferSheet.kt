package com.wdtt.client.ui.profiles

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.SettingsStore
import com.wdtt.client.TunnelManager
import com.wdtt.client.lite.ProfileTransfer
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import com.wdtt.client.ui.components.surface.ArdttBottomSheet
import com.wdtt.client.ui.components.surface.ArdttDialog
import com.wdtt.client.ui.components.surface.ArdttDialogAction
import com.wdtt.client.ui.components.surface.ArdttQrCode
import kotlinx.coroutines.launch

/**
 * Moving a profile to another device: QR code and link (with the VK call link inside),
 * then «Завершить перенос» releases this device on the server and removes the profile here.
 */
@Composable
fun TransferSheet(profile: ConnectionProfile, onDismiss: () -> Unit, onFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val link by produceState<String?>(null, profile) { value = ProfileTransfer.link(context, profile) }
    val running by TunnelManager.running.collectAsStateWithLifecycle()
    val activeId by remember { SettingsStore(context).currentProfileId }.collectAsStateWithLifecycle("")
    val inUse = running && activeId == profile.id
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    ArdttBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Перенос на другое устройство", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            "Отсканируйте QR-код в qWDTT Client на новом устройстве или передайте ссылку.\n" +
                "Ссылка на звонок VK переносится вместе с профилем.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        link?.let {
            ArdttQrCode(content = it, modifier = Modifier.widthIn(max = 320.dp).align(Alignment.CenterHorizontally))
        }
        ArdttButton(
            text = "Скопировать ссылку",
            variant = ArdttButtonVariant.Tonal,
            enabled = link != null,
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Профиль qWDTT Client", link))
                Toast.makeText(context, "Ссылка скопирована.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (inUse) "Завершение переноса доступно после отключения."
            else "После добавления профиля на новом устройстве завершите перенос: это устройство будет отвязано, профиль удалится с него.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ArdttButton(
            text = "Завершить перенос",
            variant = ArdttButtonVariant.Danger,
            enabled = !inUse && !busy,
            busy = busy,
            onClick = { confirm = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (confirm) {
        ArdttDialog(
            title = "Завершить перенос?",
            onDismissRequest = { if (!busy) confirm = false },
            confirmAction = ArdttDialogAction("Завершить", destructive = true, enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val ok = ProfileTransfer.finish(context, profile)
                    busy = false
                    confirm = false
                    if (ok) {
                        Toast.makeText(context, "Перенос завершён. Профиль удалён с этого устройства.", Toast.LENGTH_LONG).show()
                        onFinished()
                    } else {
                        Toast.makeText(context, "Сервер не ответил. Перенос не завершён.", Toast.LENGTH_LONG).show()
                    }
                }
            }),
            dismissAction = ArdttDialogAction("Отмена", onClick = { confirm = false }),
        ) {
            Text(
                "Это устройство будет отвязано от профиля, профиль «${profile.name.ifBlank { "Профиль" }}» удалится с него.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
