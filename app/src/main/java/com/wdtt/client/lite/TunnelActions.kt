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

    enum class Problem { NoProfile, NoCallLink }

    fun problemText(problem: Problem): String = when (problem) {
        Problem.NoProfile -> "Профиль не выбран."
        Problem.NoCallLink -> "Для подключения нужна ссылка на звонок VK."
    }

    /** Applies the active profile, RAW mode and power. Returns why it cannot connect, or null. */
    suspend fun prepareActive(context: Context): Problem? {
        val settings = SettingsStore(context)
        val profileId = settings.currentProfileId.first()
        if (profileId.isBlank() || ProfilesStore(context).getProfileOnce(profileId) == null) return Problem.NoProfile
        prepareSettings(context, profileId)
        // Without a VK call link the tunnel service stops silently; say so instead.
        if (settings.vkHashes.first().isBlank()) return Problem.NoCallLink
        return null
    }

    fun connect(activity: MainActivity, onProblem: (Problem) -> Unit) {
        val appContext = activity.applicationContext
        TunnelManager.scope.launch {
            val problem = prepareActive(appContext)
            if (problem != null) {
                withContext(Dispatchers.Main) { onProblem(problem) }
                return@launch
            }
            withContext(Dispatchers.Main) {
                TunnelManager.beginConnecting()
                activity.prepareVpnThen { TunnelControl.startFromSavedSettings(appContext) }
            }
            val profileId = SettingsStore(appContext).currentProfileId.first()
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

    /** Saves the VK call link (or hash) into the profile and re-applies it if active. */
    suspend fun saveCallLink(context: Context, profileId: String, link: String) {
        val store = ProfilesStore(context)
        val profile = store.getProfileOnce(profileId) ?: return
        val clean = link.split(Regex("[,\\s]+"))
            .map { com.wdtt.client.stripVkUrlStatic(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")
        store.saveProfile(profile.copy(vkHashes = clean, useGlobalHashes = clean.isBlank()))
        if (SettingsStore(context).currentProfileId.first() == profileId) store.applyProfile(context, profileId)
    }

    /** Saves the connection password of a profile and re-applies it if active. */
    suspend fun savePassword(context: Context, profileId: String, password: String) {
        val store = ProfilesStore(context)
        val profile = store.getProfileOnce(profileId) ?: return
        store.saveProfile(profile.copy(password = password.trim()))
        if (SettingsStore(context).currentProfileId.first() == profileId) store.applyProfile(context, profileId)
    }

    /** Best effort: the server may be unreachable before the tunnel is up. */
    suspend fun refreshSubscription(context: Context, profileId: String) {
        val profile = ProfilesStore(context).getProfileOnce(profileId) ?: return
        ProfileStatusClient.fetch(context, profile.peer, profile.password)?.let {
            LiteSettings(context).saveSubscription(profileId, it)
        }
    }
}
