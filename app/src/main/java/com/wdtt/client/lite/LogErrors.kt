package com.wdtt.client.lite

/**
 * Finds the error lines of the connection journal (see [RawLog]) for its «Ошибки» view:
 * lines of the tunnel client and of the app that report a failure, in Russian or in
 * English, whatever their tag. The start and stop markers next to them are kept, so it is
 * seen which run of the client each error belongs to and how it ended.
 */
object LogErrors {
    private val words = listOf(
        "ошибк", "не удал", "не выполн", "фатальн", "паник", "отклон", "недоступ",
        "передан другому приложению",
        "error", "fail", "refused", "exception", "fatal", "panic", "denied", "unreachable",
        "timed out", "reset by peer", "broken pipe", "mismatch", "not created", "not received",
    )
    private val eof = Regex("\\bEOF\\b")

    /** A start, stop or exit marker of the journal. */
    fun isMarker(line: String): Boolean = line.startsWith("=====")

    fun isError(line: String): Boolean {
        if (isMarker(line)) return false
        val l = line.lowercase()
        return words.any { it in l } || eof.containsMatchIn(line)
    }

    /**
     * The error lines, in order, with each marker that stands right before or right after
     * lines among which there is an error.
     */
    fun only(lines: List<String>): List<String> {
        val error = BooleanArray(lines.size) { isError(lines[it]) }
        // Whether the stretch between two markers that holds this line has an error.
        val stretch = BooleanArray(lines.size)
        var start = 0
        while (start < lines.size) {
            var end = start
            while (end < lines.size && !isMarker(lines[end])) end++
            val has = (start until end).any { error[it] }
            for (i in start until end) stretch[i] = has
            start = end + 1
        }
        return lines.filterIndexed { i, line ->
            if (isMarker(line)) {
                (i > 0 && !isMarker(lines[i - 1]) && stretch[i - 1]) ||
                    (i + 1 < lines.size && !isMarker(lines[i + 1]) && stretch[i + 1])
            } else {
                error[i]
            }
        }
    }
}
