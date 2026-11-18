package com.subtitleplayer.prototype

internal enum class DictionaryLevel { B1, B2, C1, C2 }

internal data class LearnerProfile(
    val level: String,
    val partOfSpeech: String,
    val source: String
)
