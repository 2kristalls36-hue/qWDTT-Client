package com.wdtt.client.lite

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wdtt.client.MainActivity
import com.wdtt.client.ProfilesStore
import com.wdtt.client.R
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Twice a day: refreshes the subscription end date of every profile from its server
 * and shows one notification per end date once it falls inside the warning window.
 */
class ExpiryWarningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "expiry-warning"
        private const val CHANNEL_ID = "subscription"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ExpiryWarningWorker>(12, TimeUnit.HOURS).build()
            try {
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } catch (_: IllegalStateException) {
                // WorkManager is not initialized (no startup provider, e.g. in JVM tests).
            }
        }
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val lite = LiteSettings(ctx)
        val profiles = ProfilesStore(ctx).profiles.first()
        val enabled = lite.warnEnabled.first()
        val warnDays = lite.warnDays.first()
        val zone = ZoneId.systemDefault()
        for (profile in profiles) {
            ProfileStatusClient.fetch(ctx, profile.peer, profile.password)?.let {
                lite.saveSubscription(profile.id, it)
            }
            if (!enabled) continue
            val info = lite.subscription(profile.id).first() ?: continue
            if (info.expiresAt <= 0L || info.expiresAt * 1000 <= System.currentTimeMillis()) continue
            val left = SubscriptionText.daysLeft(info.expiresAt, LocalDate.now(zone), zone)
            if (left > warnDays) continue
            if (lite.warnedFor(profile.id) == info.expiresAt) continue
            val text = SubscriptionText.view(info, warnDays).hint
            if (notify(ctx, profile.id, profile.name.ifBlank { "Профиль" }, text)) {
                lite.saveWarnedFor(profile.id, info.expiresAt)
            }
        }
        return Result.success()
    }

    private fun notify(ctx: Context, profileId: String, title: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Срок подписки", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_connected)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(ctx).notify(profileId.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
