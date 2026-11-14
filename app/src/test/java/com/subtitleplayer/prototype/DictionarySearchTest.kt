package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class DictionarySearchTest {
    @Test fun acceptsNormalizedPrefixesAndUnfinishedWords() {
        assertEquals("can't", normalizeDictionaryPrefix("  CAN’T  "))
        assertEquals("take off", normalizeDictionaryPrefix("TAKE\t\u00a0OFF"))
        assertEquals("can'", normalizeDictionaryPrefix("can’"))
        assertEquals("well-", normalizeDictionaryPrefix("well-"))
        assertEquals("ice-cold", normalizeDictionaryPrefix("Ice-Cold"))
    }
    @Test fun rejectsWildcardsInjectionAndUnsupportedScripts() {
        for (value in listOf("", "   ", "%", "_", "a%", "a' OR 1=1", "coffee;", "سلام", "abc1", "cof\nfee", "can''"))
            assertNull(value, normalizeDictionaryPrefix(value))
    }
    @Test fun enforcesTermAndWordBounds() {
        assertNotNull(normalizeDictionaryPrefix("a".repeat(80)))
        assertNull(normalizeDictionaryPrefix("a".repeat(81)))
        assertNotNull(normalizeDictionaryPrefix(List(8) { "word" }.joinToString(" ")))
        assertNull(normalizeDictionaryPrefix(List(9) { "word" }.joinToString(" ")))
    }
    @Test fun personalDefinitionReplacesIndexedAndCuratedVersions() {
        val own = WordMeaning("article", "معنی دلخواه")
        val result = mergeDictionarySearch("the", listOf("the" to WordMeaning("the", "منبع")), mapOf("the" to own))
        val exact = result.entries.first { it.term == "the" }
        assertEquals(own, exact.meaning)
        assertEquals("Personal dictionary", exact.source)
        assertEquals(1, result.entries.count { it.term == "the" })
    }
    @Test fun personalOnlyPhrasesAndCuratedExactEntriesAreIncluded() {
        val custom = WordMeaning("velvet moon", "عبارت شخصی")
        assertEquals(custom, mergeDictionarySearch("velvet", emptyList(), mapOf("velvet moon" to custom)).entries.single().meaning)
        assertTrue(mergeDictionarySearch("afford", emptyList(), emptyMap()).entries.any { it.term == "afford" })
    }
    @Test fun prefixMatchingIsLiteralNotContains() {
        val result = mergeDictionarySearch("forest", listOf(
            "rainforest" to WordMeaning("rainforest", "جنگل بارانی"),
            "forest" to WordMeaning("forest", "جنگل")), emptyMap())
        assertEquals(listOf("forest"), result.entries.map { it.term })
    }
    @Test fun aCompleteVariantAppearsBeforeLongerPrefixMatches() {
        val alternate = WordMeaning("prioritize", "اولویت‌بندی کردن")
        val longer = "prioritised" to WordMeaning("prioritised", "اولویت داده شد")
        val result = mergeDictionarySearch("prioritise", listOf(longer), emptyMap(), limit = 1) {
            alternate.takeIf { meaning -> meaning.headword == it }
        }
        assertEquals(DictionarySearchEntry("prioritise", alternate, "Spelling variant · Offline"), result.entries.single())
        assertTrue(result.hasMore)
    }

    @Test fun exactSearchResultsAndUnfinishedPrefixesSkipAlternateLookup() {
        val own = WordMeaning("prioritise", "معنی شخصی")
        val indexed = listOf("prioritise" to WordMeaning("prioritise", "معنی واژه‌نامه"))
        for (result in listOf(
            mergeDictionarySearch("prioritise", emptyList(), mapOf("prioritise" to own)) { error("Unexpected lookup") },
            mergeDictionarySearch("prioritise", indexed, emptyMap()) { error("Unexpected lookup") }
        )) {
            assertEquals("prioritise", result.entries.single().meaning.headword)
        }
        assertTrue(mergeDictionarySearch("prioritis", emptyList(), emptyMap()) { error("Unexpected lookup") }
            .entries.isEmpty())
    }
    @Test fun resultsAreSortedBoundedAndSignalMore() {
        val input = (0..40).reversed().map { "ztest" + ('a' + it / 26) + ('a' + it % 26) }.map { it to WordMeaning(it, "آزمون") }
        val result = mergeDictionarySearch("ztest", input, emptyMap())
        assertEquals(40, result.entries.size)
        assertTrue(result.hasMore)
        assertEquals(input.map { it.first }.sorted().take(40), result.entries.map { it.term })
        assertFalse(mergeDictionarySearch("ztest", input.take(40), emptyMap()).hasMore)
    }
    @Test fun mergingPersonalResultsKeepsCorrectFirstPage() {
        val indexed = listOf("zebra", "zenith", "zero").map { it to WordMeaning(it, "منبع") }
        val personal = mapOf("zeal" to WordMeaning("zeal", "شور"), "zebra" to WordMeaning("zebra", "گورخر"))
        val result = mergeDictionarySearch("ze", indexed, personal, limit = 2)
        assertEquals(listOf("zeal", "zebra"), result.entries.map { it.term })
        assertTrue(result.hasMore)
        assertEquals("گورخر", result.entries.last().meaning.persian)
    }
    @Test fun emptyMissAndInvalidLimit() {
        assertTrue(mergeDictionarySearch("zzvelvetqz", emptyList(), emptyMap()).entries.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { mergeDictionarySearch("a", emptyList(), emptyMap(), 0) }
    }
}
