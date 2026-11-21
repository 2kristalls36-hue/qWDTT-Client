package com.wdtt.client.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    @Test fun versions() {
        assertTrue(AppUpdate.isNewer("1.0.0", "1.0.1"))
        assertTrue(AppUpdate.isNewer("1.2.9", "1.2.10"))
        assertTrue(AppUpdate.isNewer("1.9.9", "2.0.0"))
        assertTrue(AppUpdate.isNewer("1.1.0-beta1", "1.1.0"))
        assertTrue(AppUpdate.isNewer("1.0.0", "v1.1"))
        assertFalse(AppUpdate.isNewer("1.0.1", "1.0.0"))
        assertFalse(AppUpdate.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppUpdate.isNewer("1.1.0", "1.1.0-beta1"))
    }

    @Test fun apkForDevice() {
        val names = listOf(
            "qwdtt-client-1.1.0-arm64-v8a.apk",
            "qwdtt-client-1.1.0-armeabi-v7a.apk",
            "qwdtt-client-1.1.0-x86_64.apk",
            "qwdtt-client-1.1.0-universal.apk",
        )
        assertEquals("qwdtt-client-1.1.0-arm64-v8a.apk", AppUpdate.pickApk(names, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("qwdtt-client-1.1.0-armeabi-v7a.apk", AppUpdate.pickApk(names, listOf("armeabi-v7a")))
        assertEquals("qwdtt-client-1.1.0-universal.apk", AppUpdate.pickApk(names, listOf("riscv64")))
    }
}
