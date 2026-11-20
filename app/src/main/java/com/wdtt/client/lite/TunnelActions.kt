package com.wdtt.client.lite

import android.content.Context
import com.wdtt.client.MainActivity
import com.wdtt.client.ProfilesStore
import com.wdtt.client.SettingsStore
import com.wdtt.client.TunnelControl
import com.wdtt.client.TunnelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Connect / disconnect for the home screen. Lite always runs the RAW tunnel. */
object TunnelActions {

    fun connect(activity: MainActivity, onNoProfile: () -> Unit) {
        val appContext = activity.applicationContext
        TunnelManager.scope.launch {
            val settings = SettingsStore(appContext)
            val profileId = settings.currentProfileId.first()
            if (profileId.isBlank() || ProfilesStore(appContext).getProfileOnce(profileId) == null) {
                withContext(Dispatchers.Main) { onNoProfile() }
                return@launch
            }
            prepareSettings(appContext, profileId)
            withContext(Dispatchers.Main) {
                TunnelManager.beginConnecting()
                activity.prepareVpnThen { TunnelControl.startFromSavedSettings(appContext) }
            }
            refreshSubscription(appContext, profileId)
        }
    }

    fun disconnect(context: Context) = TunnelControl.stop(context.applicationContext)

    /** Copies the profile into the engine settings with the chosen power and RAW mode. */
    private suspend fun prepareSettings(context: Context, profileId: String) {
        val settings = SettingsStore(context)
        ProfilesStore(context).applyProfile(context, profileId)
        settings.saveConnectionMode(SettingsStore.CONNECTION_MODE_RAWTUN)
        settings.saveWorkersPerHash(LiteSettings(context).powerLevel.first().workers)
    }

    /** Best effort: the server may be unreachable before the tunnel is up. */
    suspend fun refreshSubscription(context: Context, profileId: String) {
        val profile = ProfilesStore(context).getProfileOnce(profileId) ?: return
        ProfileStatusClient.fetch(context, profile.peer, profile.password)?.let {
            LiteSettings(context).saveSubscription(profileId, it)
        }
    }
}
