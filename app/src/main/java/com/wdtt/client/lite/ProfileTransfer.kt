package com.wdtt.client.lite

import android.content.Context
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.PeerAddress
import com.wdtt.client.ProfilesStore
import com.wdtt.client.SettingsStore
import kotlinx.coroutines.flow.first
import java.net.URLEncoder

/** Moving a profile to another device: a qWDTT-compatible link, then releasing this device. */
object ProfileTransfer {
    private const val DEFAULT_DTLS_PORT = 56000

    /** Same format as qWDTT export, so the link opens in qWDTT and qWDTT Client. Carries the VK call link. */
    suspend fun link(context: Context, profile: ConnectionProfile): String {
        val hashes = profile.vkHashes.ifBlank { SettingsStore(context).globalVkHashes.first() }
        fun enc(v: String) = URLEncoder.encode(v, "UTF-8")
        val peer = PeerAddress.ensurePort(profile.peer, DEFAULT_DTLS_PORT)
        val dk = if (profile.diagKey.isNotBlank()) "&dk=" + enc(profile.diagKey) else ""
        return "qwdtt://config?name=${enc(profile.name)}&peer=${enc(peer)}&hashes=${enc(hashes)}" +
            "&workers=${profile.workersPerHash}&port=${profile.listenPort}&pass=${enc(profile.password)}$dk"
    }

    /**
     * Finishes the move: the server forgets this device for the profile and the profile
     * is removed here. Returns false when the server could not be reached; then nothing changes.
     */
    suspend fun finish(context: Context, profile: ConnectionProfile): Boolean {
        if (!ProfileStatusClient.unbindThisDevice(context, profile.peer, profile.password)) return false
        val settings = SettingsStore(context)
        val store = ProfilesStore(context)
        store.deleteProfile(profile.id)
        if (settings.currentProfileId.first() == profile.id) {
            settings.saveCurrentProfile("", "")
            store.profiles.first().firstOrNull()?.let { store.applyProfile(context, it.id) }
        }
        return true
    }
}
