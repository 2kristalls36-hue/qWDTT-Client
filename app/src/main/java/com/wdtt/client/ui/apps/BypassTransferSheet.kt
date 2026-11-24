package com.wdtt.client.ui.apps

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wdtt.client.lite.BypassTransfer
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import com.wdtt.client.ui.components.surface.ArdttBottomSheet
import com.wdtt.client.ui.motion.Haptic
import com.wdtt.client.ui.motion.LocalAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Export and import of the bypass lists (apps of both modes and sites), in the qWDTT
 * file format. An import first shows what will be replaced.
 */
@Composable
fun BypassTransferSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalAppHaptics.current
    var preview by remember { mutableStateOf<BypassTransfer.Preview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun offer(raw: String?) {
        if (raw.isNullOrBlank()) {
            error = "Данные для импорта не найдены."
            haptics.perform(Haptic.Reject)
            return
        }
        runCatching { BypassTransfer.preview(raw) }
            .onSuccess { preview = it; error = null }
            .onFailure { error = it.message ?: "Файл не распознан."; haptics.perform(Haptic.Reject) }
    }

    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = BypassTransfer.export(context)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("no stream")
                }
            }.isSuccess
            haptics.perform(if (ok) Haptic.Confirm else Haptic.Reject)
            Toast.makeText(context, if (ok) "Файл сохранён." else "Файл не сохранён.", Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            offer(text)
        }
    }

    val current = preview
    if (current != null) {
        ImportPreview(
            preview = current,
            onApply = {
                scope.launch {
                    BypassTransfer.apply(context, current)
                    haptics.perform(Haptic.Confirm)
                    Toast.makeText(context, "Списки обхода загружены.", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            },
            onDismiss = { preview = null },
        )
        return
    }

    ArdttBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Экспорт и импорт", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            "Списки приложений обоих режимов и сайтов.\nФайл совместим с qWDTT.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Option(Icons.Outlined.Share, "Поделиться") {
            scope.launch {
                val json = BypassTransfer.export(context)
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, json)
                context.startActivity(Intent.createChooser(send, "Списки обхода").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Option(Icons.Outlined.SaveAlt, "Сохранить в файл") { saveFile.launch("qwdtt-bypass.json") }
        Option(Icons.Outlined.FolderOpen, "Загрузить из файла") { openFile.launch(arrayOf("*/*")) }
        Option(Icons.Outlined.ContentPaste, "Вставить из буфера обмена") { offer(readClipboard(context)) }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = com.wdtt.client.ui.home.ToneColors.Critical)
        }
    }
}

/** What an import will replace, before it is applied. */
@Composable
private fun ImportPreview(preview: BypassTransfer.Preview, onApply: () -> Unit, onDismiss: () -> Unit) {
    ArdttBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Импорт списков обхода", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            preview.sites?.let { Line("Сайты и IP-адреса", counted(it.size, "запись", "записи", "записей")) }
            if (preview.sitesTruncated) {
                Text(
                    "Загружены первые ${com.wdtt.client.BypassRoutes.MAX_RULES} записей.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.bypassApps?.let { Line("Мимо туннеля", counted(it.size, "приложение", "приложения", "приложений")) }
            preview.allowedApps?.let { Line("Только выбранные", counted(it.size, "приложение", "приложения", "приложений")) }
            preview.onlySelected?.let { Line("Режим", if (it) "Только выбранные" else "Мимо туннеля") }
        }
        Text(
            if (preview.fromQwdtt) "Файл qWDTT: в нём один список приложений, он заменит список указанного режима.\nПеречисленные списки будут заменены."
            else "Перечисленные списки будут заменены.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ArdttButton(text = "Применить", onClick = onApply, modifier = Modifier.fillMaxWidth())
        ArdttButton(text = "Отмена", variant = ArdttButtonVariant.Tonal, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun Option(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun counted(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    val word = when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}

private fun readClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}
