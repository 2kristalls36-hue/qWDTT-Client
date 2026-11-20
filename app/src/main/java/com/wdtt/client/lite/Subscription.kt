package com.wdtt.client.lite

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How close the subscription end is; drives chip color and texts. */
enum class SubscriptionTone { Normal, Warn, Critical, Expired, Unknown }

data class SubscriptionView(
    val tone: SubscriptionTone,
    /** Short value for the home chip and the sheet headline: «23 дня», «бессрочно». */
    val short: String,
    /** «до 12 декабря 2026», empty when there is no end date. */
    val untilText: String,
    /** One formal sentence under the progress bar. */
    val hint: String,
    /** 0..1 share of a 30-day period left; null — no bar. */
    val barFraction: Float?,
)

object SubscriptionText {
    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

    fun days(n: Long): String {
        val mod100 = n % 100
        val mod10 = n % 10
        val word = when {
            mod100 in 11..14 -> "дней"
            mod10 == 1L -> "день"
            mod10 in 2..4 -> "дня"
            else -> "дней"
        }
        return "$n $word"
    }

    fun warnSentence(enabled: Boolean, days: Int): String =
        if (enabled) "Приложение предупредит об окончании подписки за ${days(days.toLong())}."
        else "Приложение не будет предупреждать об окончании подписки."

    /** Whole calendar days from [today] to the expiry date (0 — expires today). */
    fun daysLeft(expiresAt: Long, today: LocalDate, zone: ZoneId): Long {
        val end = Instant.ofEpochSecond(expiresAt).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, end)
    }

    fun view(
        info: SubscriptionInfo?,
        warnDays: Int,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SubscriptionView {
        if (info == null) {
            return SubscriptionView(
                SubscriptionTone.Unknown, "нет данных", "",
                "Сведения о подписке появятся после первого подключения.", null,
            )
        }
        if (info.expiresAt <= 0L) {
            return SubscriptionView(SubscriptionTone.Normal, "бессрочно", "", "Подписка бессрочная.", null)
        }
        val until = "до " + Instant.ofEpochSecond(info.expiresAt).atZone(zone).toLocalDate().format(dateFormat)
        if (info.expiresAt * 1000 <= nowMs) {
            return SubscriptionView(SubscriptionTone.Expired, "истекла", "", "Срок подписки истёк.", 0f)
        }
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val left = daysLeft(info.expiresAt, today, zone)
        val fraction = (left.coerceAtLeast(0) / 30f).coerceIn(0.02f, 1f)
        return when {
            left <= 0 -> SubscriptionView(SubscriptionTone.Critical, "сегодня", until, "Срок подписки истекает сегодня.", fraction)
            left == 1L -> SubscriptionView(SubscriptionTone.Critical, days(1), until, "Срок подписки истекает завтра.", fraction)
            left <= warnDays -> SubscriptionView(SubscriptionTone.Warn, days(left), until, "Срок подписки истекает через ${days(left)}.", fraction)
            else -> SubscriptionView(SubscriptionTone.Normal, days(left), until, "Подписка активна.", fraction)
        }
    }
}
