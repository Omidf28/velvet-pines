package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class DictionaryResolutionTest {
    @Test fun personalDefinitionOverridesCommonWord() {
        val custom = WordMeaning("the", "تعریف شخصی")
        assertEquals(custom, immediateDictionaryMeaning("the", mapOf("the" to custom))?.meaning)
    }
    @Test fun exactEntryWinsOverInflectionGuess() {
        val calls = mutableListOf<String>()
        val result = indexedDictionaryMeaning("news") {
            calls.add(it)
            WordMeaning(it, if (it == "news") "اخبار" else "جدید")
        }
        assertEquals("اخبار", result.meaning?.persian)
        assertEquals(listOf("news"), calls)
    }
    @Test fun inflectionMustHaveExistingRootAndIsLabeled() {
        val result = indexedDictionaryMeaning("running") { if (it == "run") WordMeaning(it, "دویدن") else null }
        assertEquals("running", result.meaning?.headword)
        assertTrue(result.meaning!!.persian.contains("run: دویدن"))
        assertEquals("Possible base form · Offline", result.source)
        assertNull(indexedDictionaryMeaning("running") { null }.meaning)
    }
    @Test fun fillsDialogueGapsWithoutOverridingPersonalOrIndexedExactForms() {
        val custom = WordMeaning("hangry", "معنی شخصی")
        assertEquals(custom, immediateDictionaryMeaning("hangry", mapOf("hangry" to custom))?.meaning)
        assertEquals("hangry", immediateDictionaryMeaning("hangry", emptyMap())?.meaning?.headword)
        val search = mergeDictionarySearch("binge", emptyList(), emptyMap())
        assertEquals("binge-watch", search.entries.first { it.term == "binge-watching" }.meaning.headword)
        val phrase = subtitleTerms("Let's call it a day.", mode = SubtitleLookupMode.Phrases).last()
        assertEquals("call it a day", phrase.meaning?.headword)
        assertEquals("withdraw", dictionaryBaseForms("withdrew").single())
        val exact = WordMeaning("screenshots", "تصاویر صفحه")
        assertEquals(exact, indexedDictionaryMeaning("screenshots") { exact }.meaning)
        assertTrue(indexedDictionaryMeaning("screenshots") { null }.meaning!!.persian.startsWith("screenshot:"))
    }
    @Test fun ambiguousStemsRemainVisibleAndBounded() {
        val result = indexedDictionaryMeaning("axes") { when (it) {
            "axe" -> WordMeaning(it, "تبر")
            "ax" -> WordMeaning(it, "تبر")
            else -> null
        } }
        assertTrue(result.meaning!!.persian.contains("axe:"))
        assertTrue(result.meaning.persian.contains("ax:"))
        assertTrue(result.meaning.persian.length <= 1000)
    }
    @Test fun onlineErrorsHtmlLatinAndOversizeResultsAreRejected() {
        assertFalse(validOnlineDefinition("MYMEMORY WARNING: quota exceeded"))
        assertFalse(validOnlineDefinition("Serendipity"))
        assertFalse(validOnlineDefinition("<b>سلام</b>"))
        assertFalse(validOnlineDefinition("سلام\u0000"))
        assertFalse(validOnlineDefinition("س".repeat(1001)))
        assertFalse(validOnlineDefinition(""))
        assertTrue(validOnlineDefinition("سلام؛ درود"))
    }
}
