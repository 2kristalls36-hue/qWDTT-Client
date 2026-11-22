package com.wdtt.client.vkauth

import android.util.Log
import com.wdtt.client.lite.RawLog

/** ARDTT's log calls in the VK login code, routed to logcat and the raw connection log. */
internal object AppLog {
    fun i(tag: String, msg: String) { Log.i(tag, msg); RawLog.add("[VK] $msg") }
    fun w(tag: String, msg: String) { Log.w(tag, msg); RawLog.add("[VK] $msg") }
    fun e(tag: String, msg: String) { Log.e(tag, msg); RawLog.add("[VK] $msg") }
}
