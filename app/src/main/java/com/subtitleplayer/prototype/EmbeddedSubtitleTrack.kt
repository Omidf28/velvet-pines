package com.subtitleplayer.prototype

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal data class EmbeddedSubtitleTrack(val group: Tracks.Group, val index: Int) {
    val key: String get() = "${group.mediaTrackGroup.id}:$index"

    val candidate: EmbeddedSubtitleCandidate get() {
        val format = group.getTrackFormat(index)
        return EmbeddedSubtitleCandidate(key, format.language,
            forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
            default = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0)
    }

    fun displayName(number: Int): String {
        val format = group.getTrackFormat(index)
        val language = format.language?.takeUnless { it == "und" }?.let {
            Locale.forLanguageTag(it).getDisplayLanguage(Locale.forLanguageTag("fa"))
        }
        val name = format.label?.takeIf(String::isNotBlank) ?: language?.takeIf(String::isNotBlank)
        val forced = if (candidate.forced) " · Forced" else ""
        return (if (name == null) "Embedded subtitle $number" else "Embedded subtitle $number · $name") + forced
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun embeddedTextTracks(tracks: Tracks): List<EmbeddedSubtitleTrack> =
    tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.flatMap { group ->
        (0 until group.length).mapNotNull { index ->
            val format = group.getTrackFormat(index)
            // Media3 preserves the original MIME type in codecs when it transcodes subtitle samples.
            val mime = if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) {
                format.codecs
            } else format.sampleMimeType
            when (mime) {
                MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_SSA, MimeTypes.TEXT_VTT ->
                    EmbeddedSubtitleTrack(group, index).takeIf { group.isTrackSupported(index) }
                else -> null
            }
        }
    }
