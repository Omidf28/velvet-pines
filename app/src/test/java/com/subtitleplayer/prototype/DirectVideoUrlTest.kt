package com.subtitleplayer.prototype

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectVideoUrlTest {
    @Test
    fun preservesEncodedPathAndSignedQueryWithoutTheFragment() {
        assertEquals(
            OnlineVideoLink("https://example.org:8443/my%20video?token=a%2Bb&expires=123", OnlineVideoFormat.Auto),
            parseOnlineVideoLink("  HTTPS://example.org:8443/my%20video?token=a%2Bb&expires=123#preview  ")
        )
        assertEquals(
            OnlineVideoLink("https://example.org/watch?id=42", OnlineVideoFormat.Auto),
            parseOnlineVideoLink("https://example.org/watch?id=42")
        )
    }

    @Test
    fun rejectsLocalPathsCredentialsAndMalformedUrls() {
        listOf(
            "http://example.org/movie.mp4",
            "file:///sdcard/movie.mp4",
            "content://example.provider/movie",
            "https:///movie.mp4",
            "https://user:pass@example.org/movie.mp4",
            "https://example.org:99999/movie.mp4",
            "https://example.org/my movie.mp4"
        ).forEach { url ->
            OnlineVideoFormat.entries.forEach { format ->
                assertNull(url, parseOnlineVideoLink(url, format))
            }
        }
    }

    @Test
    fun recognizesAdaptiveManifestsByPathAndPreservesSignedQueries() {
        assertEquals(
            OnlineVideoLink("https://example.org/stream.M3U8?token=secret", OnlineVideoFormat.Hls),
            parseOnlineVideoLink("https://example.org/stream.M3U8?token=secret")
        )
        assertEquals(
            OnlineVideoFormat.Dash,
            parseOnlineVideoLink("https://example.org/live%2Empd?token=secret")?.format
        )
        assertEquals(
            OnlineVideoFormat.Auto,
            parseOnlineVideoLink("https://example.org/video.mp4?alias=live.mpd")?.format
        )
    }

    @Test
    fun honorsExplicitFormatForLinksWithoutExtensions() {
        val url = "https://example.org/live?id=42"
        assertEquals(OnlineVideoLink(url, OnlineVideoFormat.Hls), parseOnlineVideoLink(url, OnlineVideoFormat.Hls))
        assertEquals(OnlineVideoLink(url, OnlineVideoFormat.Dash), parseOnlineVideoLink(url, OnlineVideoFormat.Dash))
    }

    @Test
    fun recognizesRtspBySchemeAndPreservesServerPortAndQuery() {
        val link = OnlineVideoLink("rtsp://192.168.1.20:8554/live?channel=1", OnlineVideoFormat.Rtsp)
        assertEquals(link, parseOnlineVideoLink(" RTSP://192.168.1.20:8554/live?channel=1#preview "))
        assertEquals(link, parseOnlineVideoLink(link.url, OnlineVideoFormat.Rtsp))
    }

    @Test
    fun keepsRtspAndHttpsFormatsSeparateAndRejectsCredentials() {
        assertNull(parseOnlineVideoLink("rtsp://192.168.1.20/live", OnlineVideoFormat.Hls))
        assertNull(parseOnlineVideoLink("rtsp://192.168.1.20/live", OnlineVideoFormat.Dash))
        assertNull(parseOnlineVideoLink("https://example.org/video.mp4", OnlineVideoFormat.Rtsp))
        assertNull(parseOnlineVideoLink("rtsp://user:pass@192.168.1.20/live"))
        assertNull(parseOnlineVideoLink("rtsps://192.168.1.20/live"))
    }

    @Test
    fun resolvesSmbServerShareAndDecodedFilePath() {
        val url = "smb://nas.example.org:1445/Media/Season%201/episode%20one.mkv"
        assertEquals(OnlineVideoLink(url, OnlineVideoFormat.Smb), parseOnlineVideoLink(url))
        assertEquals(
            SmbFileLocation("nas.example.org", 1445, "Media", "Season 1\\episode one.mkv"),
            parseSmbFileLocation(URI(url))
        )
        assertEquals(445, parseSmbFileLocation(URI("smb://nas/Media/file.mp4"))?.port)
    }

    @Test
    fun rejectsIncompleteSmbPathsTraversalAndInlineCredentials() {
        listOf(
            "smb://nas",
            "smb://nas/Media",
            "smb://nas/Media/Season/",
            "smb://nas/Media/%2e%2e/file.mp4",
            "smb://nas/Media/file%5cname.mp4",
            "smb://nas/Media/file.mp4?password=secret",
            "smb://user:secret@nas/Media/file.mp4"
        ).forEach { assertNull(it, parseOnlineVideoLink(it)) }
        assertNull(parseOnlineVideoLink("smb://nas/Media/file.mp4", OnlineVideoFormat.Hls))
    }

    @Test
    fun navigatesFromShareRootAndBuildsEncodedChildUrls() {
        val root = parseSmbDirectory("smb://nas.local:1445/Media/")!!
        assertEquals(SmbFileLocation("nas.local", 1445, "Media", ""), root)
        val folder = root.child("Season 1")
        val file = folder.child("episode #1.mkv")
        assertEquals(root, folder.parent())
        assertEquals(folder, file.parent())
        assertEquals("smb://nas.local:1445/Media/Season%201/episode%20%231.mkv", file.toUrl())
        assertEquals(file, parseSmbFileLocation(URI(file.toUrl())))
        assertEquals(root, root.parent())
    }

    @Test
    fun rejectsUnsafeSmbDirectoryPathsAndEntryNames() {
        listOf(
            "smb://nas.local",
            "smb://nas.local/Media/../Secrets",
            "smb://user:secret@nas.local/Media",
            "smb://nas.local/Media?password=secret",
            "https://nas.local/Media"
        ).forEach { assertNull(it, parseSmbDirectory(it)) }
        listOf(".", "..", "nested/name", "nested\\name", "bad:name").forEach {
            assertFalse(isSmbEntryName(it))
        }
    }

    @Test
    fun createsAStableSourceIdentifierWithoutStoringTheLink() {
        val url = "https://example.org/private/movie.mp4?token=secret"
        val id = remoteVideoId(url)
        assertTrue(id.startsWith("remote:"))
        assertFalse(id.contains("example.org"))
        assertFalse(id.contains("secret"))
        assertEquals(id, remoteVideoId(url))
        assertNotEquals(id, remoteVideoId(url.replace("movie.mp4", "other.mp4")))
    }
}
