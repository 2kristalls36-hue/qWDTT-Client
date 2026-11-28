package com.wdtt.client.lite

/**
 * The description of a release (docs/releases/v<version>.md, as GitHub gives it) made
 * readable in the app: without the markup of headings, bold, code and links. The first
 * line «Обновление qWDTT Client X.» is left out: the version is already said above it.
 */
object ReleaseNotes {

    private val heading = Regex("""^#{1,6}\s+(.*)$""")
    private val bullet = Regex("""^[-*+]\s+(.*)$""")
    private val link = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val strong = Regex("""(\*\*|__)(.+?)\1""")
    private val code = Regex("""`([^`]*)`""")
    private val title = Regex("""^Обновление qWDTT Client\b.*""")

    /** Up to [limit] lines: section names end with «:», list items start with «•». */
    fun brief(notes: String, limit: Int = 6): List<String> {
        val out = mutableListOf<String>()
        for (raw in notes.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || title.matches(line)) continue
            val h = heading.matchEntire(line)
            val b = bullet.matchEntire(line)
            out += when {
                h != null -> inline(h.groupValues[1]).trimEnd(':').trim() + ":"
                b != null -> "• " + inline(b.groupValues[1])
                else -> inline(line)
            }
            if (out.size == limit) break
        }
        // A section name with nothing under it says nothing.
        while (out.isNotEmpty() && out.last().endsWith(":")) out.removeAt(out.lastIndex)
        return out
    }

    internal fun inline(text: String): String =
        text.replace(link, "$1").replace(strong, "$2").replace(code, "$1").trim()
}
