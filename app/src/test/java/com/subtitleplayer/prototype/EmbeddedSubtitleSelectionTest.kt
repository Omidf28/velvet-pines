package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class EmbeddedSubtitleSelectionTest {
    private val english = EmbeddedSubtitleCandidate("english", "en")
    private val french = EmbeddedSubtitleCandidate("french", "fr", default = true)

    @Test fun recognizesEnglishMetadataWithoutGuessingLabelsOrOtherLanguages() {
        listOf("en", "eng", "en-US", "EN_gb", " eng-GB ").forEach {
            assertTrue(it, EmbeddedSubtitleCandidate(it, it).english)
        }
        listOf(null, "", "und", "zxx", "fr", "fa", "english", "enochian").forEach {
            assertFalse(it, EmbeddedSubtitleCandidate("unknown", it).english)
        }
    }

    @Test fun newSelectionFindsEnglishInsteadOfTheContainerDefaultLanguage() {
        val selected = EmbeddedSubtitleSelection().refresh(listOf(french, english))
        assertTrue(selected.automaticEnglish)
        assertEquals("english", selected.key)
    }

    @Test fun completeDialogueTakesPriorityOverDefaultForcedSubtitles() {
        val forced = english.copy(key = "forced", forced = true, default = true)
        assertEquals("english", EmbeddedSubtitleSelection().refresh(listOf(forced, english)).key)
    }

    @Test fun aDefaultEnglishTrackWinsAmongCompleteTracks() {
        val preferred = english.copy(key = "preferred", language = "eng", default = true)
        assertEquals("preferred", EmbeddedSubtitleSelection().refresh(listOf(english, preferred)).key)
    }

    @Test fun equivalentTracksDoNotSwitchWhenDiscoveryOrderChanges() {
        val other = english.copy(key = "other", language = "en-US")
        val initial = EmbeddedSubtitleSelection().refresh(listOf(english, other))
        assertEquals("english", initial.refresh(listOf(other, english)).key)
    }

    @Test fun aNewDefaultTrackCanReplaceAnAutomaticNonDefaultChoice() {
        val initial = EmbeddedSubtitleSelection().refresh(listOf(english))
        val preferred = english.copy(key = "preferred", default = true)
        assertEquals("preferred", initial.refresh(listOf(english, preferred)).key)
    }

    @Test fun forcedEnglishCanBeUsedIfItIsTheOnlyEnglishOption() {
        val forced = english.copy(key = "forced", forced = true)
        assertEquals("forced", EmbeddedSubtitleSelection().refresh(listOf(french, forced)).key)
    }

    @Test fun missingLanguageMetadataDoesNotBecomeAnAutomaticEnglishChoice() {
        assertNull(EmbeddedSubtitleSelection().refresh(listOf(english.copy(language = null))).key)
    }

    @Test fun disappearanceOfEnglishDoesNotFallBackToAnotherLanguage() {
        val initial = EmbeddedSubtitleSelection().refresh(listOf(english))
        val missing = initial.refresh(listOf(french))
        assertTrue(missing.automaticEnglish)
        assertNull(missing.key)
        assertEquals("english", missing.refresh(listOf(english)).key)
    }

    @Test fun explicitlyTurningOffSubtitlesSurvivesSubsequentDiscovery() {
        val off = EmbeddedSubtitleSelection().manual(null)
        assertFalse(off.automaticEnglish)
        assertEquals(off, off.refresh(emptyList()).refresh(listOf(english, french)))
    }

    @Test fun aManualNonEnglishChoiceIsNotReplacedByEnglish() {
        val manual = EmbeddedSubtitleSelection().manual("french")
        assertEquals(manual, manual.refresh(listOf(english, french)))
    }

    @Test fun reselectingTheCurrentAutomaticTrackMakesItAnExplicitManualChoice() {
        val initial = EmbeddedSubtitleSelection().refresh(listOf(english))
        val manual = initial.manual(initial.key)
        val newerDefault = english.copy(key = "new-default", default = true)
        assertFalse(manual.automaticEnglish)
        assertEquals("english", manual.refresh(listOf(english, newerDefault)).key)
    }

    @Test fun anExternalSubtitleFileTakesPriorityOverAutomaticOrManualEmbeddedTracks() {
        val automatic = EmbeddedSubtitleSelection().refresh(listOf(english), externalFileSelected = true)
        val manual = EmbeddedSubtitleSelection().manual("english").refresh(listOf(english), externalFileSelected = true)
        assertEquals(EmbeddedSubtitleSelection(automaticEnglish = false), automatic)
        assertEquals(automatic, manual)
    }

    @Test fun theUserCanReenableDiscoveryAfterTurningSubtitlesOff() {
        val selected = EmbeddedSubtitleSelection().manual(null).automatic().refresh(listOf(english, french))
        assertTrue(selected.automaticEnglish)
        assertEquals("english", selected.key)
    }

    @Test fun theUserCanReturnFromAnotherLanguageToAutomaticEnglish() {
        val selected = EmbeddedSubtitleSelection().manual("french").automatic().refresh(listOf(english, french))
        assertEquals("english", selected.key)
    }

    @Test fun aMissingManualTrackRemainsRequestedForLaterDiscovery() {
        val manual = EmbeddedSubtitleSelection().manual("english")
        assertEquals(manual, manual.refresh(emptyList()).refresh(listOf(french)))
        assertEquals(manual, manual.refresh(listOf(english, french)))
    }

    @Test fun legacyProfileDefaultsPreserveManualAndOffChoices() {
        val off = VideoSubtitleSettings()
        val manual = VideoSubtitleSettings(embeddedTrack = "english")
        assertFalse(off.automaticEnglish)
        assertFalse(off.automaticSidecars)
        assertFalse(manual.automaticEnglish)
        assertNull(EmbeddedSubtitleSelection(off.automaticEnglish, off.embeddedTrack).refresh(listOf(english)).key)
        assertEquals("english", EmbeddedSubtitleSelection(manual.automaticEnglish, manual.embeddedTrack)
            .refresh(listOf(english.copy(key = "new-default", default = true))).key)
    }
}
