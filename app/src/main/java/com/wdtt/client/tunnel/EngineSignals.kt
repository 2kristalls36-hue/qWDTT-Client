package com.wdtt.client

/**
 * Signals of the Go engine read from its output, and the watchdog's pacing; kept apart
 * from TunnelManager so they can be tested.
 */
internal object EngineSignals {
    private const val VK_WAIT_PREFIX = "VK_WAIT|"

    /**
     * `VK_WAIT|<seconds>`: the engine waits out a VK limit (flood control, a pause after
     * a captcha) and retries by itself; 0 — the wait is over. Null — not this signal.
     */
    fun vkWaitSeconds(line: String): Int? {
        if (!line.startsWith(VK_WAIT_PREFIX)) return null
        return line.substring(VK_WAIT_PREFIX.length).trim().toIntOrNull()?.coerceAtLeast(0)
    }

    /**
     * How long the engine may run with no live streams before the watchdog restarts it:
     * 90 s, then twice as long after each restart in a row, at most 10 min. Every restart
     * joins the call anew; restarting every 90 s on a broken network only invites VK limits.
     */
    fun zombieRestartDelayMs(restartsInARow: Int): Long =
        (90_000L shl restartsInARow.coerceIn(0, 3)).coerceAtMost(600_000L)
}
