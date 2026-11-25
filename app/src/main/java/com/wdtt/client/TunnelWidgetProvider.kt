package com.wdtt.client

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.view.View
import android.widget.RemoteViews
import com.wdtt.client.lite.LiteSettings
import com.wdtt.client.lite.PowerLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home screen widget in the app's style: a round button colored by power level
 * (neon rim at the maximum), the active profile, status with ping, and the level.
 * Tapping the button toggles the tunnel, tapping the text opens the app.
 */
class TunnelWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_FESTIVE) {
            refresh(context)
            return
        }
        if (intent.action != ACTION_TOGGLE) return
        if (TunnelManager.sessionActive()) {
            TunnelControl.stop(context)
            return
        }
        val needsVpnPermission = runCatching { VpnService.prepare(context) != null }.getOrDefault(true)
        if (needsVpnPermission) {
            // The system consent dialog needs an activity; it starts the tunnel when granted.
            runCatching {
                PendingIntent.getActivity(
                    context, 201,
                    Intent(context, VpnPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ).send()
            }
        } else {
            TunnelControl.startFromSavedSettings(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "net.qwdttclient.app.ACTION_TOGGLE"
        /** Midnight when the New Year days begin or end: the widget redraws itself. */
        const val ACTION_FESTIVE = "net.qwdttclient.app.ACTION_FESTIVE"

        /** Kept for the tunnel service, which calls it on every state or stats change. */
        @Suppress("UNUSED_PARAMETER")
        fun updateWidgetState(context: Context, running: Boolean, statsText: String?) = refresh(context)

        /** Redraws every placed widget from the current tunnel state and settings. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, TunnelWidgetProvider::class.java))
            if (ids.isEmpty()) return
            TunnelManager.scope.launch {
                val level = LiteSettings(app).powerLevel.first()
                val profileName = SettingsStore(app).currentProfileName.first()
                val views = render(app, level, profileName)
                ids.forEach { manager.updateAppWidget(it, views) }
                scheduleFestiveRefresh(app)
            }
        }

        /**
         * One inexact alarm at the next midnight when the New Year days begin or end, so
         * the widget changes even if the app is not opened. It does not wake the phone.
         */
        private fun scheduleFestiveRefresh(context: Context) {
            val now = java.time.LocalDateTime.now()
            val at = com.wdtt.client.ui.festive.FestiveCalendar.nextChange(now)
            val ms = at.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val pi = PendingIntent.getBroadcast(
                context, 2,
                Intent(context, TunnelWidgetProvider::class.java).setAction(ACTION_FESTIVE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarms = context.getSystemService(android.app.AlarmManager::class.java) ?: return
            runCatching { alarms.set(android.app.AlarmManager.RTC, ms + 60_000L, pi) }
        }

        private fun render(context: Context, level: PowerLevel, profileName: String): RemoteViews {
            val running = TunnelManager.running.value
            val paused = TunnelManager.trustedPause.value
            val connecting = TunnelManager.isConnecting.value || TunnelManager.isReconnecting.value
            val on = running && !connecting
            val ping = if (on) TunnelManager.pingMs.value else 0L
            val views = RemoteViews(context.packageName, R.layout.tunnel_widget)

            val (bg, levelColor) = when (level) {
                PowerLevel.Stable -> (if (on) R.drawable.widget_btn_on_stable else R.drawable.widget_btn_off_stable) to R.color.widget_stable
                PowerLevel.Balanced -> (if (on) R.drawable.widget_btn_on_balanced else R.drawable.widget_btn_off_balanced) to R.color.widget_balanced
                PowerLevel.Maximum -> (if (on) R.drawable.widget_btn_on_max else R.drawable.widget_btn_off_max) to R.color.widget_max_text
            }
            val iconColor = when {
                level == PowerLevel.Maximum -> context.getColor(if (on) R.color.widget_night_text else R.color.widget_night_faint)
                on -> context.getColor(R.color.widget_on_level)
                else -> context.getColor(levelColor)
            }
            views.setImageViewResource(R.id.widget_button_bg, bg)
            views.setInt(R.id.widget_button_icon, "setColorFilter", iconColor)
            views.setContentDescription(R.id.widget_button_icon, if (running || connecting || paused != null) "Отключиться" else "Подключиться")

            views.setTextViewText(R.id.widget_title, profileName.ifBlank { "qWDTT Client" })
            views.setTextViewText(
                R.id.widget_status,
                when {
                    paused != null -> "Пауза: доверенная сеть"
                    connecting -> "Подключение…"
                    on && ping > 0 -> "Подключено · $ping мс"
                    on -> "Подключено"
                    else -> "Не подключено"
                },
            )
            val pingColor = when {
                ping <= 0 -> null
                ping < 100 -> Color.parseColor("#22C55E")
                ping < 200 -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#EF4444")
            }
            if (pingColor != null) {
                views.setViewVisibility(R.id.widget_ping_dot, View.VISIBLE)
                views.setInt(R.id.widget_ping_dot, "setColorFilter", pingColor)
            } else {
                views.setViewVisibility(R.id.widget_ping_dot, View.GONE)
            }
            views.setTextViewText(R.id.widget_level, level.title)
            val festive = com.wdtt.client.BuildConfig.FESTIVE_PREVIEW ||
                com.wdtt.client.ui.festive.FestiveCalendar.isActive(java.time.LocalDate.now())
            views.setViewVisibility(R.id.widget_festive, if (festive) View.VISIBLE else View.GONE)
            views.setTextColor(R.id.widget_level, context.getColor(levelColor))

            val toggle = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, TunnelWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_toggle, toggle)
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_info, open)
            return views
        }
    }
}
