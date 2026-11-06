package com.subtitleplayer.prototype

import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal const val videoSubtitleSettingsLimit = 20

internal data class VideoSubtitleSettings(
    val english: Uri? = null,
    val englishDelayMs: Long = 0,
    val persian: Uri? = null,
    val persianDelayMs: Long = 0,
    val embeddedTrack: String? = null,
    val automaticEnglish: Boolean = false,
    val automaticSidecars: Boolean = false,
    val size: Int = 22
)

internal data class SavedVideoSubtitleSettings(val video: Uri, val subtitles: VideoSubtitleSettings)

internal fun videoSubtitleKey(uri: Uri): String {
    val authority = uri.authority ?: return uri.toString()
    if (uri.scheme != "content") return uri.toString()
    val id = try {
        DocumentsContract.getDocumentId(uri)
    } catch (_: IllegalArgumentException) {
        return uri.toString()
    }
    return DocumentsContract.buildDocumentUri(authority, id).toString()
}

internal fun readVideoSubtitleSettings(encoded: String): List<SavedVideoSubtitleSettings> {
    val array = try {
        JSONArray(encoded)
    } catch (_: JSONException) {
        return emptyList()
    }
    return (0 until minOf(array.length(), videoSubtitleSettingsLimit)).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val video = item.optString("video").takeIf(String::isNotBlank)?.let(Uri::parse)
            ?: return@mapNotNull null
        SavedVideoSubtitleSettings(
            video,
            VideoSubtitleSettings(
                english = item.optString("english").takeIf(String::isNotBlank)?.let(Uri::parse),
                englishDelayMs = item.optLong("englishDelay"),
                persian = item.optString("persian").takeIf(String::isNotBlank)?.let(Uri::parse),
                persianDelayMs = item.optLong("persianDelay"),
                embeddedTrack = item.optString("embeddedTrack").takeIf(String::isNotBlank),
                automaticEnglish = item.optBoolean("automaticEnglish", false),
                automaticSidecars = item.optBoolean("automaticSidecars", false),
                size = item.optInt("size", 22).coerceIn(16, 32)
            )
        )
    }.distinctBy { videoSubtitleKey(it.video) }
}

internal fun writeVideoSubtitleSettings(profiles: List<SavedVideoSubtitleSettings>): String =
    JSONArray().apply {
        profiles.forEach { profile ->
            val settings = profile.subtitles
            put(
                JSONObject()
                    .put("video", profile.video.toString())
                    .put("english", settings.english?.toString().orEmpty())
                    .put("englishDelay", settings.englishDelayMs)
                    .put("persian", settings.persian?.toString().orEmpty())
                    .put("persianDelay", settings.persianDelayMs)
                    .put("embeddedTrack", settings.embeddedTrack.orEmpty())
                    .put("automaticEnglish", settings.automaticEnglish)
                    .put("automaticSidecars", settings.automaticSidecars)
                    .put("size", settings.size)
            )
        }
    }.toString()
