package com.subtitleplayer.prototype

internal enum class SubtitleLookupMode(val label: String) {
    Words("واژه"), Phrases("عبارت‌های شناخته‌شده")
}

internal fun subtitleLookupModeFrom(value: String?): SubtitleLookupMode =
    SubtitleLookupMode.entries.firstOrNull { it.name == value } ?: SubtitleLookupMode.Words

/** A selection belongs to one displayed cue, not every occurrence of the same word. */
internal data class SubtitleSelection(
    val subtitle: ActiveSubtitle,
    val start: Int,
    val endExclusive: Int
) {
    fun highlightFor(current: ActiveSubtitle): IntRange? {
        if (current != subtitle || start < 0 || endExclusive <= start || endExclusive > current.text.length) return null
        return start until endExclusive
    }
}
