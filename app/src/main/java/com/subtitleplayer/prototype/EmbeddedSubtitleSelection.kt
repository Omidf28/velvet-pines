package com.subtitleplayer.prototype

import java.util.Locale

internal data class EmbeddedSubtitleCandidate(
    val key: String,
    val language: String?,
    val forced: Boolean = false,
    val default: Boolean = false
) {
    val english: Boolean get() = language?.trim()?.lowercase(Locale.ROOT)
        ?.replace('_', '-')?.substringBefore('-') in setOf("en", "eng")
}

/** Automatic discovery and explicit user intent remain separate even when no track is available. */
internal data class EmbeddedSubtitleSelection(
    val automaticEnglish: Boolean = true,
    val key: String? = null
) {
    fun manual(key: String?): EmbeddedSubtitleSelection = EmbeddedSubtitleSelection(false, key)
    fun automatic(): EmbeddedSubtitleSelection = copy(automaticEnglish = true)

    fun refresh(
        candidates: List<EmbeddedSubtitleCandidate>,
        externalFileSelected: Boolean = false
    ): EmbeddedSubtitleSelection {
        if (externalFileSelected) return EmbeddedSubtitleSelection(false)
        if (!automaticEnglish) return this
        val preferred = candidates.filter { it.english }.minWithOrNull(
            compareBy<EmbeddedSubtitleCandidate> { it.forced }
                .thenByDescending { it.default }
                .thenByDescending { it.key == key }
        )
        return copy(key = preferred?.key)
    }
}
