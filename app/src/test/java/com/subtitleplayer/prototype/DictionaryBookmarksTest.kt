package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class DictionaryBookmarksTest {
    private val entries = (0..44).map {
        val term = "zsaved" + ('a' + it / 26) + ('a' + it % 26)
        DictionaryBookmark(term, term, "معنی ذخیره‌شده")
    }

    @Test fun savedPagesAreSortedAndKeepEverySnapshot() {
        val first = searchDictionaryBookmarks(entries.reversed(), "", "", emptyMap())
        assertEquals(40, first.entries.size)
        assertTrue(first.hasMore)
        val second = searchDictionaryBookmarks(entries, "", first.entries.last().term, emptyMap())
        assertEquals(entries.map { it.wordKey }, (first.entries + second.entries).map { it.term })
        assertFalse(second.hasMore)
        assertEquals("معنی ذخیره‌شده", second.entries.first().meaning.persian)
    }

    @Test fun savedSearchMatchesOnlySavedKeysAndPrefersPersonalMeanings() {
        val saved = listOf(DictionaryBookmark("coffee", "coffee", "قدیمی"))
        val personal = mapOf("coffee" to WordMeaning("coffee", "شخصی"))
        val match = searchDictionaryBookmarks(saved, "COF", "", personal).entries.single()
        assertEquals("شخصی", match.meaning.persian)
        assertEquals("Personal dictionary", match.source)
        assertTrue(searchDictionaryBookmarks(saved, "forest", "", personal).entries.isEmpty())
        assertTrue(searchDictionaryBookmarks(saved, "%", "", personal).entries.isEmpty())
        assertTrue(searchDictionaryBookmarks(emptyList(), "", "", personal).entries.isEmpty())
    }
}
