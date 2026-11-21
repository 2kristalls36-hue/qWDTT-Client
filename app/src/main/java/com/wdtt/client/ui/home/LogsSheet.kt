package com.wdtt.client.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.lite.RawLog
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import com.wdtt.client.ui.components.surface.ArdttBottomSheet
import com.wdtt.client.ui.theme.ArdttColors

/**
 * Hidden raw connection log (long press on the app title): every line of the tunnel
 * client exactly as printed, plus start, stop and exit markers. Newest at the bottom.
 */
@Composable
fun LogsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val version by RawLog.version.collectAsStateWithLifecycle()
    val lines = remember(version) { RawLog.snapshot() }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }

    ArdttBottomSheet(onDismissRequest = onDismiss, scrollable = false, modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Журнал подключения", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(
            if (lines.isEmpty()) "Журнал пуст: туннель ещё не запускался."
            else "Строк: ${lines.size}. Вывод клиента без обработки.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ArdttColors.TerminalBgDark)
                    .padding(10.dp),
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        line,
                        color = if (line.startsWith("=====")) ArdttColors.TerminalBlue else ArdttColors.TerminalText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArdttButton(
                text = "Скопировать",
                variant = ArdttButtonVariant.Tonal,
                enabled = lines.isNotEmpty(),
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Журнал qWDTT Client", lines.joinToString("\n")))
                    haptics.perform(com.wdtt.client.ui.motion.Haptic.Confirm)
                    Toast.makeText(context, "Журнал скопирован.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                fillMaxWidth = false,
            )
            ArdttButton(
                text = "Очистить",
                variant = ArdttButtonVariant.Outlined,
                enabled = lines.isNotEmpty(),
                onClick = { RawLog.clear() },
                modifier = Modifier.weight(1f),
                fillMaxWidth = false,
            )
        }
    }
}
