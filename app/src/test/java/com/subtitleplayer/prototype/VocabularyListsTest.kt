package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class VocabularyListsTest {
    @Test fun namesAreBoundedNormalizedAndCaseInsensitive() {
        assertEquals("IELTS Writing", validVocabularyListName("  IELTS\u00a0 Writing  "))
        assertEquals("ielts writing", vocabularyListKey("IELTS Writing"))
        assertNull(validVocabularyListName("\nIELTS"))
        assertNull(validVocabularyListName(" "))
        assertNull(validVocabularyListName("a".repeat(81)))
        assertEquals("a".repeat(80), validVocabularyListName("a".repeat(80)))
    }
}
