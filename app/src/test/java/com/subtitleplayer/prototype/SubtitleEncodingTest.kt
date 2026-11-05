package com.subtitleplayer.prototype

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SubtitleEncodingTest {
    private val persian = "1\n00:00:01,000 --> 00:00:02,000\nسلام"

    @Test fun utf8WithAndWithoutBomHasPriorityForBothRoles() {
        val bytes = persian.toByteArray(Charsets.UTF_8)
        assertEquals(persian, decodeSubtitleBytes(bytes, false))
        assertEquals(persian, decodeSubtitleBytes(byteArrayOf(-17, -69, -65) + bytes, true))
        assertEquals("سلام", parseSubtitleFile(decodeSubtitleBytes(bytes, true)).single().text)
    }
    @Test fun utf16BomWorksInBothByteOrders() {
        assertEquals(persian, decodeSubtitleBytes(byteArrayOf(-1, -2) + persian.toByteArray(Charsets.UTF_16LE), true))
        assertEquals(persian, decodeSubtitleBytes(byteArrayOf(-2, -1) + persian.toByteArray(Charsets.UTF_16BE), false))
    }
    @Test fun persianLegacyFileUsesWindows1256() {
        assertEquals(persian, decodeSubtitleBytes(persian.toByteArray(Charset.forName("windows-1256")), true))
    }
    @Test fun englishLegacyFilePreservesSmartApostrophe() {
        val text = "She can’t go."
        assertEquals(text, decodeSubtitleBytes(text.toByteArray(Charset.forName("windows-1252")), false))
    }
    @Test(expected = IOException::class) fun malformedBomMarkedUnicodeDoesNotFallBack() {
        decodeSubtitleBytes(byteArrayOf(-17, -69, -65, -1), true)
    }
    @Test(expected = IOException::class) fun utf32IsRejectedInsteadOfBeingMistakenForUtf16() {
        decodeSubtitleBytes(byteArrayOf(-1, -2, 0, 0, 65, 0, 0, 0), false)
    }
    @Test fun readingIsBoundedAtTheExactByteLimit() {
        assertArrayEquals(byteArrayOf(1, 2), readSubtitleBytes(ByteArrayInputStream(byteArrayOf(1, 2)), 2))
        org.junit.Assert.assertThrows(IOException::class.java) {
            readSubtitleBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2)
        }
    }
}
