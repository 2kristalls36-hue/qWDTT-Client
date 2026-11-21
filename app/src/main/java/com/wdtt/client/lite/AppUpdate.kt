package com.wdtt.client.lite

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates from GitHub releases of this repository, as in the original qWDTT:
 * a `v1.2.3` tag builds signed APKs per ABI plus SHA256SUMS.txt, the app reads the
 * latest release, downloads the APK for this device, checks the sum and opens the installer.
 * Android itself refuses an APK signed with another key, so only our builds install.
 */
object AppUpdate {
    const val REPO = "2kristalls36-hue/qWDTT-Client"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases"

    data class Release(
        val version: String,
        val notes: String,
        val apkName: String,
        val apkUrl: String,
        val sumsUrl: String?,
        val pageUrl: String,
    )

    sealed interface Check {
        data class Available(val release: Release) : Check
        data object UpToDate : Check
        data object Failed : Check
    }

    sealed interface Download {
        data class Progress(val fraction: Float) : Download
        data class Done(val file: File) : Download
        data class Error(val message: String) : Download
    }

    /** Newer release found by the last check; the home screen marks the gear with a dot. */
    val available = kotlinx.coroutines.flow.MutableStateFlow<Release?>(null)

    @Volatile private var lastAutoCheckMs = 0L

    /** Background check for the gear dot: at most once per 6 hours per app run. */
    suspend fun checkIfDue(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastAutoCheckMs < 6 * 3600_000L) return
        lastAutoCheckMs = now
        check(context)
    }

    fun installedVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    suspend fun check(context: Context): Check = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(get(LATEST_URL))
            val release = parse(json) ?: return@withContext Check.Failed
            RawLog.add("[UPDATE] latest ${release.version}, installed ${installedVersion(context)}")
            if (isNewer(installedVersion(context), release.version)) {
                available.value = release
                Check.Available(release)
            } else {
                available.value = null
                Check.UpToDate
            }
        } catch (e: Exception) {
            RawLog.add("[UPDATE] check failed: ${e.javaClass.simpleName}: ${e.message}")
            Check.Failed
        }
    }

    internal fun parse(json: JSONObject): Release? {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val version = json.optString("tag_name").removePrefix("v").ifBlank { return null }
        val assets = json.optJSONArray("assets") ?: return null
        val apks = mutableListOf<Pair<String, String>>()
        var sums: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (name.endsWith(".apk")) apks += name to url
            if (name == "SHA256SUMS.txt") sums = url
        }
        val apk = pickApk(apks.map { it.first }, Build.SUPPORTED_ABIS.toList())
            ?.let { n -> apks.first { it.first == n } } ?: return null
        return Release(
            version = version,
            notes = json.optString("body").trim(),
            apkName = apk.first,
            apkUrl = apk.second,
            sumsUrl = sums,
            pageUrl = json.optString("html_url").ifBlank { RELEASES_PAGE },
        )
    }

    /** The APK built for the device's preferred ABI, else the universal one. */
    internal fun pickApk(names: List<String>, deviceAbis: List<String>): String? {
        for (abi in deviceAbis) names.firstOrNull { it.contains("-$abi.") || it.contains("-$abi-") }?.let { return it }
        return names.firstOrNull { it.contains("universal") }
    }

    /** Semantic version compare: 1.2.10 > 1.2.9; a release beats its own -beta. */
    internal fun isNewer(local: String, remote: String): Boolean {
        fun parts(v: String): Pair<List<Int>, String?> {
            val core = v.substringBefore('-')
            val pre = v.substringAfter('-', "").ifBlank { null }
            return core.split('.').map { it.toIntOrNull() ?: 0 } to pre
        }
        val (l, lp) = parts(local.trim().removePrefix("v"))
        val (r, rp) = parts(remote.trim().removePrefix("v"))
        for (i in 0 until maxOf(l.size, r.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = r.getOrElse(i) { 0 }
            if (a != b) return b > a
        }
        return lp != null && rp == null
    }

    fun download(context: Context, release: Release): Flow<Download> = flow {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, release.apkName)
        val conn = open(release.apkUrl)
        try {
            val total = conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmit = 0f
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val f = done.toFloat() / total
                            if (f - lastEmit >= 0.01f) { lastEmit = f; emit(Download.Progress(f)) }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            val expected = release.sumsUrl?.let { url ->
                get(url).lines().firstNotNullOfOrNull { line ->
                    val parts = line.trim().split(Regex("\\s+"), limit = 2)
                    if (parts.size == 2 && parts[1].trimStart('*', '.', '/') == release.apkName) parts[0].lowercase() else null
                }
            }
            if (expected != null && expected != actual) {
                file.delete()
                RawLog.add("[UPDATE] checksum mismatch: $actual != $expected")
                emit(Download.Error("Файл обновления повреждён."))
                return@flow
            }
            emit(Download.Done(file))
        } catch (e: Exception) {
            file.delete()
            RawLog.add("[UPDATE] download failed: ${e.javaClass.simpleName}: ${e.message}")
            emit(Download.Error("Не удалось скачать обновление."))
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    /** False until the user allows this app to install apps (Android 8+). */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "qWDTT-Client")
        }

    private fun get(url: String): String {
        val conn = open(url)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
