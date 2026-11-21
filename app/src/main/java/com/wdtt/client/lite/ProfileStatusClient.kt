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
    /** Port of the server HTTP API (the same TCP port as the server's main listener). */
    const val API_PORT = 56000
    /** Local entrance the Go client opens while the RAW tunnel is up. */
    const val TUNNEL_PROXY = "127.0.0.1:9123"

    /** Server host of the running tunnel, set when the client starts. */
    @Volatile var tunnelHost: String = ""

    /** The client reported that its local entrance into the tunnel is open. */
    @Volatile var tunnelReady: Boolean = false

    /**
     * Where to send API requests for [peer]: through the running tunnel when it leads to
     * the same server (works on networks with whitelists), otherwise directly.
     */
    private fun endpoints(peer: String): List<String> {
        // The API listens on the server's main port; a profile link may carry another
        // port (direct or raw), which must not be used for the API.
        val direct = "http://${PeerAddress.httpEndpoint(PeerAddress.host(peer), API_PORT)}"
        val viaTunnel = tunnelReady && com.wdtt.client.TunnelManager.running.value &&
            tunnelHost.isNotBlank() && PeerAddress.host(peer) == tunnelHost
        return if (viaTunnel) listOf("http://$TUNNEL_PROXY", direct) else listOf(direct)
    }

    /** First endpoint that answers the challenge, with its nonce. */
    private fun open(peer: String): Pair<String, String>? {
        for (e in endpoints(peer)) {
            val nonce = challenge(e)
            if (nonce != null) return e to nonce
        }
        return null
    }

    /** API requests go to the connection log, so a failure can be read there. */
    private fun log(text: String) = RawLog.add("[API] $text")
    private fun Throwable.describe() = "${javaClass.simpleName}: ${message ?: "без описания"}"
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
        val (endpoint, nonce) = open(peer) ?: return@withContext Check.Unreachable
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
            val code = conn.responseCode
            log("status → HTTP $code")
            when (code) {
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
        } catch (e: Exception) {
            log("status: ${e.describe()}")
            Check.Unreachable
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun fetch(context: Context, peer: String, password: String): SubscriptionInfo? =
        withContext(Dispatchers.IO) {
            if (peer.isBlank() || password.isBlank()) return@withContext null
            val (endpoint, nonce) = open(peer) ?: return@withContext null
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
                val code = conn.responseCode
                log("status → HTTP $code")
                if (code != 200) return@withContext null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                SubscriptionInfo(
                    expiresAt = json.optLong("expires_at", 0L),
                    maxDevices = json.optInt("max_devices", 1),
                    boundDevices = json.optInt("bound_devices", 0),
                    checkedAt = System.currentTimeMillis(),
                )
            } catch (e: Exception) {
                log("status: ${e.describe()}")
                null
            } finally {
                conn?.disconnect()
            }
        }

    /** Unbinds this device from the profile password, freeing its place for another device. */
    suspend fun unbindThisDevice(context: Context, peer: String, password: String): Boolean = withContext(Dispatchers.IO) {
        if (peer.isBlank() || password.isBlank()) return@withContext false
        val (endpoint, nonce) = open(peer) ?: return@withContext false
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
            val code = conn.responseCode
            log("unbind → HTTP $code")
            code == 200
        } catch (e: Exception) {
            log("unbind: ${e.describe()}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun challenge(endpoint: String): String? {
        log("challenge $endpoint")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$endpoint/api/profile/challenge").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }
            conn.outputStream.use { it.write(ByteArray(0)) }
            val code = conn.responseCode
            log("challenge → HTTP $code")
            if (code != 200) null
            else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .optString("nonce").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            log("challenge: ${e.describe()}")
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
