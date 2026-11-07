package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class SubtitleInteractionTest {
    @Test fun defaultWordModeDoesNotExpandCommonPhrases() {
        val words = subtitleTerms("She gave up.")
        assertEquals(listOf("She", "gave", "up"), words.map { it.text })
        assertEquals("give", words[1].meaning?.headword)
        assertEquals(listOf("She", "gave up"),
            subtitleTerms("She gave up.", mode = SubtitleLookupMode.Phrases).map { it.text })
    }

    @Test fun wordModeRespectsPersonalWordsWithoutAbsorbingPersonalPhrases() {
        val words = subtitleTerms("piece of cake", mapOf(
            "piece of cake" to WordMeaning("piece of cake", "بسیار آسان"),
            "piece" to WordMeaning("piece", "قطعه")
        ))
        assertEquals(listOf("piece", "of", "cake"), words.map { it.text })
        assertEquals("قطعه", words.first().meaning?.persian)
    }

    @Test fun phraseModeRetainsPersonalOverridesAndExactOffsets() {
        val text = "A piece\tof cake!"
        val term = subtitleTerms(text, mapOf("piece of cake" to WordMeaning("piece of cake", "بسیار آسان")),
            SubtitleLookupMode.Phrases)[1]
        assertEquals("piece of cake", term.text)
        assertEquals("بسیار آسان", term.meaning?.persian)
        assertEquals("piece\tof cake", text.substring(term.start, term.endExclusive))
    }

    @Test fun wordModeKeepsContractionsAndPunctuationBoundaries() {
        val text = "I can’t, give\nup."
        val words = subtitleTerms(text)
        assertEquals(listOf("I", "can’t", "give", "up"), words.map { it.text })
        assertEquals("can", words[1].meaning?.headword)
        words.forEach { assertEquals(it.text, text.substring(it.start, it.endExclusive)) }
    }

    @Test fun preferencesDefaultToWordsAndRestoreTheExplicitChoice() {
        assertEquals(SubtitleLookupMode.Words, subtitleLookupModeFrom(null))
        assertEquals(SubtitleLookupMode.Words, subtitleLookupModeFrom("old-or-invalid-value"))
        SubtitleLookupMode.entries.forEach { assertEquals(it, subtitleLookupModeFrom(it.name)) }
    }

    @Test fun selectionTargetsOneRepeatedWordRatherThanEveryMatchingWord() {
        val cue = ActiveSubtitle("go go go", 1000)
        val term = subtitleTerms(cue.text)[1]
        val range = SubtitleSelection(cue, term.start, term.endExclusive).highlightFor(cue)
        assertEquals(3..4, range)
        assertEquals("go", cue.text.substring(range!!.first, range.last + 1))
    }

    @Test fun selectionCannotLeakIntoAnotherCueWithTheSameText() {
        val cue = ActiveSubtitle("Hello", 1000)
        val selected = SubtitleSelection(cue, 0, 5)
        assertEquals(0..4, selected.highlightFor(cue.copy()))
        assertNull(selected.highlightFor(cue.copy(startMs = 2000)))
        assertNull(selected.highlightFor(cue.copy(text = "Hello again")))
    }

    @Test fun selectionUsesUtf16OffsetsAfterNonEnglishCharactersAndEmoji() {
        val cue = ActiveSubtitle("🎬 Hello, hello!", 1000)
        val term = subtitleTerms(cue.text)[1]
        val range = SubtitleSelection(cue, term.start, term.endExclusive).highlightFor(cue)
        assertEquals(10..14, range)
        assertEquals("hello", cue.text.substring(range!!.first, range.last + 1))
    }

    @Test fun invalidSelectionBoundsNeverReachTheTextRenderer() {
        val cue = ActiveSubtitle("Hello", 1000)
        listOf(-1 to 2, 2 to 2, 3 to 1, 0 to 6, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (start, end) ->
            assertNull(SubtitleSelection(cue, start, end).highlightFor(cue))
        }
        assertNull(SubtitleSelection(ActiveSubtitle("", 1000), 0, 1).highlightFor(ActiveSubtitle("", 1000)))
    }

    @Test fun changingTheLookupModeRemovesPhraseExpansionWithoutChangingSourceText() {
        val text = "We GAVE\tUP, then left."
        val phrase = subtitleTerms(text, mode = SubtitleLookupMode.Phrases)[1]
        val words = subtitleTerms(text, mode = SubtitleLookupMode.Words)
        assertEquals("GAVE UP", phrase.text)
        assertEquals(listOf("We", "GAVE", "UP", "then", "left"), words.map { it.text })
        assertEquals("GAVE\tUP", text.substring(phrase.start, phrase.endExclusive))
    }

    @Test fun phraseChoicesUseTheTappedOccurrenceAndStopAtSeparators() {
        val cue = ActiveSubtitle("🎬 Go on, go\ton now.\nGo on again.", 1000)
        val word = subtitleTerms(cue.text).filter { it.text.equals("go", ignoreCase = true) }[1]
        val choices = subtitlePhraseCandidates(SubtitleSelection(cue, word.start, word.endExclusive))
        assertEquals(listOf("go on now", "go on"), choices.map { it.text })
        val phrase = choices.last()
        assertEquals("go\ton", cue.text.substring(phrase.start, phrase.endExclusive))
        assertEquals(word.start, phrase.start)
    }

    @Test fun phraseChoicesCanExtendAnExistingPhraseButNeverShrinkIt() {
        val cue = ActiveSubtitle("We gave up hope.", 1000)
        val term = subtitleTerms(cue.text, mode = SubtitleLookupMode.Phrases)[1]
        val choices = subtitlePhraseCandidates(SubtitleSelection(cue, term.start, term.endExclusive))
        assertEquals(listOf("We gave up hope", "We gave up", "gave up hope"), choices.map { it.text })
    }

    @Test fun phraseChoicesStayWithinTheBatchQueryLimit() {
        val cue = ActiveSubtitle("a b c d e f g h i j k l m n o", 1000)
        val term = subtitleTerms(cue.text)[7]
        assertEquals(35, subtitlePhraseCandidates(SubtitleSelection(cue, term.start, term.endExclusive)).size)
        assertEquals(emptyList<SubtitleTerm>(), subtitlePhraseCandidates(SubtitleSelection(cue, 0, 2)))
    }
}
