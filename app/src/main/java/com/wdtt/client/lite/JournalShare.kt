package com.wdtt.client.lite

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.wdtt.client.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The raw journal as one text file for the system share sheet: a messenger takes it
 * whole, without the length limit of a pasted message.
 */
object JournalShare {
    private const val DIR = "logs"

    fun fileName(now: Date): String =
        "qwdtt-client-log-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now) + ".txt"

    /** Header with what the journal itself does not say, then the lines as they are. */
    fun text(lines: List<String>, now: Date, app: String, device: String): String = buildString {
        appendLine("qWDTT Client $app")
        appendLine(device)
        appendLine("Сохранено: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(now))
        appendLine()
        lines.forEach { appendLine(it) }
    }

    fun share(context: Context, lines: List<String>) {
        val now = Date()
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        // Only the newest file is kept: older ones were already shared.
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName(now))
        val device = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
        file.writeText(text(lines, now, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", device))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Журнал qWDTT Client")
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Журнал подключения")
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
