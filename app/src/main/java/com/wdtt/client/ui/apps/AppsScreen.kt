package com.wdtt.client.ui.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.wdtt.client.ui.components.surface.glassBackdropSource
import com.wdtt.client.ui.components.surface.GlassBackdrop
import com.wdtt.client.ui.components.surface.LocalGlassBackdrop
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import com.wdtt.client.ui.components.control.ArdttSearchBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wdtt.client.SettingsStore
import com.wdtt.client.TunnelManager
import com.wdtt.client.ui.components.control.ArdttChoice
import com.wdtt.client.ui.components.control.ArdttChoiceChipRow
import com.wdtt.client.ui.components.control.ArdttSwitchRow
import com.wdtt.client.ui.components.layout.ArdttBottomChrome
import com.wdtt.client.ui.components.layout.ArdttPageHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private data class AppItem(val packageName: String, val name: String, val system: Boolean)

/**
 * Apps that bypass the tunnel (or, in the second mode, the only apps that use it).
 * Stored in the same settings qWDTT uses, so the RAW tunnel applies them on connect.
 */
@Composable
fun AppsScreen(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedRaw by settingsStore.excludedApps.collectAsStateWithLifecycle("")
    val selected = remember(savedRaw) { savedRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
    val onlySelected by settingsStore.isWhitelist.collectAsStateWithLifecycle(false)
    val running by TunnelManager.running.collectAsStateWithLifecycle()
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val apps by produceState<List<AppItem>?>(null) { value = withContext(Dispatchers.IO) { loadApps(context) } }

    val visible = remember(apps, query, showSystem, selected) {
        val q = query.trim().lowercase(Locale.getDefault())
        (apps ?: emptyList())
            .filter { showSystem || !it.system || it.packageName in selected }
            .filter { q.isEmpty() || it.name.lowercase(Locale.getDefault()).contains(q) || it.packageName.contains(q) }
            .sortedWith(compareByDescending<AppItem> { it.packageName in selected }.thenBy { it.name.lowercase(Locale.getDefault()) })
    }

    val imePad = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val keyboardVisible = imePad > 12.dp
    // With liquid glass on, the list is recorded for the floating search bar to refract.
    // A separate layer: the bar cannot sample the root one it is itself drawn into.
    val rootGlass = LocalGlassBackdrop.current
    val listLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val listGlass = remember(listLayer) { GlassBackdrop(listLayer) }.takeIf { rootGlass != null }
    if (listGlass != null && rootGlass != null) listGlass.transparency = rootGlass.transparency
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .glassBackdropSource(listGlass, fill = MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        ArdttPageHeader(title = "Приложения")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ArdttChoiceChipRow(
                choices = listOf(
                    ArdttChoice(false, "Мимо туннеля"),
                    ArdttChoice(true, "Только выбранные"),
                ),
                selected = onlySelected,
                onSelect = { scope.launch { settingsStore.saveExceptionsMode(it) } },
            )
            Text(
                if (onlySelected) "Через туннель работают только выбранные приложения."
                else "Выбранные приложения работают вне туннеля.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (running) {
                Text(
                    "Изменения применяются при следующем подключении.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ArdttSwitchRow(title = "Системные приложения", checked = showSystem, onCheckedChange = { showSystem = it })
        }

        if (apps == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isNotBlank()) "Нет совпадений." else "Нет приложений.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LazyColumn(
                state = listState,
                // Magnet: after a fling the nearest row settles flush with the top.
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(
                    lazyListState = listState,
                    snapPosition = androidx.compose.foundation.gestures.snapping.SnapPosition.Start,
                ),
                // The last rows can pass under the floating search bar.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = ArdttBottomChrome.scrollContentPadding(extra = 12.dp)),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visible, key = { it.packageName }) { app ->
                    AppRow(app, checked = app.packageName in selected) { on ->
                        val next = if (on) selected + app.packageName else selected - app.packageName
                        scope.launch { settingsStore.saveExcludedApps(next.joinToString(",")) }
                    }
                }
            }
        }
    }
    // Search floats above the tab pill, as in ARDTT; with the keyboard open it rides on top of it.
    androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides listGlass) {
    ArdttSearchBar(
        value = query,
        onValueChange = { query = it },
        keyboardVisible = keyboardVisible,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .zIndex(2f)
            .padding(horizontal = 16.dp)
            .padding(bottom = maxOf(ArdttBottomChrome.stickyBottomPadding(), imePad + 8.dp)),
    )
    }
    }
}

@Composable
private fun AppRow(app: AppItem, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap(96, 96).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val iconModifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
        icon?.let { Image(it, contentDescription = null, modifier = iconModifier) }
            ?: Box(iconModifier.background(MaterialTheme.colorScheme.surfaceVariant))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun loadApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val own = context.packageName
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .asSequence()
        .filter { it.packageName != own }
        .map {
            AppItem(
                packageName = it.packageName,
                name = pm.getApplicationLabel(it).toString(),
                system = (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
            )
        }
        .toList()
}
