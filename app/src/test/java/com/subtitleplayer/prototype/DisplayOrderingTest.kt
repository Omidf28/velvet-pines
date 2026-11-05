package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class DisplayOrderingTest {
    private val comparator = Comparator<String>(::compareNaturalNames)
    @Test fun episodesAndSeasonsUseNumericOrder() {
        assertEquals(listOf("S1E2", "S1E10", "S2E1", "S10E1"), listOf("S10E1", "S1E10", "S2E1", "S1E2").sortedWith(comparator))
    }
    @Test fun handlesVeryLargeNumbersWithoutOverflow() {
        val small = "episode" + "9".repeat(40)
        val large = "episode1" + "0".repeat(40)
        assertTrue(compareNaturalNames(small, large) < 0)
    }
    @Test fun supportsPersianAndArabicDigits() {
        assertTrue(compareNaturalNames("قسمت ۲", "قسمت ۱۰") < 0)
        assertTrue(compareNaturalNames("قسمت ٢", "قسمت ١٠") < 0)
    }
    @Test fun zeroPaddingAndCaseHaveDeterministicOrder() {
        assertEquals(listOf("ep2", "ep02", "ep002"), listOf("ep002", "ep2", "ep02").sortedWith(comparator))
        assertTrue(compareNaturalNames("Episode2", "episode10") < 0)
        assertEquals(0, compareNaturalNames("same", "same"))
    }
    @Test fun comparisonIsAntisymmetricAndSortedOrderIsTransitive() {
        val names = listOf("", "0", "00", "۰", "a", "A", "a2", "a02", "a10", "a۲", "a٢٠", "a9x")
        names.forEach { a -> names.forEach { b ->
            assertEquals(Integer.signum(compareNaturalNames(a,b)), -Integer.signum(compareNaturalNames(b,a)))
        } }
        names.forEach { a -> names.forEach { b -> names.forEach { c ->
            if (compareNaturalNames(a,b) <= 0 && compareNaturalNames(b,c) <= 0) {
                assertTrue("$a <= $b <= $c", compareNaturalNames(a,c) <= 0)
            }
        } } }
        val sorted = names.sortedWith(comparator)
        sorted.indices.forEach { i -> (i until sorted.size).forEach { j ->
            assertTrue(compareNaturalNames(sorted[i], sorted[j]) <= 0)
        } }
    }
    private fun word(id: Long, text: String, due: Long) = SavedWord(text, text, text, "معنی", "dialogue", 0, 0, "source", "name", id = id, reviewDueAt = due)
    @Test fun notebookSortsBySavedOrderAndWordWithoutMutatingInput() {
        val words = listOf(word(2,"b",0), word(1,"a",100), word(3,"a",50))
        assertEquals(listOf(3L,2L,1L), orderedSavedWords(words,WordDisplayOrder.Newest).map { it.id })
        assertEquals(listOf(1L,2L,3L), orderedSavedWords(words,WordDisplayOrder.Oldest).map { it.id })
        assertEquals(listOf(3L,1L,2L), orderedSavedWords(words,WordDisplayOrder.Alphabetical).map { it.id })
        assertEquals(listOf(2L,1L,3L), words.map { it.id })
    }
    @Test fun dueSortIncludesNewCardsFirstAndBreaksTiesById() {
        val words = listOf(word(3,"c",50), word(2,"b",0), word(1,"a",50))
        assertEquals(listOf(2L,1L,3L), orderedSavedWords(words,WordDisplayOrder.Due).map { it.id })
    }
}
