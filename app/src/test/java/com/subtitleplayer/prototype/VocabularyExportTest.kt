package com.subtitleplayer.prototype

import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyExportTest {
    private val row = VocabularyExportRow("went", "go", "رفتن", "We went home.", "Episode 1", 2500, 2, 123456, 3)
    private fun export(rows: List<VocabularyExportRow>) = StringWriter().also { writeVocabularyCsv(rows, it) }.toString()

    @Test
    fun writesPersianAndExplicitReviewAndPositionFields() {
        val result = export(listOf(row))
        assertTrue(result.startsWith("\uFEFFword,headword,meaning,dialogue,source_name,position_ms,review_level,review_due_at_epoch_ms,review_count\r\n"))
        assertTrue(result.endsWith("\"went\",\"go\",\"رفتن\",\"We went home.\",\"Episode 1\",2500,2,123456,3\r\n"))
    }

    @Test
    fun preservesCommasQuotesAndMultilineDialogueInsideQuotedCells() {
        val result = export(listOf(row.copy(dialogue = "She said, \"go\".\nNext line.")))
        assertTrue(result.contains("\"She said, \"\"go\"\".\nNext line.\""))
    }

    @Test
    fun protectsSpreadsheetFormulaCellsEvenAfterLeadingWhitespace() {
        listOf("=1+1", " +SUM(A1)", "-2+3", "@cmd", "\tvalue", "\rvalue", "\nvalue").forEach { text ->
            assertTrue(export(listOf(row.copy(meaning = text))).contains("\"'" + text + "\""))
        }
    }

    @Test
    fun exportsOnlyRequestedRowsAndNoMediaAddresses() {
        val result = export(listOf(row))
        assertFalse(result.contains("source_uri"))
        assertFalse(result.contains("content://"))
        assertEquals(1, Regex("\"went\"").findAll(result).count())
    }

    @Test
    fun emptyExportStillHasAHeaderAndDoesNotModifyInput() {
        val result = export(emptyList())
        assertEquals(1, result.count { it == '\n' })
        val rows = listOf(row, row.copy(word = "came"))
        val original = rows.toList()
        export(rows)
        assertEquals(original, rows)
    }
}
