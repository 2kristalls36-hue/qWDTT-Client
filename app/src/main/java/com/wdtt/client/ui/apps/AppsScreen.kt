package com.wdtt.client.ui.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Language
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.LaunchedEffect
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

internal data class AppItem(val packageName: String, val name: String, val system: Boolean)

/**
 * Apps that bypass the tunnel (or, in the second mode, the only apps that use it).
 * Stored in the same settings qWDTT uses, so the RAW tunnel applies them on connect.
 */
@Composable
fun AppsScreen(settingsStore: SettingsStore) {
    val context = LocalContext.current
    val haptics = com.wdtt.client.ui.motion.LocalAppHaptics.current
    val scope = rememberCoroutineScope()
    // Separate lists per mode: «Мимо туннеля» and «Только выбранные».
    LaunchedEffect(Unit) { settingsStore.migrateSplitAppLists() }
    val onlySelectedMode by settingsStore.isWhitelist.collectAsStateWithLifecycle(false)
    val excludedRaw by settingsStore.excludedApps.collectAsStateWithLifecycle("")
    val allowedRaw by settingsStore.allowedApps.collectAsStateWithLifecycle("")
    val savedRaw = if (onlySelectedMode) allowedRaw else excludedRaw
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
    // Another mode shows another list: drop the lagging sort copy of the old one.
    LaunchedEffect(onlySelectedMode) { sortSelected = null; leaving = emptySet() }
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
    // Two panes: apps and sites (addresses and domains outside the tunnel).
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

    var transferOpen by remember { mutableStateOf(false) }
    if (transferOpen) BypassTransferSheet(onDismiss = { transferOpen = false })
    val screenGlass = com.wdtt.client.ui.components.surface.rememberScreenGlass()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    com.wdtt.client.ui.motion.MagnetSnapTick(listState)
    com.wdtt.client.ui.components.layout.GlassHeaderPage(
        title = "Обход",
        scrolled = listState.canScrollBackward,
        backdrop = screenGlass,
        actions = {
            ArdttButton(
                icon = com.wdtt.client.ui.components.icons.TransferIcon,
                contentDescription = "Экспорт и импорт",
                variant = ArdttButtonVariant.Icon,
                onClick = { transferOpen = true },
            )
        },
        overlay = {
            // Search floats above the tab pill; with the keyboard open it rides on top of it.
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
                    // Add row: field and button; the subdomain chip appears while typing a domain.
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
        // One band for all stubs, sweeping left to right; only while loading, so the
        // loaded list is not redrawn every frame.
        val shift = if (apps == null) {
            val shimmerT = androidx.compose.animation.core.rememberInfiniteTransition(label = "appsLoading")
            shimmerT.animateFloat(
                -400f, 1400f,
                androidx.compose.animation.core.infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)),
                label = "shift",
            ).value
        } else 0f
        val stubBase = com.wdtt.client.ui.theme.liteInsetColor()
        val stubLight = com.wdtt.client.ui.theme.liteCardColor()
        val shimmer = Brush.horizontalGradient(listOf(stubBase, stubLight, stubBase), startX = shift, endX = shift + 420f)
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
                // Tabs, not buttons: the section the actions apply to is always marked.
                com.wdtt.client.ui.components.control.ArdttTabRow(
                    tabs = listOf(
                        com.wdtt.client.ui.components.control.ArdttTab(
                            "Приложения", androidx.compose.material.icons.Icons.Outlined.Apps, count = selected.size,
                        ),
                        com.wdtt.client.ui.components.control.ArdttTab(
                            "Сайты", androidx.compose.material.icons.Icons.Outlined.Language, count = sites.size,
                        ),
                    ),
                    selected = if (sitesPane) 1 else 0,
                    onSelect = { sitesPane = it == 1 },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (sitesPane) {
                item(key = "sites-hint") {
                    Column(Modifier.animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(120)).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        Box(Modifier.animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(120)).fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
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
                Column(Modifier.animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(120)).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    // A tinted card: the switch is a list filter and should not get lost among the rows.
                    ArdttSwitchRow(
                        title = "Системные приложения",
                        checked = showSystem,
                        onCheckedChange = { showSystem = it },
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.09f))
                            .padding(horizontal = 14.dp),
                    )
                }
            }
            when {
                apps == null -> items(12, key = { "stub$it" }) { i ->
                    AppRowStub(shimmer, titleWidth = listOf(0.62f, 0.48f, 0.7f, 0.55f)[i % 4], subtitleWidth = listOf(0.4f, 0.52f, 0.34f, 0.46f)[i % 4])
                }
                visible.isEmpty() -> item(key = "empty") {
                    Box(Modifier.animateItem(fadeInSpec = tween(220), fadeOutSpec = tween(120)).fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
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
                            val csv = next.joinToString(",")
                            if (onlySelectedMode) settingsStore.saveAllowedApps(csv) else settingsStore.saveExcludedApps(csv)
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
            .background(com.wdtt.client.ui.theme.liteCardColor())
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
internal fun AppRow(
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
    AppRowFrame(
        modifier = modifier
            .graphicsLayer {
                alpha = 1f - out
                translationY = -10.dp.toPx() * out
            }
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle),
        icon = { m ->
            icon?.let { Image(it, contentDescription = null, modifier = m) }
                ?: Box(m.background(com.wdtt.client.ui.theme.liteInsetColor()))
        },
        title = { Text(app.name, style = AppRowTitle(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        subtitle = { Text(app.packageName, style = AppRowSubtitle(), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailing = { com.wdtt.client.ui.components.control.LiteSwitch(checked = checked, onCheckedChange = null) },
    )
}

@Composable private fun AppRowTitle() = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
@Composable private fun AppRowSubtitle() = MaterialTheme.typography.labelSmall

/**
 * Layout shared by an app row and its loading stub, so the stub is exactly as tall:
 * same padding, icon, two text lines and switch.
 */
@Composable
private fun AppRowFrame(
    modifier: Modifier,
    icon: @Composable (Modifier) -> Unit,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(com.wdtt.client.ui.theme.liteCardColor())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f)) {
            title()
            subtitle()
        }
        trailing()
    }
}

/**
 * Loading stub: a light band sweeps over an icon, two text bars
 * and the switch. Text bars sit in slots of the text line height, so the stub matches
 * a loaded row exactly.
 */
@Composable
internal fun AppRowStub(brush: Brush, titleWidth: Float, subtitleWidth: Float) {
    AppRowFrame(
        modifier = Modifier.clearAndSetSemantics {},
        icon = { m -> Box(m.background(brush)) },
        title = { ShimmerLine(brush, titleWidth, AppRowTitle()) },
        subtitle = { ShimmerLine(brush, subtitleWidth, AppRowSubtitle()) },
        trailing = { com.wdtt.client.ui.components.control.LiteSwitch(checked = false, onCheckedChange = null, enabled = false) },
    )
}

@Composable
private fun ShimmerLine(brush: Brush, widthFraction: Float, style: androidx.compose.ui.text.TextStyle) {
    val density = LocalDensity.current
    // An invisible one-line text gives the exact line height the real text would take.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Text(" ", style = style, maxLines = 1, modifier = Modifier.alpha(0f))
        Box(
            Modifier
                .fillMaxWidth(widthFraction)
                .height(with(density) { style.fontSize.toDp() })
                .clip(RoundedCornerShape(50))
                .background(brush),
        )
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
