package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class PersianDictionarySearchTest {
    @Test fun foldsArabicLettersMarksAndJoinersInBothQueriesAndDefinitions() {
        assertEquals(listOf("کتاب", "یادگیری"), persianSearchTokens(" كِتاب  ياد‌گيري "))
        assertTrue(matchesPersianMeaning("کتاب‌های یادگیری زبان", listOf("کتاب", "یادگیر")))
        assertFalse(matchesPersianMeaning("بیاد", listOf("یاد")))
        assertNull(persianSearchTokens("coffee قهوه"))
        assertNull(persianSearchTokens("قهوه%"))
        assertNull(persianSearchTokens("قهوه\nکتاب"))
        assertNull(persianSearchTokens("ک".repeat(81)))
        assertNull(persianSearchTokens(List(9) { "کتاب" }.joinToString(" ")))
    }

    @Test fun reverseResultsUseEffectiveDefinitionsAndIncludePersonalOnlyEntries() {
        val personal = mapOf(
            "coffee" to WordMeaning("coffee", "چای"),
            "zpersonal" to WordMeaning("zpersonal", "قهوه خوشمزه")
        )
        val result = mergePersianDictionarySearch(listOf("قهوه"), listOf(
            "coffee" to WordMeaning("coffee", "قهوه"),
            "coffeepot" to WordMeaning("coffeepot", "قهوه جوش")
        ), personal)
        assertFalse(result.entries.any { it.term == "coffee" })
        assertEquals("Personal dictionary", result.entries.single { it.term == "zpersonal" }.source)
        assertTrue(result.entries.any { it.term == "coffeepot" })
    }

    @Test fun multiwordQueriesRequireEveryTokenAndPreferShortSingleWords() {
        val entries = listOf(
            "long coffee phrase" to WordMeaning("long coffee phrase", "قهوه گرم"),
            "brew" to WordMeaning("brew", "قهوه گرم"),
            "coffeepot" to WordMeaning("coffeepot", "قهوه سرد")
        )
        val result = mergePersianDictionarySearch(listOf("قهوه", "گرم"), entries, emptyMap())
        assertEquals(listOf("brew", "long coffee phrase"), result.entries.map { it.term })
    }

    @Test fun exactPersianSensesRankBeforeIncidentalMentions() {
        val entries = listOf(
            "urn" to WordMeaning("urn", "ظرف قهوه"),
            "coffee" to WordMeaning("coffee", "قهوه‌\nدرخت قهوه")
        )
        val result = mergePersianDictionarySearch(listOf("قهوه"), entries, emptyMap())
        assertEquals("coffee", result.entries.first().term)
    }

    @Test fun savedSearchSupportsPersianAndRetainsItsStoredDefinition() {
        val saved = listOf(DictionaryBookmark("zsaved", "zsaved", "يادگيري كتاب"))
        val result = searchDictionaryBookmarks(saved, "یادگیری کتاب", "", emptyMap())
        assertEquals(listOf("zsaved"), result.entries.map { it.term })
        assertTrue(searchDictionaryBookmarks(saved, "جنگل", "", emptyMap()).entries.isEmpty())
    }
}
