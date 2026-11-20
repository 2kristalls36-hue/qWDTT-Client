package com.wdtt.client.lite

import android.content.Context
import android.net.Uri
import com.wdtt.client.ConnectionProfile
import com.wdtt.client.PeerAddress
import com.wdtt.client.SubscriptionImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/** Profiles read from a link, QR code, file or clipboard, in the same formats as qWDTT. */
data class ParsedImport(
    val profiles: List<ConnectionProfile>,
    val suggestedName: String? = null,
)

object ProfileImport {
    /** qwdtt:// link, legacy wdtt://, JSON object/array, or a qWDTT subscription payload. */
    fun parse(rawText: String): ParsedImport? {
        val parsed = SubscriptionImport.parsePayload(rawText)
        if (parsed != null && parsed.profiles.isNotEmpty()) {
            return ParsedImport(parsed.profiles, parsed.subscriptionName)
        }
        val single = parseQrConfig(rawText) ?: return null
        return ParsedImport(listOf(single), single.name)
    }

    fun looksLikeSubscriptionUrl(text: String): Boolean {
        val t = text.trim()
        return (t.startsWith("http://", true) || t.startsWith("https://", true)) && !t.contains('\n')
    }

    suspend fun readUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        if (uri.scheme == "qwdtt" || uri.scheme == "wdtt") return@withContext uri.toString()
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) {
            null
        }
    }
}

// Copied from qWDTT ProfilesTab.kt: single-profile link formats.
internal fun parseQrConfig(rawText: String): ConnectionProfile? {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return null

    // 0. Try legacy original WDTT scheme
    if (trimmed.startsWith("wdtt://")) {
        try {
            // wdtt://<server_ip>:<dtls_port>:<wg_port>:<local_port>:<password>:<vk_hash>
            val parts = trimmed.removePrefix("wdtt://").split(":")
            if (parts.size >= 6) {
                val ip = parts[0]
                val dtlsPort = parts[1]
                val localPort = parts[3].toIntOrNull() ?: 9000
                val pass = parts[4]
                val hash = parts.drop(5).joinToString(":")
                return ConnectionProfile(
                    id = UUID.randomUUID().toString(),
                    name = "WDTT $ip",
                    peer = "$ip:$dtlsPort",
                    vkHashes = hash,
                    workersPerHash = 9,
                    listenPort = localPort,
                    password = pass
                )
            }
        } catch (e: Exception) {
            // continue parsing if failed
        }
    }

    // 1. Try URL scheme
    if (trimmed.startsWith("qwdtt://config") || trimmed.startsWith("qwdtt:config")) {
        try {
            val uri = android.net.Uri.parse(trimmed.replace("qwdtt:config", "qwdtt://config"))
            val name = uri.getQueryParameter("name") ?: "QR Профиль"
            val peerRaw = uri.getQueryParameter("peer") ?: return null
            val dtlsPortParam = uri.getQueryParameter("dtls_port") ?: uri.getQueryParameter("server_port")
            val peer = if (dtlsPortParam != null) {
                PeerAddress.ensurePort(peerRaw, dtlsPortParam.toIntOrNull()?.coerceIn(1, 65535) ?: 56000)
            } else {
                peerRaw
            }
            val hashes = uri.getQueryParameter("hashes") ?: ""
            val workers = uri.getQueryParameter("workers")?.toIntOrNull() ?: 18
            val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 9000
            val pass = uri.getQueryParameter("pass") ?: ""
            return ConnectionProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                peer = peer,
                vkHashes = hashes,
                workersPerHash = workers,
                listenPort = port,
                password = pass,
                diagKey = uri.getQueryParameter("dk").orEmpty().trim()
            )
        } catch (e: Exception) {
            // fallback
        }
    }

    // 2. Try JSON (raw or base64)
    var jsonStr = trimmed
    if (!trimmed.startsWith("{")) {
        try {
            val decodedBytes = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
            jsonStr = String(decodedBytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            // not base64
        }
    }

    if (jsonStr.startsWith("{")) {
        try {
            val jsonObj = JSONObject(jsonStr)
            val name = jsonObj.optString("name", "QR Профиль")
            val peer = jsonObj.getString("peer")
            val hashes = jsonObj.optString("hashes", jsonObj.optString("vkHashes", ""))
            val workers = jsonObj.optInt("workers", jsonObj.optInt("workersPerHash", 18))
            val port = jsonObj.optInt("port", jsonObj.optInt("listenPort", 9000))
            val pass = jsonObj.optString("password", jsonObj.optString("pass", ""))
            return ConnectionProfile(
                id = UUID.randomUUID().toString(),
                name = name,
                peer = peer,
                vkHashes = hashes,
                workersPerHash = workers,
                listenPort = port,
                password = pass,
                diagKey = jsonObj.optString("diagKey", jsonObj.optString("dk", "")).trim()
            )
        } catch (e: Exception) {
            // invalid json
        }
    }

    return null
}
