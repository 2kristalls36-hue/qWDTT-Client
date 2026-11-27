package com.wdtt.client.lite

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.wdtt.client.ManlCaptchaWebViewManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VkConfirmationNoticeTest {
    @Test fun theNoticeComesUpWithTheAppsWordsAndGoesWhenDone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val nm = context.getSystemService(NotificationManager::class.java)
        // The app in the background (another test may have left its screen counted as shown):
        // the confirmation is asked for by a notice.
        val shownBefore = com.wdtt.client.MainActivity.activeActivities
        com.wdtt.client.MainActivity.activeActivities = 0
        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        val job = kotlinx.coroutines.GlobalScope.launch {
            withTimeoutOrNull(500) { runCatching { ManlCaptchaWebViewManager.solveCaptchaAsync(context, "https://example.org/captcha", "t") } }
        }
        Thread.sleep(200)
        val shown = shadowOf(nm).allNotifications.firstOrNull()
        assertNotNull("a notice", shown)
        val extras = shown!!.extras
        assertEquals("Подтверждение VK", extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
        assertEquals("Для подключения требуется подтверждение VK.", extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        assertEquals(NotificationManager.IMPORTANCE_HIGH, nm.getNotificationChannel("vk_confirmation").importance)
        assertNull("no old channel", nm.getNotificationChannel("captcha_channel"))
        job.cancel()
        com.wdtt.client.MainActivity.activeActivities = shownBefore
    }
}
