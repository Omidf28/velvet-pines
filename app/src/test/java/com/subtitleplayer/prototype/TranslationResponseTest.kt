package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException

class TranslationResponseTest {
    @Test fun exactResponseBoundAndEmptyBodyAreReadSafely() {
        val bytes = ByteArray(maxTranslationResponseBytes) { (it % 127).toByte() }
        assertArrayEquals(bytes, readTranslationResponse(ByteArrayInputStream(bytes)) {})
        assertTrue(readTranslationResponse(ByteArrayInputStream(byteArrayOf())) {}.isEmpty())
    }
    @Test fun oversizedResponseStopsAtTheLimitPlusOneByte() {
        val input = ByteArrayInputStream(ByteArray(1_000_000))
        val failure = assertThrows(OnlineLookupException::class.java) { readTranslationResponse(input) {} }
        assertEquals(LookupFailure.InvalidResponse, failure.failure)
        assertEquals(1_000_000 - maxTranslationResponseBytes - 1, input.available())
    }
    @Test fun cancellationIsCheckedBetweenReads() {
        val input = ByteArrayInputStream(ByteArray(10000))
        var checks = 0
        assertThrows(CancellationException::class.java) {
            readTranslationResponse(input) { if (++checks == 2) throw CancellationException() }
        }
        assertEquals(10000 - 4096, input.available())
    }
    @Test fun cancelledRequestNeverBeginsReading() {
        val input = ByteArrayInputStream(ByteArray(100))
        assertThrows(CancellationException::class.java) { readTranslationResponse(input) { throw CancellationException() } }
        assertEquals(100, input.available())
    }
    @Test fun httpAndProviderStatusesHaveSpecificFailures() {
        assertNull(translationStatusFailure(200))
        assertEquals(LookupFailure.Quota, translationStatusFailure(429))
        assertEquals(LookupFailure.Quota, translationStatusFailure(200, quotaFinished = true))
        assertEquals(LookupFailure.Service, translationStatusFailure(503))
        assertEquals(LookupFailure.Service, translationStatusFailure(-1))
        assertEquals(LookupFailure.Service, translationStatusFailure(302))
    }
}
