package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class DictionaryFormsTest {
    @Test fun irregularFormsRetainTheirBase() {
        assertEquals(listOf("go"), dictionaryBaseForms("WENT"))
        assertEquals(listOf("child"), dictionaryBaseForms("children"))
    }
    @Test fun regularFormsOfferUsefulCandidates() {
        assertTrue("study" in dictionaryBaseForms("studies"))
        assertTrue("run" in dictionaryBaseForms("running"))
        assertTrue("stop" in dictionaryBaseForms("stopped"))
        assertTrue("make" in dictionaryBaseForms("making"))
        assertTrue("happy" in dictionaryBaseForms("happiest"))
    }
    @Test fun possessiveAndCaseNormalization() {
        assertTrue("cooper" in dictionaryBaseForms("Cooper’s"))
        assertEquals(dictionaryBaseForms("Running"), dictionaryBaseForms("running"))
    }
    @Test fun noRootForNegationOrUnsupportedInputs() {
        assertTrue(dictionaryBaseForms("wasn't").isEmpty())
        assertTrue(dictionaryBaseForms("123").isEmpty())
        assertTrue(dictionaryBaseForms("take off").isEmpty())
        assertTrue(dictionaryBaseForms("glass").isEmpty())
    }
    @Test fun functionWordsHavePersianMeanings() {
        for (key in listOf("a", "an", "the", "you", "is", "can't", "don't")) {
            assertTrue(commonWordMeanings.getValue(key).any { it in '\u0600'..'\u06ff' })
        }
    }
}
