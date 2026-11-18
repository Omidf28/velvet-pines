package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class LearnerProfileTest {
    @Test fun englishLevelFilterAppliesBeforePersonalResultsAreTruncated() {
        val personal = (0..79).associate {
            val term = "zprofile" + ('a' + it / 26) + ('a' + it % 26)
            term to WordMeaning(term, "شخصی")
        }
        val eligible = personal.keys.sorted().takeLast(3).toSet()
        val result = mergeDictionarySearch("zprofile", emptyList(), personal,
            limit = 2, allowed = eligible)
        assertEquals(eligible.sorted().take(2), result.entries.map { it.term })
        assertTrue(result.hasMore)
        val last = mergeDictionarySearch("zprofile", emptyList(), personal,
            after = result.entries.last().term, allowed = eligible)
        assertEquals(eligible.sorted().last(), last.entries.single().term)
        assertFalse(last.hasMore)
        assertTrue(mergeDictionarySearch("coffee", emptyList(), emptyMap(), allowed = emptySet()).entries.isEmpty())
    }

    @Test fun persianAndSavedSearchesUseTheSameEligibleKeys() {
        val personal = mapOf("zpersonal" to WordMeaning("zpersonal", "قهوه"))
        val result = mergePersianDictionarySearch(listOf("قهوه"), listOf(
            "coffee" to WordMeaning("coffee", "قهوه"), "urn" to WordMeaning("urn", "ظرف قهوه")
        ), personal, allowed = setOf("coffee"))
        assertEquals(listOf("coffee"), result.entries.map { it.term })
        val saved = listOf(DictionaryBookmark("coffee", "coffee", "قهوه"))
        assertTrue(searchDictionaryBookmarks(saved, "قهوه", "", personal, allowed = emptySet()).entries.isEmpty())
        assertEquals("coffee", searchDictionaryBookmarks(saved, "قهوه", "", personal,
            allowed = setOf("coffee")).entries.single().term)
    }
}
