package com.wdtt.client

/**
 * Counters of one connection session for the connection log: how long the tunnel took to
 * come up, how often and why it was restarted, how many times the app joined the VK call,
 * captchas and VK limits. Lets two versions be compared by their logs, not by feel.
 */
internal class SessionMetrics(private val now: () -> Long = System::currentTimeMillis) {
    private var running = false
    private var startedAt = 0L
    private var readyAt = 0L
    private var ready = false
    private val reconnects = LinkedHashMap<String, Int>()
    private var joins = 0
    private var captchas = 0
    private var vkLimits = 0

    val active: Boolean @Synchronized get() = running

    @Synchronized fun start() {
        running = true
        startedAt = now()
        readyAt = 0L
        ready = false
        reconnects.clear()
        joins = 0
        captchas = 0
        vkLimits = 0
    }

    /** The tunnel carries traffic; the time to it, once per session (null afterwards). */
    @Synchronized fun ready(): Long? {
        if (!running || ready) return null
        ready = true
        readyAt = now()
        return readyAt - startedAt
    }

    @Synchronized fun reconnect(reason: String) {
        if (!running) return
        reconnects[reason] = (reconnects[reason] ?: 0) + 1
    }

    @Synchronized fun join() { if (running) joins++ }
    @Synchronized fun captcha() { if (running) captchas++ }
    @Synchronized fun vkLimit() { if (running) vkLimits++ }

    @Synchronized fun summary(): String {
        val total = reconnects.values.sum()
        val reasons = if (total == 0) "" else reconnects.entries.joinToString(", ", " (", ")") { "${it.key} ${it.value}" }
        val connect = if (!ready) "не подключено" else "подключение за ${seconds(readyAt - startedAt)}"
        return "[ИТОГ] Сессия ${duration(now() - startedAt)}; $connect; переподключений $total$reasons; " +
            "входов в звонок $joins; капч $captchas; ограничений VK $vkLimits"
    }

    @Synchronized fun stop(): String? {
        if (!running) return null
        val s = summary()
        running = false
        return s
    }

    private fun seconds(ms: Long): String = "%.1f с".format(java.util.Locale.ROOT, ms / 1000.0).replace('.', ',')

    private fun duration(ms: Long): String {
        val totalMin = (ms / 60_000L).coerceAtLeast(0)
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "$h ч $m мин" else "$m мин"
    }
}
