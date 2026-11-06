package com.subtitleplayer.prototype

import java.io.IOException
import java.io.StringWriter
import org.junit.Assert.*
import org.junit.Test

class DictionaryTransferTest {
    @Test fun exportRoundTripPreservesMultilineTabsAndBackslashes() {
        val entries = listOf(PersonalDictionaryEntry("hello", "hello", "سلام\nمثال\tC:\\folder"))
        val text = StringWriter().also { writeDictionary(entries, it) }.toString()
        assertEquals(entries, parseDictionary(text))
    }
    @Test fun importedFormsNormalizeAndLastDuplicateWins() {
        val rows = parseDictionary("\uFEFFword\theadword\tmeaning\r\nWENT\tgo\tاول\r\nwent\tgo\tدوم\r\n")
        assertEquals(listOf(PersonalDictionaryEntry("went", "go", "دوم")), rows)
    }
    @Test fun malformedRowRejectsWholeImportInsteadOfSkippingData() {
        assertThrows(IOException::class.java) { parseDictionary("word\theadword\tmeaning\nhello\thello\tسلام\nbad\tmissing") }
        assertThrows(IOException::class.java) { parseDictionary("word\theadword\tmeaning\nx\tx\tbad\\x") }
        assertThrows(IOException::class.java) { parseDictionary("wrong header") }
    }
    @Test fun emptyAndOversizedImportsAreRejected() {
        assertThrows(IOException::class.java) { parseDictionary("word\theadword\tmeaning\n") }
        assertThrows(IOException::class.java) { parseDictionary("word\theadword\tmeaning\n" + "a\ta\tمعنی\n".repeat(20001)) }
    }
    @Test fun oversizedExportFailsBeforeWritingAnyContent() {
        val writer = StringWriter()
        val rows = List(10000) { PersonalDictionaryEntry("hello", "hello", "م".repeat(1000)) }
        assertThrows(IOException::class.java) { writeDictionary(rows, writer) }
        assertEquals("", writer.toString())
    }
    @Test fun invalidOrExcessiveRowsCannotProduceUnimportableExports() {
        assertThrows(IOException::class.java) {
            writeDictionary(listOf(PersonalDictionaryEntry("BAD", "bad", "معنی")), StringWriter())
        }
        assertThrows(IOException::class.java) {
            writeDictionary(List(20001) { PersonalDictionaryEntry("a", "a", "معنی") }, StringWriter())
        }
    }
    @Test fun importedCustomPhraseIsRecognizedWithLongestMatch() {
        val map = mapOf("piece of cake" to WordMeaning("piece of cake", "بسیار آسان"),
            "piece of" to WordMeaning("piece of", "بخشی از"))
        val text = "A piece of cake!"
        assertEquals("piece of cake", subtitleTerms(text, map, SubtitleLookupMode.Phrases)[1].text)
        assertEquals("بسیار آسان", subtitleTerms(text, map, SubtitleLookupMode.Phrases)[1].meaning?.persian)
        assertEquals(listOf("piece", "of", "cake"), subtitleTerms("piece, of cake", map, SubtitleLookupMode.Phrases).map { it.text })
    }
}
