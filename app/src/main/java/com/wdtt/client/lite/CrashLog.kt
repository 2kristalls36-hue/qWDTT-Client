package com.wdtt.client.lite

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves the last crash to a file, so the next launch can show it and the user can copy it. */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        if (isCrashProcess(context)) return
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val header = buildString {
                    appendLine(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                    appendLine("Версия: ${app.packageManager.getPackageInfo(app.packageName, 0).versionName}")
                    appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Поток: ${thread.name}")
                }
                File(app.filesDir, FILE).writeText(header + "\n" + trace)
                // Show the report right away, in its own process that survives this one.
                app.startActivity(
                    android.content.Intent(app, CrashActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            } catch (_: Throwable) {
                // Never let the crash logger itself hide the original crash.
            }
            // Kill this process instead of the system «app stopped» dialog: the report is already open.
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        }
    }

    fun isCrashProcess(context: Context): Boolean = processName(context).endsWith(":crash")

    /**
     * Name of the current process. Application.getProcessName() exists only on Android 9+;
     * on 8.x it is read from /proc, with ActivityManager as a fallback.
     */
    private fun processName(context: Context): String {
        if (android.os.Build.VERSION.SDK_INT >= 28) return android.app.Application.getProcessName()
        runCatching {
            val raw = File("/proc/self/cmdline").readBytes()
            val end = raw.indexOf(0.toByte()).let { if (it < 0) raw.size else it }
            val name = String(raw, 0, end).trim()
            if (name.isNotEmpty()) return name
        }
        val pid = android.os.Process.myPid()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        return am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName.orEmpty()
    }

    fun read(context: Context): String? =
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }
}
