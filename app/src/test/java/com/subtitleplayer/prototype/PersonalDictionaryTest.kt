package com.subtitleplayer.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalDictionaryTest {
    @Test
    fun normalizesCaseApostrophesAndSpacesIntoOneStableKey() {
        assertEquals("can't", normalizeDictionaryTerm("  CAN’T  "))
        assertEquals("looking for", normalizeDictionaryTerm("Looking\t\u00A0for"))
        assertEquals("well-known", normalizeDictionaryTerm("Well-Known"))
    }

    @Test
    fun rejectsMalformedOrOversizedEnglishTerms() {
        listOf("", " ", "two, words", "look\nfor", "سلام", "one 2", "a".repeat(81),
            List(9) { "a" }.joinToString(" "), "can't\u0000").forEach {
            assertNull(it, normalizeDictionaryTerm(it))
        }
        assertEquals("a".repeat(80), normalizeDictionaryTerm("a".repeat(80)))
    }

    @Test
    fun rejectsEmptyOversizedAndControlCharacterDefinitions() {
        listOf("", "  ", "x".repeat(1001), "test\u0000", "test\rtext").forEach {
            assertNull(validPersonalMeaning(it))
        }
        assertEquals("معنی\nبا مثال", validPersonalMeaning("  معنی\nبا مثال  "))
        assertEquals("x".repeat(1000), validPersonalMeaning("x".repeat(1000)))
    }

    @Test
    fun resolvesUnknownWordsFromThePersonalDictionary() {
        val meaning = WordMeaning("unlisted", "معنی دلخواه")
        val term = subtitleTerms("UNLISTED!", mapOf("unlisted" to meaning)).single()
        assertEquals(meaning, term.meaning)
        assertEquals("UNLISTED", term.text)
        assertEquals("UNLISTED", "UNLISTED!".substring(term.start, term.endExclusive))
    }

    @Test
    fun personalMeaningOverridesDefaultButDoesNotGuessInflections() {
        val personal = WordMeaning("go", "معنی اصلاح‌شده")
        val terms = subtitleTerms("WENT go", mapOf("went" to personal))
        assertEquals(personal, terms.first().meaning)
        assertEquals(WordMeaning("go", "رفتن"), terms.last().meaning)
        assertEquals(WordMeaning("go", "رفتن"), subtitleTerms("WENT").single().meaning)
    }

    @Test
    fun personalPhraseKeepsOriginalSpansAndPunctuationBoundaries() {
        val meaning = WordMeaning("look for", "جست‌وجو کردن")
        val text = "Looking\tfor shelter; looking, for."
        val terms = subtitleTerms(text, mapOf("looking for" to meaning))
        assertEquals(meaning, terms.first().meaning)
        assertEquals("Looking\tfor", text.substring(terms.first().start, terms.first().endExclusive))
        assertEquals(listOf("Looking for", "shelter", "looking", "for"), terms.map { it.text })
        assertEquals("look", terms[2].meaning?.headword)
    }

    @Test
    fun removingOverrideRestoresBuiltInMeaningOrUnknownState() {
        assertEquals(WordMeaning("give up", "تسلیم شدن / دست کشیدن"), defaultTermMeaning("GAVE  UP"))
        assertEquals(WordMeaning("go", "رفتن"), defaultTermMeaning("WENT"))
        assertNull(defaultTermMeaning("unlisted"))
    }
}
