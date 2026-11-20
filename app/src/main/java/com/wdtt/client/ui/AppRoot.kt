package com.wdtt.client.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
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
}

@Composable
fun AppRoot(settingsStore: SettingsStore) {
    var route by rememberSaveable { mutableStateOf(Routes.HOME) }
    val items = listOf(
        ArdttNavItem(Routes.HOME, "Главная", Icons.Outlined.Home),
        ArdttNavItem(Routes.PROFILES, "Профили", Icons.Outlined.Person),
        ArdttNavItem(Routes.APPS, "Приложения", Icons.Outlined.Apps),
    )
    Box(Modifier.fillMaxSize()) {
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
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(current) }
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
