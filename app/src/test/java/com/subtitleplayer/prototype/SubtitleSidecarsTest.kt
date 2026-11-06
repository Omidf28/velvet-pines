package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class SubtitleSidecarsTest {
    private fun find(video: String = "Episode.mkv", vararg names: String): SidecarMatches =
        matchSubtitleSidecars(video, names.mapIndexed { index, name -> SidecarFile("opaque-$index", name) })

    @Test fun recognizesExactEnglishSidecarNames() {
        val result = find(names = arrayOf("Episode.en.srt"))
        assertEquals("Episode.en.srt", result.automatic?.file?.name)
        assertTrue(result.matches.single().english)
    }

    @Test fun acceptsRegionalAndThreeLetterEnglishTagsWithoutCaseDependence() {
        listOf("Episode.eng.vtt", "EPISODE.EN_us.SRT", "Episode.eng-GB.srt", "Episode.en-001.vtt").forEach {
            assertEquals(it, find(names = arrayOf(it)).automatic?.file?.name)
        }
    }

    @Test fun anUntaggedSubtitleRequiresAnExplicitChoice() {
        val result = find(names = arrayOf("Episode.srt"))
        assertNull(result.automatic)
        assertEquals("Episode.srt", result.matches.single().file.name)
        assertFalse(result.matches.single().english)
    }

    @Test fun otherLanguageTagsAreNotSuggestedAsEnglish() {
        assertTrue(find(names = arrayOf("Episode.fa.srt", "Episode.fr.vtt", "Episode.und.srt")).matches.isEmpty())
    }

    @Test fun doesNotConfuseEpisodeNumberPrefixes() {
        assertTrue(find("Series.S01E01.mkv", "Series.S01E010.en.srt", "Series.S01E02.en.srt").matches.isEmpty())
    }

    @Test fun requiresTheEntireReleaseBasename() {
        assertTrue(find("Episode.1080p.mkv", "Episode.en.srt", "Episode.720p.en.srt").matches.isEmpty())
        assertNotNull(find("Episode.1080p.mkv", "Episode.1080p.en.srt").automatic)
    }

    @Test fun rejectsBackupFilesAndUnsupportedSubtitleFormats() {
        assertTrue(find(names = arrayOf("Episode.en.srt.bak", "Episode.en.ass", "Episode.en.zip")).matches.isEmpty())
    }

    @Test fun matchesUnicodeNormalizedNamesWithoutLosingOpaqueIds() {
        val result = matchSubtitleSidecars("Café 🎬.mkv", listOf(SidecarFile("provider:id/with:opaque-parts", "CAFE\u0301 🎬.en.srt")))
        assertEquals("provider:id/with:opaque-parts", result.automatic?.file?.id)
    }

    @Test fun aVideoNameWithoutAnExtensionStillUsesNormalizedCase() {
        assertEquals("episode.en.srt", find("EPISODE", "episode.en.srt").automatic?.file?.name)
    }

    @Test fun unknownSuffixesAreNotTreatedAsEnglish() {
        assertTrue(find(names = arrayOf("Episode.english.srt", "Episode.en.commentary.srt", "Episode.en.fake.vtt")).matches.isEmpty())
    }

    @Test fun equallyRankedEnglishFilesAreAmbiguousRegardlessOfListingOrder() {
        val a = SidecarFile("a", "Episode.en.srt")
        val b = SidecarFile("b", "Episode.eng.vtt")
        val forward = matchSubtitleSidecars("Episode.mkv", listOf(a, b))
        assertNull(forward.automatic)
        assertNull(matchSubtitleSidecars("Episode.mkv", listOf(b, a)).automatic)
        assertEquals(2, forward.matches.size)
    }

    @Test fun duplicateProviderRowsDoNotCreateFalseAmbiguity() {
        val file = SidecarFile("same", "Episode.en.srt")
        assertEquals(file, matchSubtitleSidecars("Episode.mkv", listOf(file, file)).automatic?.file)
    }

    @Test fun uniqueClosedCaptionsCanBeAutomaticallySelected() {
        val result = find(names = arrayOf("Episode.en.sdh.vtt"))
        assertNotNull(result.automatic)
        assertTrue(result.matches.single().captions)
    }

    @Test fun ordinaryEnglishTakesPriorityOverClosedCaptions() {
        assertEquals("Episode.en.srt", find(names = arrayOf("Episode.en.cc.srt", "Episode.en.srt")).automatic?.file?.name)
    }

    @Test fun forcedSidecarsRequireManualChoice() {
        val result = find(names = arrayOf("Episode.forced.en.srt"))
        assertNull(result.automatic)
        assertTrue(result.matches.single().forced)
        assertTrue(result.matches.single().english)
    }

    @Test fun forcedFilesDoNotMakeAnOrdinaryEnglishFileAmbiguous() {
        assertEquals("Episode.eng.srt", find(names = arrayOf("Episode.en.forced.srt", "Episode.eng.srt")).automatic?.file?.name)
    }

    @Test fun multipleLanguageMarkersAndEmptyStemsAreRejected() {
        assertTrue(find(names = arrayOf("Episode.en.eng.srt")).matches.isEmpty())
        assertTrue(find(".mkv", ".en.srt").matches.isEmpty())
    }

    @Test fun exactUntaggedAndEnglishFilesCanCoexistWithoutGuessingTheUntaggedLanguage() {
        val result = find(names = arrayOf("Episode.srt", "Episode.en.srt"))
        assertEquals(2, result.matches.size)
        assertEquals("Episode.en.srt", result.automatic?.file?.name)
    }

    @Test fun aLateResultCannotOverwriteAManualChoiceOrAnActiveWordLookup() {
        val started = SubtitleDiscoveryVersion(10, 0)
        assertFalse(canApplySidecarAutomatically(started, started.copy(preference = 1), true, false))
    }

    @Test fun returningToTheSameVideoAfterAnotherOpenDoesNotReviveAnOldRequest() {
        assertFalse(canApplySidecarAutomatically(SubtitleDiscoveryVersion(10, 0), SubtitleDiscoveryVersion(12, 0), true, false))
    }

    @Test fun onlyAnUnchangedAutomaticSessionCanAcceptAResult() {
        val version = SubtitleDiscoveryVersion(10, 3)
        assertTrue(canApplySidecarAutomatically(version, version, true, false))
        assertFalse(canApplySidecarAutomatically(version, version, false, false))
        assertFalse(canApplySidecarAutomatically(version, version, true, true))
    }
}
