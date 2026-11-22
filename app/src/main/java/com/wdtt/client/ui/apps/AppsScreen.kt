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
import androidx.compose.material.icons.filled.Close
import com.wdtt.client.ui.components.control.ArdttChoiceChip
import com.wdtt.client.ui.components.control.ArdttButtonVariant
import com.wdtt.client.ui.components.control.ArdttButton
import androidx.compose.animation.core.VisibilityThreshold
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val scope = rememberCoroutineScope()
    val savedRaw by settingsStore.excludedApps.collectAsStateWithLifecycle("")
    val selected = remember(savedRaw) { savedRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
    val onlySelected by settingsStore.isWhitelist.collectAsStateWithLifecycle(false)
    val running by TunnelManager.running.collectAsStateWithLifecycle()
    var showSystem by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val apps by produceState<List<AppItem>?>(null) { value = withContext(Dispatchers.IO) { loadApps(context) } }

    // The list is sorted by a copy of the selection that lags behind it: a toggled row
    // stays in place while its switch animates, then leaves (a slight swipe up with a
    // fade) and appears at its new place.
    var sortSelected by remember { mutableStateOf<Set<String>?>(null) }
    var leaving by remember { mutableStateOf(emptySet<String>()) }
    val order = sortSelected ?: selected
    val visible = remember(apps, query, showSystem, order) {
        val q = query.trim().lowercase(Locale.getDefault())
        (apps ?: emptyList())
            .filter { showSystem || !it.system || it.packageName in order }
            .filter { q.isEmpty() || it.name.lowercase(Locale.getDefault()).contains(q) || it.packageName.contains(q) }
            .sortedWith(compareByDescending<AppItem> { it.packageName in order }.thenBy { it.name.lowercase(Locale.getDefault()) })
    }

    val imePad = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val keyboardVisible = imePad > 12.dp
    // With liquid glass on, the screen content is recorded for the floating header and
    // search bar to refract (they cannot sample the root layer they are drawn into).
    // Two panes, as in ARDTT: apps and sites (addresses and domains outside the tunnel).
    var sitesPane by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val sitesRaw by settingsStore.bypassRoutes.collectAsStateWithLifecycle("")
    val sites = remember(sitesRaw) { com.wdtt.client.BypassRoutes.parseRules(sitesRaw) }
    var newSite by remember { mutableStateOf("") }
    var siteError by remember { mutableStateOf<String?>(null) }
    var subdomains by remember { mutableStateOf(false) }
    fun saveSites(list: List<String>) = scope.launch { settingsStore.saveBypassRoutes(list.joinToString("\n")) }
    fun addSite() {
        val rule = com.wdtt.client.BypassRoutes.normalizeRuleInput(newSite).lowercase(Locale.US)
        val withSub = if (subdomains && !rule.startsWith("*.") && rule.any { it.isLetter() }) "*.$rule" else rule
        siteError = when {
            rule.isEmpty() -> null
            !com.wdtt.client.BypassRoutes.isValidRule(withSub) -> "Адрес не распознан."
            withSub in sites -> "Адрес уже есть в списке."
            sites.size >= com.wdtt.client.BypassRoutes.MAX_RULES -> "В списке максимальное число адресов."
            else -> null
        }
        if (rule.isEmpty() || siteError != null) {
            haptics.perform(com.wdtt.client.ui.motion.Haptic.Reject)
            return
        }
        haptics.perform(com.wdtt.client.ui.motion.Haptic.Confirm)
        saveSites(listOf(withSub) + sites)
        newSite = ""
    }

    val screenGlass = com.wdtt.client.ui.components.surface.rememberScreenGlass()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    com.wdtt.client.ui.motion.MagnetSnapTick(listState)
    com.wdtt.client.ui.components.layout.GlassHeaderPage(
        title = "Обход",
        scrolled = listState.canScrollBackward,
        backdrop = screenGlass,
        overlay = {
            // Search floats above the tab pill, as in ARDTT; with the keyboard open it rides on top of it.
            androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides screenGlass) {
                val bottom = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = maxOf(ArdttBottomChrome.stickyBottomPadding(), imePad + 8.dp))
                if (!sitesPane) {
                    ArdttSearchBar(
                        value = query,
                        onValueChange = { query = it },
                        keyboardVisible = keyboardVisible,
                        modifier = bottom,
                    )
                } else {
                    // Add row: field and button, as in ARDTT; the subdomain chip appears while typing a domain.
                    Column(bottom, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isDomain = newSite.any { it.isLetter() }
                        androidx.compose.animation.AnimatedVisibility(visible = isDomain && keyboardVisible) {
                            ArdttChoiceChip(
                                label = "С поддоменами",
                                selected = subdomains,
                                enabled = true,
                                onClick = { subdomains = !subdomains },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ArdttSearchBar(
                                value = newSite,
                                onValueChange = { newSite = it.filter { c -> c != '\n' && c != '\r' }; siteError = null },
                                keyboardVisible = keyboardVisible,
                                placeholder = "Сайт или IP-адрес",
                                onSubmit = { addSite() },
                                modifier = Modifier.weight(1f),
                            )
                            ArdttButton(
                                text = "Добавить",
                                onClick = { addSite() },
                                enabled = newSite.isNotBlank(),
                                fillMaxWidth = false,
                            )
                        }
                    }
                }
            }
        },
    ) { top ->
        LazyColumn(
            state = listState,
            // Magnet: after a fling the nearest row settles flush with the top.
            flingBehavior = com.wdtt.client.ui.motion.rememberMagnetSnapFling(listState),
            // The last rows can pass under the floating search bar.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = ArdttBottomChrome.scrollContentPadding(extra = 12.dp)),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "panes") {
                ArdttChoiceChipRow(
                    choices = listOf(ArdttChoice(false, "Приложения"), ArdttChoice(true, "Сайты")),
                    selected = sitesPane,
                    onSelect = { sitesPane = it },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (sitesPane) {
                item(key = "sites-hint") {
                    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Сайты и IP-адреса из списка открываются вне туннеля.",
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
                        siteError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = com.wdtt.client.ui.home.ToneColors.Critical)
                        }
                    }
                }
                if (sites.isEmpty()) {
                    item(key = "sites-empty") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("Список пуст.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(sites, key = { "site|$it" }) { rule ->
                        SiteRow(
                            rule = rule,
                            modifier = Modifier.animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(160)),
                            onRemove = {
                                haptics.perform(com.wdtt.client.ui.motion.Haptic.Tick)
                                saveSites(sites - rule)
                            },
                        )
                    }
                }
            } else {
            item(key = "controls") {
                Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            }
            when {
                apps == null -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                visible.isEmpty() -> item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (query.isNotBlank()) "Нет совпадений." else "Нет приложений.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // The key carries the group, so a row that changes group is removed and
                // inserted (fade) instead of sliding across the whole list.
                else -> items(visible, key = { "${it.packageName}|${it.packageName in order}" }) { app ->
                    AppRow(
                        app,
                        checked = app.packageName in selected,
                        leaving = app.packageName in leaving,
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(220),
                            placementSpec = spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold),
                            fadeOutSpec = tween(120),
                        ),
                    ) { on ->
                        haptics.toggle(on)
                        val next = if (on) selected + app.packageName else selected - app.packageName
                        val pkg = app.packageName
                        scope.launch {
                            settingsStore.saveExcludedApps(next.joinToString(","))
                            if (sortSelected == null) sortSelected = selected
                            delay(330) // the switch finishes first
                            leaving = leaving + pkg
                            delay(170) // slight swipe up with a fade
                            sortSelected = (sortSelected ?: emptySet()).let { if (on) it + pkg else it - pkg }
                            leaving = leaving - pkg
                        }
                    }
                }
            }
            }
        }
    }
}

/** One site rule: the address and a remove button. */
@Composable
private fun SiteRow(rule: String, modifier: Modifier = Modifier, onRemove: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rule,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        ArdttButton(
            onClick = onRemove,
            variant = ArdttButtonVariant.Icon,
            icon = androidx.compose.material.icons.Icons.Filled.Close,
            contentDescription = "Удалить $rule",
        )
    }
}

@Composable
private fun AppRow(
    app: AppItem,
    checked: Boolean,
    leaving: Boolean = false,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
) {
    val out by animateFloatAsState(if (leaving) 1f else 0f, tween(170), label = "leave")
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
        modifier
            .graphicsLayer {
                alpha = 1f - out
                translationY = -10.dp.toPx() * out
            }
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
        com.wdtt.client.ui.components.control.LiteSwitch(checked = checked, onCheckedChange = null)
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
