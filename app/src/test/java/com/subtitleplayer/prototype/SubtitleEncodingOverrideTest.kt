package com.subtitleplayer.prototype

import java.io.IOException
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleEncodingOverrideTest {
    @Test fun explicitEncodingCanResolveAmbiguousUtf8Bytes() {
        val bytes = byteArrayOf(-61, -87)
        assertEquals("é", decodeSubtitleBytes(bytes, false))
        assertEquals("Ã©", decodeSubtitleBytes(bytes, false, SubtitleTextEncoding.Windows1252))
    }
    @Test fun explicitPersianEncodingWorksInEitherSubtitleRole() {
        val text = "سلام"
        val bytes = text.toByteArray(Charset.forName("windows-1256"))
        assertEquals(text, decodeSubtitleBytes(bytes, false, SubtitleTextEncoding.Windows1256))
        assertEquals(text, decodeSubtitleBytes(bytes, true, SubtitleTextEncoding.Windows1256))
    }
    @Test(expected = IOException::class) fun explicitUtf8DoesNotSilentlyUseLegacyFallback() {
        decodeSubtitleBytes(byteArrayOf(-1), true, SubtitleTextEncoding.Utf8)
    }
    @Test fun bomTakesPriorityOverManualLegacyChoice() {
        val text = "سلام"
        val bytes = byteArrayOf(-1, -2) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, decodeSubtitleBytes(bytes, true, SubtitleTextEncoding.Windows1252))
    }
}
