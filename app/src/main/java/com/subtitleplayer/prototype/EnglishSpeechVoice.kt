package com.subtitleplayer.prototype

internal data class EnglishSpeechVoice(
    val name: String,
    val language: String,
    val country: String,
    val requiresNetwork: Boolean,
    val requiresDownload: Boolean,
    val quality: Int,
    val latency: Int
)

/** Only installed English voices that the engine advertises as local are eligible. */
internal fun chooseEnglishSpeechVoice(voices: List<EnglishSpeechVoice>): EnglishSpeechVoice? =
    voices.filter {
        it.language.equals("en", ignoreCase = true) && !it.requiresNetwork && !it.requiresDownload
    }.sortedWith(
        compareBy<EnglishSpeechVoice> {
            when (it.country.uppercase(java.util.Locale.ROOT)) {
                "US" -> 0
                "GB" -> 1
                else -> 2
            }
        }.thenByDescending { it.quality }.thenBy { it.latency }.thenBy { it.name }
    ).firstOrNull()
