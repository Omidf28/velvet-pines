package com.subtitleplayer.prototype

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun skipsParentLinksArchivesAndUnsupportedLinks() {
        for (href in listOf("#section", "index.html#section", "../", "episode.zip", "http://media.example/episode.mp4",
            "javascript:play()", "https://user@media.example/episode.mp4", "bad url")) {
            assertNull(href, resolveStreamIndexEntry(base, href, "Episode"))
        }
    }
}
