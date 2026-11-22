package com.wdtt.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val update by com.wdtt.client.lite.AppUpdate.available.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { com.wdtt.client.lite.AppUpdate.checkIfDue(context) }
    val lite = androidx.compose.runtime.remember { com.wdtt.client.lite.LiteSettings(context) }
    val glassOn by lite.liquidGlass.collectAsState(false)
    val glassLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val backdrop = androidx.compose.runtime.remember(glassLayer) { com.wdtt.client.ui.components.surface.GlassBackdrop(glassLayer) }
        .takeIf { glassOn && com.wdtt.client.ui.components.surface.LiquidGlass.supported }
    val hapticsOn by lite.haptics.collectAsState(true)
    val magnetOn by lite.magnet.collectAsState(true)
    val appHaptics = com.wdtt.client.ui.motion.rememberAppHaptics(hapticsOn)
    val glassTransparency by lite.glassTransparency.collectAsState(com.wdtt.client.lite.LiteSettings.DEFAULT_GLASS_TRANSPARENCY)
    backdrop?.transparency = glassTransparency
    // The glass rim takes the colour of the power level, like the connect button;
    // only the maximum level keeps the rainbow.
    val power by lite.powerLevel.collectAsState(com.wdtt.client.lite.PowerLevel.Default)
    val rimTarget = com.wdtt.client.ui.home.levelColors(
        if (power == com.wdtt.client.lite.PowerLevel.Maximum) com.wdtt.client.lite.PowerLevel.Balanced else power,
    ).accent
    val rimColor by androidx.compose.animation.animateColorAsState(rimTarget, label = "rim")
    backdrop?.rim = if (power == com.wdtt.client.lite.PowerLevel.Maximum) null else rimColor
    val items = listOf(
        // Same icons as ARDTT; the motions follow the routes (key turn, folder lift,
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
    androidx.compose.runtime.CompositionLocalProvider(
        com.wdtt.client.ui.components.surface.LocalGlassBackdrop provides backdrop,
        com.wdtt.client.ui.motion.LocalAppHaptics provides appHaptics,
        com.wdtt.client.ui.motion.LocalMagnetEnabled provides magnetOn,
        androidx.compose.ui.platform.LocalHapticFeedback provides androidx.compose.runtime.remember(appHaptics) { com.wdtt.client.ui.motion.SettingAwareHapticFeedback(appHaptics) },
    ) {
    Box(Modifier.fillMaxSize()) {
        // Everything under the tab bar is recorded once per frame for the glass to refract.
        Box(Modifier.fillMaxSize().glassBackdropSource(backdrop)) {
        ArdttBackdrop(modifier = Modifier.fillMaxSize())
        AnimatedContent(
            targetState = route,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab",
            modifier = Modifier.fillMaxSize(),
        ) { current ->
            when (current) {
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
        ArdttNavigationBar(
            items = items,
            selectedRoute = route,
            onSelect = { route = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    }
}
}
