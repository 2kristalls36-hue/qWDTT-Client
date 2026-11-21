package com.wdtt.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import com.wdtt.client.ui.components.surface.glassBackdropSource
import com.wdtt.client.ui.components.layout.LiteTabIcons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
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
    val appHaptics = com.wdtt.client.ui.motion.rememberAppHaptics(hapticsOn)
    val glassTransparency by lite.glassTransparency.collectAsState(com.wdtt.client.lite.LiteSettings.DEFAULT_GLASS_TRANSPARENCY)
    backdrop?.transparency = glassTransparency
    val items = listOf(
        ArdttNavItem(Routes.HOME, "Главная", Icons.Outlined.Home, glyph = LiteTabIcons.Power),
        ArdttNavItem(Routes.PROFILES, "Профили", Icons.Outlined.Person, glyph = LiteTabIcons.Cards),
        ArdttNavItem(Routes.APPS, "Приложения", Icons.Outlined.Apps, glyph = LiteTabIcons.Tiles),
        // Gear with the ARDTT half turn; a dot when a newer version is published.
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
