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
        // Without a VK call link the tunnel service stops silently; say so instead —
        // unless the VK account is signed in: then the link is created automatically.
        // Neither the profile nor the settings have a link: with a signed-in VK account a
        // default link is created (then every profile without its own link uses it).
        if (settings.vkHashes.first().isBlank()) {
            if (!createDefaultCallLink(context).isSuccess) return Problem.NoCallLink
            prepareSettings(context, profileId)
        }
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

    /**
     * Creates a VK call through the signed-in account and saves its link into the
     * profile. Fails when there is no VK session or VK refuses.
     */
    suspend fun createCallLink(context: Context, profileId: String): Result<String> {
        val result = newCallHash(context)
        result.onSuccess { hash ->
            RawLog.add("[VK] call link created for the profile")
            saveCallLink(context, profileId, "https://vk.com/call/join/$hash")
        }.onFailure { RawLog.add("[VK] call link not created: ${it.message}") }
        return result
    }

    /**
     * Creates a VK call through the signed-in account and saves it as the default link in
     * the settings (used by profiles without their own link).
     */
    suspend fun createDefaultCallLink(context: Context): Result<String> {
        val result = newCallHash(context)
        result.onSuccess { hash ->
            RawLog.add("[VK] default call link created")
            saveDefaultCallLink(context, hash)
        }.onFailure { RawLog.add("[VK] call link not created: ${it.message}") }
        return result
    }

    /**
     * A new call through the signed-in account. The access token is first asked for over
     * HTTP with the session cookies. VK may answer that with a page that only works in the
     * browser (permission request, a new sign-in flow on another domain): then the same
     * request opens in the VK window, where the session is, and the token is taken from it.
     */
    private suspend fun newCallHash(context: Context): Result<String> {
        val signedIn = withContext(Dispatchers.Main) { com.wdtt.client.vkauth.VkSession.hasSessionCookie() }
        if (!signedIn) return Result.failure(IllegalStateException("Вход в аккаунт VK не выполнен."))
        when (val first = com.wdtt.client.vkauth.VkCallHashGenerator.generateOutcome(context)) {
            is com.wdtt.client.vkauth.CallHashOutcome.Success -> return Result.success(first.hash)
            is com.wdtt.client.vkauth.CallHashOutcome.Failure -> {
                val e = first.error
                RawLog.add("[VK] call link over HTTP failed: ${e.kind} phase=${e.phase} http=${e.httpCode ?: "-"} api=${e.apiCode ?: "-"}")
                if (e.kind != com.wdtt.client.vkauth.CallHashErrorKind.AuthRequired) return Result.failure(IllegalStateException(e.userMessage))
            }
        }
        RawLog.add("[VK] access token: opening the VK window")
        val token = try {
            com.wdtt.client.vkauth.VkLoginActivity.awaitAccessToken(context)
        } catch (t: Throwable) {
            RawLog.add("[VK] access token not received: ${t.message}")
            return Result.failure(IllegalStateException("Доступ не получен."))
        }
        return com.wdtt.client.vkauth.VkCallHashGenerator.generateWithToken(token)
    }

    /** Saves the default call link (or hash) and re-applies the active profile. */
    suspend fun saveDefaultCallLink(context: Context, link: String) {
        val clean = link.split(Regex("[,\\s]+"))
            .map { com.wdtt.client.stripVkUrlStatic(it) }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(",")
        val settings = SettingsStore(context)
        settings.saveGlobalVkHashes(clean)
        val active = settings.currentProfileId.first()
        if (active.isNotBlank() && ProfilesStore(context).getProfileOnce(active) != null) {
            ProfilesStore(context).applyProfile(context, active)
        }
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

    /** Best effort: the server may be unreachable before the tunnel is up. */
    suspend fun refreshSubscription(context: Context, profileId: String) {
        val profile = ProfilesStore(context).getProfileOnce(profileId) ?: return
        ProfileStatusClient.fetch(context, profile.peer, profile.password)?.let {
            LiteSettings(context).saveSubscription(profileId, it)
        }
    }
}
