package com.subtitleplayer.prototype

import java.io.StringWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiExportTest {
    private val row = VocabularyExportRow("went", "go", "رفتن", "We went home.", "Episode 1", 2500, 2, 12345, 3)
    private fun export(rows: List<VocabularyExportRow>) = StringWriter().also { writeAnkiVocabulary(rows, it) }.toString()

    @Test fun declaresTwoHtmlFieldsSeparatedByTabs() {
        assertTrue(export(emptyList()).startsWith("#separator:Tab\n#html:true\n#columns:Front\tBack\n"))
    }
    @Test fun frontHasContextWhilePersianAnswerIsOnlyOnBack() {
        val fields = export(listOf(row)).lineSequence().drop(3).first().split('\t')
        assertEquals(2, fields.size)
        assertTrue(fields[0].contains("We went home."))
        assertFalse(fields[0].contains("رفتن"))
        assertTrue(fields[1].contains("رفتن"))
    }
    @Test fun untrustedTextCannotInjectHtmlIntoAnki() {
        val text = "<script>alert(\"x\")</script> & 'test'"
        val result = export(listOf(row.copy(meaning = text)))
        assertFalse(result.contains("<script>"))
        assertTrue(result.contains("&lt;script&gt;"))
        assertTrue(result.contains("&quot;x&quot;"))
        assertTrue(result.contains("&amp;"))
    }
    @Test fun multilineAndTabCharactersStayInOneNoteWithTwoFields() {
        val lines = export(listOf(row.copy(dialogue = "first\r\nsecond\tthird"))).lines()
        assertEquals(5, lines.size)
        assertEquals(1, lines[3].count { it == '\t' })
        assertTrue(lines[3].contains("first<br>second third"))
    }
    @Test fun contextAndSourceDistinguishFrontFieldsAndNoSchedulingIsTransferred() {
        val lines = export(listOf(row, row.copy(sourceName = "Episode 2"))).lines()
        assertFalse(lines[3].substringBefore('\t') == lines[4].substringBefore('\t'))
        assertFalse(lines[3].contains("12345"))
        assertFalse(lines[3].contains("source_uri"))
    }
}
