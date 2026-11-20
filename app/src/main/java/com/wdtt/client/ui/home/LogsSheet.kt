package com.wdtt.client.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.TunnelManager
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import com.wdtt.client.ui.components.surface.ArdttBottomSheet
import com.wdtt.client.ui.theme.ArdttColors

/** Hidden connection log: opened by a long press on the app title on the home screen. */
@Composable
fun LogsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val logs by TunnelManager.logs.collectAsStateWithLifecycle()
    val fatal by TunnelManager.lastFatalError.collectAsStateWithLifecycle()
    val text = buildString {
        fatal?.let { appendLine("Ошибка: $it").appendLine() }
        logs.forEach { e -> appendLine(if (e.count > 1) "${e.message} ×${e.count}" else e.message) }
    }.ifBlank { "Журнал пуст." }
    ArdttBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Журнал подключения", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ArdttColors.TerminalBgDark)
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            SelectionContainer {
                Text(text, color = ArdttColors.TerminalText, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        ArdttButton(
            text = "Скопировать",
            variant = ArdttButtonVariant.Tonal,
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Журнал qWDTT Lite", text))
                Toast.makeText(context, "Журнал скопирован.", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
