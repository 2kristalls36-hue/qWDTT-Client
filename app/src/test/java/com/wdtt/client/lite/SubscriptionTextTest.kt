package com.wdtt.client.lite

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SubscriptionTextTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val now = LocalDateTime.of(2026, 11, 20, 10, 0).atZone(zone).toInstant().toEpochMilli()

    private fun endIn(days: Long, hour: Int = 12): Long =
        LocalDateTime.of(2026, 11, 20, hour, 0).plusDays(days).atZone(zone).toEpochSecond()

    private fun info(expiresAt: Long) = SubscriptionInfo(expiresAt, 3, 1, now)

    @Test fun pluralForms() {
        assertEquals("1 день", SubscriptionText.days(1))
        assertEquals("3 дня", SubscriptionText.days(3))
        assertEquals("7 дней", SubscriptionText.days(7))
        assertEquals("11 дней", SubscriptionText.days(11))
        assertEquals("21 день", SubscriptionText.days(21))
        assertEquals("23 дня", SubscriptionText.days(23))
    }

    @Test fun farAwayIsNormal() {
        val v = SubscriptionText.view(info(endIn(23)), 3, now, zone)
        assertEquals(SubscriptionTone.Normal, v.tone)
        assertEquals("23 дня", v.short)
        assertEquals("до 13 декабря 2026", v.untilText)
        assertEquals("Подписка активна.", v.hint)
    }

    @Test fun insideWarningWindow() {
        val v = SubscriptionText.view(info(endIn(3)), 3, now, zone)
        assertEquals(SubscriptionTone.Warn, v.tone)
        assertEquals("Срок подписки истекает через 3 дня.", v.hint)
    }

    @Test fun tomorrowAndToday() {
        assertEquals("Срок подписки истекает завтра.", SubscriptionText.view(info(endIn(1)), 3, now, zone).hint)
        assertEquals("Срок подписки истекает сегодня.", SubscriptionText.view(info(endIn(0, 22)), 3, now, zone).hint)
    }

    @Test fun expiredAndUnlimited() {
        assertEquals(SubscriptionTone.Expired, SubscriptionText.view(info(endIn(0, 8)), 3, now, zone).tone)
        assertEquals("Подписка бессрочная.", SubscriptionText.view(info(0), 3, now, zone).hint)
    }

    @Test fun warnSentence() {
        assertEquals("Приложение предупредит об окончании подписки за 3 дня.", SubscriptionText.warnSentence(true, 3))
        assertEquals("Приложение предупредит об окончании подписки за 1 день.", SubscriptionText.warnSentence(true, 1))
        assertEquals("Приложение не будет предупреждать об окончании подписки.", SubscriptionText.warnSentence(false, 3))
    }
}
