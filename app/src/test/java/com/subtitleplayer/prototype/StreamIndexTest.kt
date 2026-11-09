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
            StreamIndexEntry(
                "Episode 2", "Episode 2.mkv",
                OnlineVideoLink("https://media.example/series/Season%201/Episode%202.mkv?token=abc", OnlineVideoFormat.Auto)
            ),
            entry
        )
    }

    @Test fun recognizesStreamingManifestsAndUsesTheFileNameAsFallback() {
        val entry = resolveStreamIndexEntry(base, "//cdn.example/live/episode.m3u8", "")
        assertEquals("episode.m3u8", entry?.title)
        assertEquals(OnlineVideoFormat.Hls, entry?.link?.format)
        assertEquals(OnlineVideoFormat.Dash, resolveStreamIndexEntry(base, "/live/episode.mpd", "")?.link?.format)
    }

    @Test fun skipsPagesArchivesAndUnsupportedLinks() {
        for (href in listOf("episode.html", "episode.zip", "http://media.example/episode.mp4",
            "javascript:play()", "https://user@media.example/episode.mp4", "bad url")) {
            assertNull(href, resolveStreamIndexEntry(base, href, "Episode"))
        }
    }
}
