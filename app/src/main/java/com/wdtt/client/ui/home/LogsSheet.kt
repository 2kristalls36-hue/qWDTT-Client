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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.lite.LogErrors
import com.wdtt.client.lite.RawLog
import com.wdtt.client.ui.components.control.ArdttButton
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import com.wdtt.client.ui.components.surface.ArdttBottomSheet
import com.wdtt.client.ui.theme.ArdttColors

/**
 * Hidden raw connection log (long press on the app title): every line of the tunnel
 * client exactly as printed, plus start, stop and exit markers. Newest at the bottom.
 * Opens on «Ошибки»: the error lines only, with the markers of the runs they belong to;
 * «Все события» shows everything, the errors in red. Copy and share take what is shown.
 */
@Composable
fun LogsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val version by RawLog.version.collectAsStateWithLifecycle()
    val all = remember(version) { RawLog.snapshot() }
    // Errors first: that is what the journal is opened for.
    var errorsOnly by rememberSaveable { mutableStateOf(true) }
    val errors = remember(all) { LogErrors.only(all) }
    val errorCount = remember(errors) { errors.count { !LogErrors.isMarker(it) } }
    val lines = if (errorsOnly) errors else all
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, errorsOnly) { if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex) }

    ArdttBottomSheet(onDismissRequest = onDismiss, scrollable = false, maxHeightFraction = 0.92f, modifier = Modifier.padding(horizontal = 20.dp)) {
        Text("Журнал подключения", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        com.wdtt.client.ui.components.control.ArdttChoiceChipRow(
            choices = listOf(
                com.wdtt.client.ui.components.control.ArdttChoice(true, "Ошибки", count = errorCount),
                com.wdtt.client.ui.components.control.ArdttChoice(false, "Все события"),
            ),
            selected = errorsOnly,
            onSelect = { errorsOnly = it },
        )
        Text(
            when {
                all.isEmpty() -> "Журнал пуст: туннель ещё не запускался."
                errorsOnly -> "Строки с ошибками и отметки запуска и остановки клиента."
                else -> "Строк: ${all.size}. Вывод клиента без обработки."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 420 dp, less on a short screen: the buttons stay on it.
        SelectionContainer(Modifier.weight(1f, fill = false)) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ArdttColors.TerminalBgDark)
                    .padding(10.dp),
            ) {
                if (errorsOnly && lines.isEmpty() && all.isNotEmpty()) {
                    item {
                        Text(
                            "Ошибок не обнаружено.",
                            color = ArdttColors.TerminalText.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
                itemsIndexed(lines) { _, line ->
                    Text(
                        line,
                        color = when {
                            LogErrors.isMarker(line) -> ArdttColors.TerminalBlue
                            LogErrors.isError(line) -> ArdttColors.TerminalRed
                            else -> ArdttColors.TerminalText
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
        }
        ArdttButton(
            text = "Поделиться",
            variant = ArdttButtonVariant.Primary,
            enabled = lines.isNotEmpty(),
            onClick = {
                haptics.perform(com.wdtt.client.ui.motion.Haptic.Click)
                runCatching { com.wdtt.client.lite.JournalShare.share(context, lines) }
                    .onFailure { Toast.makeText(context, "Не удалось подготовить файл журнала.", Toast.LENGTH_SHORT).show() }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ArdttButton(
                text = "Скопировать",
                variant = ArdttButtonVariant.Tonal,
                enabled = lines.isNotEmpty(),
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Журнал qWDTT Client", lines.joinToString("\n")))
                    haptics.perform(com.wdtt.client.ui.motion.Haptic.Confirm)
                    Toast.makeText(context, if (errorsOnly) "Строки с ошибками скопированы." else "Журнал скопирован.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                fillMaxWidth = false,
            )
            ArdttButton(
                text = "Очистить",
                variant = ArdttButtonVariant.Outlined,
                // Always the whole journal.
                enabled = all.isNotEmpty(),
                onClick = { RawLog.clear() },
                modifier = Modifier.weight(1f),
                fillMaxWidth = false,
            )
        }
    }
}
