package com.wdtt.client.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseNotesTest {
    @Test fun markupOfOldNotesIsGone() {
        val notes = """
            Обновление qWDTT Client 1.1.0.

            ## Новое

            - **Пауза в доверенных сетях Wi‑Fi.** В доверенной сети подключение приостанавливается.
            - **Экспорт и импорт списков обхода** в формате qWDTT.
            - Файл `arm64-v8a`, исходный код [qWDTT](https://github.com/SpaceNeuroX/proxy-turn-vk-android).
        """.trimIndent()
        assertEquals(
            listOf(
                "Новое:",
                "• Пауза в доверенных сетях Wi‑Fi. В доверенной сети подключение приостанавливается.",
                "• Экспорт и импорт списков обхода в формате qWDTT.",
                "• Файл arm64-v8a, исходный код qWDTT.",
            ),
            ReleaseNotes.brief(notes),
        )
    }

    @Test fun plainNotesKeepTheirSections() {
        val notes = "Обновление qWDTT Client 1.2.1.\n\nИсправления:\n- Первое.\n- Второе.\n"
        assertEquals(listOf("Исправления:", "• Первое.", "• Второе."), ReleaseNotes.brief(notes))
    }

    @Test fun limitDoesNotEndOnASectionName() {
        val notes = "Новое:\n- А.\n- Б.\nИсправления:\n- В."
        assertEquals(listOf("Новое:", "• А.", "• Б."), ReleaseNotes.brief(notes, limit = 4))
    }

    private fun descriptions(): List<File> {
        val dir = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/releases") }
            .first { it.isDirectory }
        return dir.listFiles { f -> f.name.endsWith(".md") }!!.sorted()
    }

    /** 1.2.0 and older show the first six lines as they are (CLAUDE.md, «Версии и релизы»). */
    @Test fun olderAppsShowNoMarkupFrom120On() {
        val old = setOf("v1.0.0.md", "v1.1.0.md")
        for (f in descriptions().filter { it.name !in old }) {
            f.readLines().filter { it.isNotBlank() }.take(6).forEach { line ->
                assertTrue("${f.name}: $line", !line.trimStart().startsWith("#") && "**" !in line && "`" !in line)
            }
        }
    }

    @Test fun everyPublishedDescriptionReadsWithoutMarkup() {
        val files = descriptions()
        assertTrue(files.isNotEmpty())
        for (f in files) {
            val brief = ReleaseNotes.brief(f.readText())
            assertTrue(f.name, brief.isNotEmpty())
            for (line in brief) {
                assertTrue("${f.name}: $line", listOf("#", "**", "`", "](").none { it in line })
            }
        }
    }
}
