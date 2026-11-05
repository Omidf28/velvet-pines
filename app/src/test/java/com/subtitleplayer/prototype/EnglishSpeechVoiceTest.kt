package com.subtitleplayer.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnglishSpeechVoiceTest {
    private fun voice(
        name: String,
        country: String = "US",
        language: String = "en",
        network: Boolean = false,
        download: Boolean = false,
        quality: Int = 300,
        latency: Int = 300
    ) = EnglishSpeechVoice(name, language, country, network, download, quality, latency)

    @Test
    fun refusesNetworkVoicesEvenWhenTheyHaveHigherQuality() {
        val local = voice("local", quality = 100)
        assertEquals(local, chooseEnglishSpeechVoice(listOf(voice("online", network = true, quality = 500), local)))
        assertNull(chooseEnglishSpeechVoice(listOf(voice("online", network = true))))
    }

    @Test
    fun doesNotUseUndownloadedVoices() {
        assertNull(chooseEnglishSpeechVoice(listOf(voice("missing", download = true))))
    }

    @Test
    fun doesNotPronounceEnglishWithOtherLanguageVoices() {
        assertNull(chooseEnglishSpeechVoice(listOf(voice("persian", language = "fa"), voice("french", language = "fr"))))
        assertNull(chooseEnglishSpeechVoice(emptyList()))
    }

    @Test
    fun usesAvailableEnglishFallbackAndPrefersUsThenGb() {
        val gb = voice("british", country = "GB")
        val us = voice("american")
        val au = voice("australian", country = "AU")
        assertEquals(au, chooseEnglishSpeechVoice(listOf(au)))
        assertEquals(gb, chooseEnglishSpeechVoice(listOf(au, gb)))
        assertEquals(us, chooseEnglishSpeechVoice(listOf(gb, us, au)))
    }

    @Test
    fun prefersQualityThenLatencyAmongSameLocaleVoices() {
        val best = voice("best", quality = 500, latency = 100)
        assertEquals(best, chooseEnglishSpeechVoice(listOf(voice("basic"), voice("slower", quality = 500), best)))
    }

    @Test
    fun voiceChoiceIsStableRegardlessOfEngineEnumerationOrder() {
        val a = voice("a")
        val b = voice("b")
        assertEquals(a, chooseEnglishSpeechVoice(listOf(a, b)))
        assertEquals(a, chooseEnglishSpeechVoice(listOf(b, a)))
    }
}
