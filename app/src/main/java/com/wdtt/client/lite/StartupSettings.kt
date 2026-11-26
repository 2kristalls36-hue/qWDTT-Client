package com.wdtt.client.lite

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wdtt.client.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * The settings the look of the first frame depends on (theme, power level, glass, screen
 * fit), read once when the app starts. The first frame is held back until they are here
 * (see MainActivity), and the screens fall back on them until their own reading of the
 * settings arrives: so the app opens as it was left instead of opening with the defaults
 * and then jumping to the saved values. On a cold start, as the first one after an update,
 * the settings take long enough to read for that jump to be seen.
 */
object StartupSettings {
    var themeMode: String? by mutableStateOf(null)
        private set
    var powerLevel: PowerLevel? by mutableStateOf(null)
        private set
    var liquidGlass: Boolean? by mutableStateOf(null)
        private set
    var glassTransparency: Float? by mutableStateOf(null)
        private set
    var screenFit: Boolean? by mutableStateOf(null)
        private set

    /** Everything is read (or reading failed and the defaults stand). */
    var ready by mutableStateOf(false)
        private set

    suspend fun load(context: Context) {
        if (ready) return
        try {
            val lite = LiteSettings(context)
            themeMode = SettingsStore(context).themeMode.first()
            powerLevel = lite.powerLevel.first()
            liquidGlass = lite.liquidGlass.first()
            glassTransparency = lite.glassTransparency.first()
            screenFit = lite.screenFit.first()
        } finally {
            ready = true
        }
    }
}
