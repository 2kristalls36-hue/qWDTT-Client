package com.wdtt.client.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import com.wdtt.client.ui.components.surface.glassBackdropSource
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.wdtt.client.SettingsStore
import com.wdtt.client.ui.components.layout.ArdttBackdrop
import com.wdtt.client.ui.components.layout.ArdttNavItem
import com.wdtt.client.ui.components.layout.ArdttNavigationBar

internal object Routes {
    const val HOME = "tunnel"
    const val PROFILES = "profiles"
    const val APPS = "exceptions"
    const val SETTINGS = "settings"
}

@Composable
fun AppRoot(settingsStore: SettingsStore) {
    var route by rememberSaveable { mutableStateOf(Routes.HOME) }
    val pageOrder = listOf(Routes.HOME, Routes.PROFILES, Routes.APPS, Routes.SETTINGS)
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = pageOrder.indexOf(route).coerceAtLeast(0)) { pageOrder.size }
    // Position of the tab bar droplet, in tabs; the page strip is kept at the same place.
    var dropletPos by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(pageOrder.indexOf(route).coerceAtLeast(0).toFloat()) }
    androidx.compose.runtime.LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { dropletPos }.collect { p ->
            val page = kotlin.math.round(p).toInt().coerceIn(0, pageOrder.size - 1)
            pager.scrollToPage(page, (p - page).coerceIn(-0.5f, 0.5f))
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val update by com.wdtt.client.lite.AppUpdate.available.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { com.wdtt.client.lite.AppUpdate.checkIfDue(context) }
    val lite = androidx.compose.runtime.remember { com.wdtt.client.lite.LiteSettings(context) }
    // Until the settings are read here, as they were read at the start (see StartupSettings).
    val startup = com.wdtt.client.lite.StartupSettings
    val glassSaved by lite.liquidGlass.collectAsState(null)
    val glassOn = glassSaved ?: startup.liquidGlass ?: false
    val glassLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val backdrop = androidx.compose.runtime.remember(glassLayer) { com.wdtt.client.ui.components.surface.GlassBackdrop(glassLayer) }
        .takeIf { glassOn && com.wdtt.client.ui.components.surface.LiquidGlass.supported }
    val hapticsOn by lite.haptics.collectAsState(true)
    val magnetOn by lite.magnet.collectAsState(true)
    val appHaptics = com.wdtt.client.ui.motion.rememberAppHaptics(hapticsOn)
    val transparencySaved by lite.glassTransparency.collectAsState(null)
    val glassTransparency = transparencySaved ?: startup.glassTransparency ?: com.wdtt.client.lite.LiteSettings.DEFAULT_GLASS_TRANSPARENCY
    backdrop?.transparency = glassTransparency
    // The glass rim takes the colour of the power level, like the connect button;
    // only the maximum level keeps the rainbow.
    val powerSaved by lite.powerLevel.collectAsState(null)
    val power = powerSaved ?: startup.powerLevel ?: com.wdtt.client.lite.PowerLevel.Default
    val rimTarget = com.wdtt.client.ui.home.levelColors(
        if (power == com.wdtt.client.lite.PowerLevel.Maximum) com.wdtt.client.lite.PowerLevel.Balanced else power,
    ).accent
    val rimColor by androidx.compose.animation.animateColorAsState(rimTarget, label = "rim")
    backdrop?.rim = if (power == com.wdtt.client.lite.PowerLevel.Maximum) null else rimColor
    val items = listOf(
        // Tab icons; the motions follow the routes (key turn, folder lift,
        // filter slide, gear half turn).
        ArdttNavItem(Routes.HOME, "Главная", Icons.Outlined.VpnKey),
        ArdttNavItem(Routes.PROFILES, "Профили", Icons.Outlined.Folder),
        ArdttNavItem(Routes.APPS, "Обход", Icons.Outlined.FilterList),
        ArdttNavItem(Routes.SETTINGS, "Настройки", Icons.Outlined.Settings, dot = update != null),
    )
    // A link or file opened from another app is imported on the profiles tab.
    val incoming by com.wdtt.client.MainActivity.pendingFileUri
    androidx.compose.runtime.LaunchedEffect(incoming) { if (incoming != null) route = Routes.PROFILES }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
    ) {
    // The physical screen (rounded corners, navigation): the tab bar fits itself to it.
    val screenFitSaved by lite.screenFit.collectAsState(null)
    val screenFitOn = screenFitSaved ?: startup.screenFit ?: true
    val measuredShape = com.wdtt.client.ui.components.layout.rememberScreenShape()
    // Setting off: as if the corners were unknown, everything keeps its usual shape.
    val screenShape = if (screenFitOn) measuredShape else com.wdtt.client.ui.components.layout.ScreenShape.Unknown
    // New Year: wakes up only from 31 December to 2 January.
    // Santa's train goes while the connection is on or being made, not while it is paused.
    val tunnelOn by com.wdtt.client.TunnelManager.running.collectAsState()
    val tunnelConnecting by com.wdtt.client.TunnelManager.isConnecting.collectAsState()
    val tunnelReconnecting by com.wdtt.client.TunnelManager.isReconnecting.collectAsState()
    val tunnelPaused by com.wdtt.client.TunnelManager.trustedPause.collectAsState()
    val travelling = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    travelling.value = tunnelPaused == null && (tunnelOn || tunnelConnecting || tunnelReconnecting)
    val festive = com.wdtt.client.ui.festive.rememberFestive(power, travelling)
    androidx.compose.runtime.CompositionLocalProvider(
        com.wdtt.client.ui.festive.LocalFestive provides festive,
        com.wdtt.client.ui.components.layout.LocalScreenShape provides screenShape,
        com.wdtt.client.ui.components.surface.LocalGlassBackdrop provides backdrop,
        com.wdtt.client.ui.motion.LocalAppHaptics provides appHaptics,
        com.wdtt.client.ui.motion.LocalMagnetEnabled provides magnetOn,
        androidx.compose.ui.platform.LocalHapticFeedback provides androidx.compose.runtime.remember(appHaptics) { com.wdtt.client.ui.motion.SettingAwareHapticFeedback(appHaptics) },
    ) {
    Box(Modifier.fillMaxSize()) {
        // Everything under the tab bar is recorded once per frame for the glass to refract.
        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
        ArdttBackdrop(modifier = Modifier.fillMaxSize())
        // The screens are pages of one strip that follows the droplet of the tab bar: drag
        // the droplet and the screens slide along with it; a tap moves both together. The
        // content itself does not swipe between screens (lists and sliders keep the gesture).
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            userScrollEnabled = false,
            key = { pageOrder[it] },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (pageOrder[page]) {
                Routes.HOME -> com.wdtt.client.ui.home.HomeScreen(
                    settingsStore = settingsStore,
                    onOpenProfiles = { route = Routes.PROFILES },
                )
                Routes.PROFILES -> com.wdtt.client.ui.profiles.ProfilesScreen(settingsStore)
                Routes.SETTINGS -> com.wdtt.client.ui.home.SettingsScreen()
                else -> com.wdtt.client.ui.apps.AppsScreen(settingsStore)
            }
        }
        }
        val level = power
        val levelColors = com.wdtt.client.ui.home.levelColors(level)
        ArdttNavigationBar(
            items = items,
            selectedRoute = route,
            onSelect = { route = it },
            onPosition = { dropletPos = it },
            modifier = Modifier.align(Alignment.BottomCenter),
            accent = when (level) {
                com.wdtt.client.lite.PowerLevel.Stable -> com.wdtt.client.ui.components.layout.NavAccent.Plain
                com.wdtt.client.lite.PowerLevel.Balanced -> com.wdtt.client.ui.components.layout.NavAccent.Sheen(levelColors.accent)
                // New Year: garland lights instead of the neon, the tab glints warm.
                com.wdtt.client.lite.PowerLevel.Maximum -> if (festive.active) com.wdtt.client.ui.components.layout.NavAccent.Sheen(com.wdtt.client.ui.festive.GarlandColors[1])
                    else com.wdtt.client.ui.components.layout.NavAccent.Neon(levelColors.neon)
            },
        )
        // New Year, maximum level: one garland across the top, under the status bar.
        com.wdtt.client.ui.festive.TopGarland(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                .height(18.dp),
        )
        com.wdtt.client.ui.festive.SnowfallOverlay(Modifier.fillMaxSize())
    }
    }
}
}
