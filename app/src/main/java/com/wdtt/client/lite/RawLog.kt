package com.wdtt.client.lite

import com.wdtt.client.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unprocessed tunnel client output: every line exactly as the Go client printed it,
 * plus start and exit markers. Kept in memory, last [CAPACITY] lines.
 */
object RawLog {
    private const val CAPACITY = 3000
    private val lines = ArrayDeque<String>(CAPACITY)
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Changes on every append, so the screen can refresh. */
    val version = MutableStateFlow(0L)

    fun add(line: String) = synchronized(lines) {
        if (lines.size >= CAPACITY) lines.removeFirst()
        lines.addLast(line)
        if (BuildConfig.LOGCAT_MIRROR) android.util.Log.i("qWDTT", line)
        version.value = version.value + 1
    }

    fun marker(text: String) = add("===== ${time.format(Date())} $text =====")

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun clear() = synchronized(lines) {
        lines.clear()
        version.value = version.value + 1
    }
}
