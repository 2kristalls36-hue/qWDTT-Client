package com.wdtt.client.lite

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.wdtt.client.PeerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Asks the qWDTT server about a profile: expiry date and bound devices
 * (`/api/profile/challenge` + `/api/profile/status`, HMAC proof with the profile password).
 */
object ProfileStatusClient {
    private const val DEFAULT_DTLS_PORT = 56000
    private const val TIMEOUT_MS = 5000

    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    /** Result of asking the server about a profile before it is added or connected. */
    sealed interface Check {
        data class Ok(val info: SubscriptionInfo, val thisDeviceBound: Boolean) : Check
        /** The server answered but did not accept the profile: wrong password or expired. */
        data object Rejected : Check
        data object Unreachable : Check
    }

    suspend fun check(context: Context, peer: String, password: String): Check = withContext(Dispatchers.IO) {
        if (peer.isBlank() || password.isBlank()) return@withContext Check.Rejected
        val endpoint = "http://${PeerAddress.httpEndpoint(peer, DEFAULT_DTLS_PORT)}"
        val nonce = challenge(endpoint) ?: return@withContext Check.Unreachable
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("$endpoint/api/profile/status").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(proofBody("status", password, deviceId(context), nonce).toByteArray(Charsets.UTF_8)) }
            when (conn.responseCode) {
                200 -> {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    Check.Ok(
                        SubscriptionInfo(
                            expiresAt = json.optLong("expires_at", 0L),
                            maxDevices = json.optInt("max_devices", 1),
                            boundDevices = json.optInt("bound_devices", 0),
                            checkedAt = System.currentTimeMillis(),
                        ),
                        thisDeviceBound = json.optBoolean("is_current_bound", false),
                    )
                }
                401, 403 -> Check.Rejected
                else -> Check.Unreachable
            }
        } catch (_: Exception) {
            Check.Unreachable
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun fetch(context: Context, peer: String, password: String): SubscriptionInfo? =
        withContext(Dispatchers.IO) {
            if (peer.isBlank() || password.isBlank()) return@withContext null
            val endpoint = "http://${PeerAddress.httpEndpoint(peer, DEFAULT_DTLS_PORT)}"
            val nonce = challenge(endpoint) ?: return@withContext null
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$endpoint/api/profile/status").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    useCaches = false
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("Accept", "application/json")
                }
                val body = proofBody("status", password, deviceId(context), nonce)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode != 200) return@withContext null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                SubscriptionInfo(
                    expiresAt = json.optLong("expires_at", 0L),
                    maxDevices = json.optInt("max_devices", 1),
                    boundDevices = json.optInt("bound_devices", 0),
                    checkedAt = System.currentTimeMillis(),
                )
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

    /** Unbinds this device from the profile password, freeing its place for another device. */
    suspend fun unbindThisDevice(context: Context, peer: String, password: String): Boolean = withContext(Dispatchers.IO) {
        if (peer.isBlank() || password.isBlank()) return@withContext false
        val endpoint = "http://${PeerAddress.httpEndpoint(peer, DEFAULT_DTLS_PORT)}"
        val nonce = challenge(endpoint) ?: return@withContext false
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("$endpoint/api/profile/unbind").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            val body = proofBody("unbind", password, deviceId(context), nonce)
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun challenge(endpoint: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$endpoint/api/profile/challenge").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }
            conn.outputStream.use { it.write(ByteArray(0)) }
            if (conn.responseCode != 200) null
            else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .optString("nonce").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun proofBody(action: String, password: String, deviceId: String, nonce: String): String {
        val keyId = MessageDigest.getInstance("SHA-256")
            .digest(("WDTT-PROFILE-ID-v1\u0000" + password).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val proof = mac.doFinal("$action\n$deviceId\n$nonce".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "device_id=${URLEncoder.encode(deviceId, "UTF-8")}" +
            "&nonce=${URLEncoder.encode(nonce, "UTF-8")}" +
            "&key_id=$keyId&proof=$proof"
    }
}
