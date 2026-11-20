package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class SharedStreamLinkTest {
    private fun ready(url: String, text: String = url) = assertEquals(SharedStreamInput.Ready(url), parseSharedStreamText(text))

    @Test fun acceptsDirectVideoArchiveAndRtspWithoutChangingSignedQueries() {
        ready("https://media.example/Episode%20One.mkv?key=a%2Bb&end=123",
            "  HTTPS://media.example/Episode%20One.mkv?key=a%2Bb&end=123#preview  ")
        ready("https://media.example/series/index.html")
        ready("rtsp://media.example:8554/live?channel=1")
        ready("https://media.example/live.m3u8?signature=abc,def!")
    }

    @Test fun extractsOneUrlFromBrowserTextAndBalancedProseWrappers() {
        val url = "https://media.example/Episode_(1).mp4"
        ready(url, "Watch this episode:\n($url)")
        ready(url, "Watch <$url>")
        ready(url, "Watch '$url'")
        ready(url, "Watch ‘$url’")
        ready(url, "Watch $url.")
        ready(url, "Watch $url\n$url")
    }

    @Test fun refusesAmbiguousUnsupportedAndCredentialBearingLinks() {
        for (text in listOf(null, "", "No video here", "http://media.example/file.mp4",
            "https://user:password@media.example/file.mp4", "file:///sdcard/video.mp4",
            "smb://media.example/share/video.mp4", "https:///file.mp4",
            "https://media.example:99999/file.mp4", "javascript://media.example/file.mp4",
            "https://media.example/one.mp4 https://media.example/two.mp4")) {
            assertTrue(text, parseSharedStreamText(text) is SharedStreamInput.Invalid)
        }
    }

    @Test fun rejectsOversizedTextAndUrlsWithoutTruncatingThemIntoPlayableLinks() {
        assertTrue(parseSharedStreamText("https://media.example/" + "x".repeat(8192)) is SharedStreamInput.Invalid)
        assertTrue(parseSharedStreamText("x".repeat(sharedStreamTextLimit + 1)) is SharedStreamInput.Invalid)
    }
}
