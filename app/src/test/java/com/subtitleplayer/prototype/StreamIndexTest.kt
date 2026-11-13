package com.subtitleplayer.prototype

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamIndexTest {
    private val base = URI("https://media.example/series/index.html")

    @Test fun resolvesAnEpisodeRelativeToItsIndex() {
        val entry = resolveStreamIndexEntry(base, "Season%201/Episode%202.mkv?token=abc#preview", "Episode 2")
        assertEquals(
            StreamIndexEntry.Video(
                "Episode 2", "Episode 2.mkv",
                OnlineVideoLink("https://media.example/series/Season%201/Episode%202.mkv?token=abc", OnlineVideoFormat.Auto)
            ),
            entry
        )
    }

    @Test fun recognizesStreamingManifestsAndUsesTheFileNameAsFallback() {
        val entry = resolveStreamIndexEntry(base, "//cdn.example/live/episode.m3u8", "") as? StreamIndexEntry.Video
        assertEquals("episode.m3u8", entry?.title)
        assertEquals(OnlineVideoFormat.Hls, entry?.link?.format)
        val dash = resolveStreamIndexEntry(base, "/live/episode.mpd", "") as? StreamIndexEntry.Video
        assertEquals(OnlineVideoFormat.Dash, dash?.link?.format)
    }

    @Test fun resolvesCatalogsDirectoriesAndPagination() {
        assertEquals(StreamIndexEntry.Page("Season 1", "season1.html", URI("https://media.example/series/season1.html")),
            resolveStreamIndexEntry(base, "season1.html", "Season 1"))
        assertEquals(URI("https://media.example/series/Season%202/"),
            (resolveStreamIndexEntry(base, "Season 2/", "") as? StreamIndexEntry.Page)?.address)
        assertEquals(URI("https://media.example/series/index.html?page=2"),
            (resolveStreamIndexEntry(base, "?page=2", "Next") as? StreamIndexEntry.Page)?.address)
    }

    @Test fun keepsSeasonAndEpisodeNumbersAttachedToTheirLabels() {
        val expected = listOf("season 2", "episode 2")
        assertEquals(expected, streamSearchTerms("Season_02 / Episode-2"))
        assertEquals(expected, streamSearchTerms("فصل ۲، قسمت ۲"))
    }

    @Test fun matchesEpisodeCodesAndLabelsAcrossNamingStyles() {
        assertTrue(matchesStreamSearch(streamSearchTerms("فصل ۲ قسمت ۵"), "Show.S02E05.1080p.mkv"))
        assertTrue(matchesStreamSearch(streamSearchTerms("s02e05"), "Season 2", "Episode 5"))
        assertTrue(matchesStreamSearch(streamSearchTerms("s02e05"), "فصل ۲", "قسمت ۵"))
        assertFalse(matchesStreamSearch(streamSearchTerms("Season 2 Episode 5"), "Show.S01E05.mkv"))
        assertFalse(matchesStreamSearch(streamSearchTerms("S02E05"), "Show.S02E50.mkv"))
        assertFalse(matchesStreamSearch(streamSearchTerms("Season 2"), "Season", "2.mkv"))
    }

    @Test fun episodeTitleUsesTheCatalogPathAndHidesAnAddressUsedAsLinkText() {
        val url = "https://media.example/series/episode.mkv?token=abc"
        val episode = resolveStreamIndexEntry(base, url, url) as StreamIndexEntry.Video
        assertEquals("episode.mkv · Example Series / Season 2", streamEpisodeTitle(episode, "Example Series / Season 2"))
        assertEquals("episode.mkv", streamEpisodeTitle(episode, null))
        assertEquals(url, episode.link.url)
    }

    @Test fun recoversEpisodeNumbersFromFilenamesBehindShortenedLabels() {
        val entries = mapOf(
            "Forest.S02E10.1080p.mkv" to "Season 2 · Episode 10",
            "Forest.2x03.mkv" to "Season 2 · Episode 3",
            "Forest_S02_E04_720p.mkv" to "Season 2 · Episode 4",
            "EP-005.mkv" to "Episode 5",
            "Behind.the.Scenes.1920x1080.mkv" to "Short label"
        )
        for ((filename, expected) in entries) {
            val original = checkNotNull(resolveStreamIndexEntry(base, filename, "Short label"))
            val episode = archiveEpisodeEntry(original)
            assertEquals(expected, episode.title)
            assertEquals(filename, episode.fileName)
            assertEquals(original.url, episode.url)
        }
    }

    @Test fun skipsParentLinksArchivesAndUnsupportedLinks() {
        for (href in listOf("#section", "index.html#section", "../", "episode.zip", "http://media.example/episode.mp4",
            "javascript:play()", "https://user@media.example/episode.mp4", "bad url")) {
            assertNull(href, resolveStreamIndexEntry(base, href, "Episode"))
        }
    }

    @Test fun ranksExactArchiveTitlesAheadOfEarlierPartialMatches() {
        val partial = StreamIndexEntry.CatalogPage("The Forest Tales 2025", "", "partial", emptyList(), "2025")
        val prefix = StreamIndexEntry.CatalogPage("Forest Tales Again 2024", "", "prefix", emptyList(), "2024")
        val exact = StreamIndexEntry.CatalogPage("Forest Tales 2025", "داستان‌های جنگل", "exact", emptyList(), "2025")
        val titles = List(archiveSearchLimit) { partial.copy(url = "partial-$it") } + listOf(prefix, exact)

        assertEquals(listOf(exact, prefix), searchArchiveTitles(titles, "forest tales").take(2))
        assertEquals(listOf(exact), searchArchiveTitles(titles, "داستانهاي جنگل ۲۰۲۵"))
        assertEquals(listOf(exact), searchArchiveTitles(titles, "داستان های جنگل"))
        assertEquals(titles.take(archiveSearchLimit), searchArchiveTitles(titles, ""))
    }
}
