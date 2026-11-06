package com.subtitleplayer.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitlesTest {
    @Test
    fun preservesLineBreaksAndResolvesBothSidesOfACueBoundary() {
        val source = "\uFEFF1\r\n00:00:01,000 --> 00:00:07,000\r\n<i>First line</i>\r\n" +
            "Second line\r\n\r\n2\r\n00:00:07,000 --> 00:00:09,000\r\nNext cue\r\n"
        val cues = parseSrt(source)

        assertNull(activeSubtitle(cues, 999))
        assertEquals(ActiveSubtitle("First line\nSecond line", 1_000), activeSubtitle(cues, 1_000))
        assertEquals(ActiveSubtitle("First line\nSecond line", 1_000), activeSubtitle(cues, 6_999))
        assertEquals(ActiveSubtitle("Next cue", 7_000), activeSubtitle(cues, 7_000))
        assertNull(activeSubtitle(cues, 9_000))
    }

    @Test
    fun keepsOverlappingCuesAndSkipsInvalidTimeRanges() {
        val cues = parseSrt(
            """
            00:00:02,000 --> 00:00:04,000
            Second

            00:00:01,000 --> 00:00:03,000
            First

            00:00:04,000 --> 00:00:01,000
            Invalid
            """.trimIndent()
        )

        assertEquals(ActiveSubtitle("First\nSecond", 1_000), activeSubtitle(cues, 2_000))
        assertEquals(ActiveSubtitle("First", 1_000), activeSubtitle(cues, 1_000))
        assertEquals(2, cues.size)
    }

    @Test
    fun parsesPlainWebVttCuesWithIdsStylesAndShortOrLongTimestamps() {
        val source = "\uFEFF" + """
            WEBVTT captions

            STYLE
            ::cue { color: lime; }

            NOTE this block is skipped
            00:00.000 --> 00:30.000
            Not a cue

            scene-one
            00:01.000 --> 00:03.500 line:90%
            <v Sam><c.yellow>Come &amp; see.</c>
            <00:00:02.000>Now.

            00:00:04.000 --> 00:00:06.000
            It's &lt;here&gt;.

            999999999999999999999:00:00.000 --> 999999999999999999999:00:01.000
            Out of range
            """.trimIndent().replace("\n", "\r\n")

        assertEquals(
            listOf(
                SubtitleCue(1_000, 3_500, "Come & see.\nNow."),
                SubtitleCue(4_000, 6_000, "It's <here>.")
            ),
            parseSubtitleFile(source)
        )
    }

    @Test
    fun rendersEnglishAndPersianWithIndependentCueDelays() {
        val english = parseSubtitleFile("1\n00:00:01,000 --> 00:00:02,000\nHello")
        val persian = parseSubtitleFile("WEBVTT\n\n00:01.000 --> 00:02.000\nسلام")
        val persianDelayMs = 500L

        assertEquals("Hello", activeSubtitle(english, 1_250)?.text)
        assertNull(activeSubtitle(persian, 1_250 - persianDelayMs))
        assertNull(activeSubtitle(english, 2_000))
        assertEquals("سلام", activeSubtitle(persian, 2_000 - persianDelayMs)?.text)
    }

    @Test
    fun mapsDialogueRangesToVideoTimeAndClipsAtBothEnds() {
        val cue = SubtitleCue(1_000, 4_000, "First line")
        assertEquals(DialogueRange("First line", 1_750, 4_750), cue.toDialogueRange(750, 10_000))
        assertEquals(DialogueRange("First line", 0, 2_500), cue.toDialogueRange(-1_500, 10_000))
        assertEquals(DialogueRange("First line", 8_000, 10_000), cue.toDialogueRange(7_000, 10_000))
        assertNull(cue.toDialogueRange(-4_000, 10_000))
        assertNull(cue.toDialogueRange(9_000, 10_000))
        assertNull(cue.toDialogueRange(0, 0))
    }

    @Test
    fun linksAnInflectedPhraseAsOneTermWhilePreservingTheVisibleText() {
        val text = "She GAVE\tUP, then left."
        val terms = subtitleTerms(text, mode = SubtitleLookupMode.Phrases)

        assertEquals(listOf("She", "GAVE UP", "then", "left"), terms.map(SubtitleTerm::text))
        val phrase = terms[1]
        assertEquals("GAVE\tUP", text.substring(phrase.start, phrase.endExclusive))
        assertEquals(WordMeaning("give up", "تسلیم شدن / دست کشیدن"), phrase.meaning)
        assertEquals("leave", terms.last().meaning?.headword)
    }

    @Test
    fun choosesTheLongestRecognizedPhraseAndHandlesNonbreakingSpaces() {
        val terms = subtitleTerms("We ran\u00A0out of time, so we ran out.", mode = SubtitleLookupMode.Phrases)

        assertEquals(listOf("run out of", "run out"), terms.mapNotNull {
            it.meaning?.headword?.takeIf { headword -> headword.startsWith("run out") }
        })
    }

    @Test
    fun doesNotJoinWordsAcrossPunctuationOrHardLineBreaks() {
        val terms = subtitleTerms("Give, up. Look\nfor shelter.", mode = SubtitleLookupMode.Phrases)

        assertEquals(listOf("Give", "up", "Look", "for", "shelter"), terms.map(SubtitleTerm::text))
        assertEquals("give", terms.first().meaning?.headword)
        assertNull(terms[1].meaning)
        assertEquals("shelter", terms.last().meaning?.headword)
    }

    @Test
    fun treatsContractionsAsSingleWordsAndFindsInflectedEntries() {
        val words = subtitleWords.findAll("I can’t afford it. We WENT.").map { it.value }.toList()

        assertEquals(listOf("I", "can’t", "afford", "it", "We", "WENT"), words)
        assertEquals("can", lookupWord(words[1])?.headword)
        assertEquals("go", lookupWord(words.last())?.headword)
        assertEquals("bring", lookupWord("BROUGHT")?.headword)
        assertEquals(normalizeWord("CAN’T"), normalizeWord("can't"))
        assertNull(lookupWord("unlisted"))
    }
}
