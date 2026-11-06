package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CancellationException

class WordLookupServiceTest {
    private fun missing() = DictionaryResult(null, "not found")
    private fun meaning(key: String) = WordMeaning(key, "معنی آزمایشی")

    @Test fun offlineHitsAndMissesAreReusedAcrossCaseChanges() {
        var reads = 0
        val service = WordLookupService({ reads++; if (it == "coffee") DictionaryResult(meaning(it), "offline") else missing() }, { key, _ -> meaning(key) })
        assertNotNull(service.resolve("COFFEE", emptyMap()).meaning)
        assertNotNull(service.resolve("coffee", emptyMap()).meaning)
        assertNull(service.resolve("absent", emptyMap()).meaning)
        assertNull(service.resolve("ABSENT", emptyMap()).meaning)
        assertEquals(2, reads)
    }
    @Test fun personalEditsAlwaysOverrideAndDoNotPoisonTheCache() {
        var reads = 0
        val service = WordLookupService({ reads++; DictionaryResult(meaning(it), "offline") }, { key, _ -> meaning(key) })
        val original = service.resolve("coffee", emptyMap())
        val own = WordMeaning("coffee", "قهوهٔ دلخواه")
        assertEquals(own, service.resolve("coffee", mapOf("coffee" to own)).meaning)
        assertEquals(original, service.resolve("coffee", emptyMap()))
        assertEquals(1, reads)
    }
    @Test fun defaultLookupNeverCallsTheNetwork() {
        var calls = 0
        val service = WordLookupService({ missing() }, { key, _ -> calls++; meaning(key) })
        repeat(3) { assertNull(service.resolve("absent", emptyMap()).meaning) }
        assertEquals(0, calls)
    }
    @Test fun successfulOnlineTranslationIsReusedOnAnotherTapWithoutNetwork() {
        var calls = 0
        val service = WordLookupService({ missing() }, { key, _ -> calls++; meaning(key) })
        val result = service.resolve("absent", emptyMap(), WordLookupRequest.Online)
        assertEquals(result.meaning, service.resolve("ABSENT", emptyMap()).meaning)
        assertTrue(service.resolve("absent", emptyMap()).source.contains("حافظهٔ موقت"))
        assertEquals(result.meaning, service.resolve("absent", emptyMap(), WordLookupRequest.Online).meaning)
        assertEquals(1, calls)
    }
    @Test fun packagedDefinitionWinsOverCachedMachineTranslation() {
        val offline = DictionaryResult(WordMeaning("coffee", "قهوه"), "offline")
        val service = WordLookupService({ offline }, { key, _ -> meaning(key) })
        service.resolve("coffee", emptyMap(), WordLookupRequest.Online)
        assertEquals(offline, service.resolve("coffee", emptyMap()))
    }
    @Test fun onlineFailureIsTypedAndAnExplicitRetryCanSucceed() {
        var calls = 0
        val service = WordLookupService({ missing() }, { key, _ ->
            calls++
            if (calls == 1) throw OnlineLookupException(LookupFailure.Timeout)
            meaning(key)
        })
        assertEquals(LookupFailure.Timeout, service.resolve("absent", emptyMap(), WordLookupRequest.Online).failure)
        assertNull(service.resolve("absent", emptyMap()).meaning)
        assertNotNull(service.resolve("absent", emptyMap(), WordLookupRequest.Online).meaning)
        assertEquals(2, calls)
    }
    @Test fun offlineFailuresAreNotCached() {
        var reads = 0
        val service = WordLookupService({ reads++; if (reads == 1) throw IOException("storage") else DictionaryResult(meaning(it), "offline") }, { key, _ -> meaning(key) })
        assertEquals(LookupFailure.OfflineUnavailable, service.resolve("coffee", emptyMap()).failure)
        assertNotNull(service.resolve("coffee", emptyMap()).meaning)
        assertEquals(2, reads)
    }
    @Test fun invalidTranslationsAreNotSavedInMemory() {
        var calls = 0
        val service = WordLookupService({ missing() }, { key, _ -> calls++; WordMeaning(key, "unchanged latin") })
        repeat(2) { assertEquals(LookupFailure.InvalidResponse, service.resolve("absent", emptyMap(), WordLookupRequest.Online).failure) }
        assertNull(service.resolve("absent", emptyMap()).meaning)
        assertEquals(2, calls)
    }
    @Test fun cancelledNetworkResultIsNotCachedOrConvertedToAnError() {
        var calls = 0
        val service = WordLookupService({ missing() }, { key, _ -> calls++; meaning(key) })
        var checks = 0
        assertThrows(CancellationException::class.java) {
            service.resolve("absent", emptyMap(), WordLookupRequest.Online) { if (++checks == 2) throw CancellationException() }
        }
        assertNull(service.resolve("absent", emptyMap()).meaning)
        assertNotNull(service.resolve("absent", emptyMap(), WordLookupRequest.Online).meaning)
        assertEquals(2, calls)
    }
    @Test fun offlineCancellationPropagatesAndDoesNotCacheAnEntry() {
        var calls = 0
        val service = WordLookupService({ calls++; DictionaryResult(meaning(it), "offline") }, { key, _ -> meaning(key) })
        var checks = 0
        assertThrows(CancellationException::class.java) {
            service.resolve("coffee", emptyMap()) { if (++checks == 2) throw CancellationException() }
        }
        service.resolve("coffee", emptyMap())
        assertEquals(2, calls)
    }
    @Test fun leastRecentlyUsedOfflineEntriesAreEvicted() {
        var calls = 0
        val service = WordLookupService({ calls++; DictionaryResult(meaning(it), "offline") }, { key, _ -> meaning(key) }, offlineCapacity = 2)
        for (key in listOf("alpha", "beta", "alpha", "gamma", "beta")) service.resolve(key, emptyMap())
        assertEquals(4, calls)
    }
    @Test fun leastRecentlyUsedOnlineEntriesAreEvicted() {
        var calls = 0
        val service = WordLookupService({ missing() }, { key, _ -> calls++; meaning(key) }, onlineCapacity = 2)
        for (key in listOf("alpha", "beta", "alpha", "gamma", "beta")) service.resolve(key, emptyMap(), WordLookupRequest.Online)
        assertEquals(4, calls)
    }
    @Test fun dictionaryRefreshCannotReplayAConsumedOnlineIntent() {
        var calls = 0
        val gate = WordLookupRequestGate()
        val service = WordLookupService({ missing() }, { key, _ -> calls++; meaning(key) })
        gate.online()
        service.resolve("absent", emptyMap(), gate.consume())
        val personal = mapOf("absent" to WordMeaning("absent", "تعریف شخصی"))
        val refreshRequest = gate.consume()
        assertEquals(WordLookupRequest.Offline, refreshRequest)
        assertEquals(personal["absent"], service.resolve("absent", personal, refreshRequest).meaning)
        service.resolve("absent", emptyMap(), gate.consume())
        assertEquals(1, calls)
        gate.online(); gate.offline()
        assertEquals(WordLookupRequest.Offline, gate.consume())
    }
    @Test fun failedOnlineIntentIsNotReplayedByLaterRefreshes() {
        var calls = 0
        val gate = WordLookupRequestGate()
        val service = WordLookupService({ missing() }, { _, _ -> calls++; throw OnlineLookupException(LookupFailure.Quota) })
        gate.online()
        assertEquals(LookupFailure.Quota, service.resolve("absent", emptyMap(), gate.consume()).failure)
        repeat(3) { assertNull(service.resolve("absent", emptyMap(), gate.consume()).meaning) }
        assertEquals(1, calls)
        gate.online()
        service.resolve("absent", emptyMap(), gate.consume())
        assertEquals(2, calls)
    }
    @Test fun cachedOnlineMeaningSurvivesTemporaryOfflineIndexFailure() {
        val service = WordLookupService({ throw IOException() }, { key, _ -> meaning(key) })
        service.resolve("absent", emptyMap(), WordLookupRequest.Online)
        val cached = service.resolve("absent", emptyMap())
        assertNotNull(cached.meaning)
        assertNull(cached.failure)
    }
    @Test fun quotaFailureStaysOutOfTheCache() {
        var calls = 0
        val service = WordLookupService({ missing() }, { _, _ -> calls++; throw OnlineLookupException(LookupFailure.Quota) })
        repeat(2) { assertEquals(LookupFailure.Quota, service.resolve("absent", emptyMap(), WordLookupRequest.Online).failure) }
        assertEquals(2, calls)
    }
}
