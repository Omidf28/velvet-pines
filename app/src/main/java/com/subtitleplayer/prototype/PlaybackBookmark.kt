package com.subtitleplayer.prototype

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal data class PlaybackBookmark(val positionMs: Long, val durationMs: Long, val completed: Boolean) {
    val resumePositionMs: Long get() = if (completed) 0 else positionMs.coerceAtLeast(0)
    val progress: Float? get() = when {
        completed -> 1f
        durationMs <= 0 -> null
        else -> (positionMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    }
    val resumable: Boolean get() = !completed && resumePositionMs > 0
}

internal fun capturePlaybackBookmark(positionMs: Long, durationMs: Long, ended: Boolean): PlaybackBookmark {
    val duration = durationMs.coerceAtLeast(0)
    val position = positionMs.coerceAtLeast(0)
    val completed = ended || (duration > 0 && position >= duration)
    return PlaybackBookmark(if (completed) 0 else position, duration, completed)
}

/** Preserve an incoming movie's bookmark if recording the outgoing movie evicts a history entry. */
internal fun resumePositionForOpen(explicit: Long?, latest: PlaybackBookmark?, prior: PlaybackBookmark?): Long =
    explicit?.coerceAtLeast(0) ?: (latest ?: prior)?.resumePositionMs ?: 0L

internal fun PlaybackBookmark.statusLabel(): String? = when {
    completed -> "Watched · Replay"
    resumable -> "Resume at " + formatTime(resumePositionMs)
    else -> null
}

/** Stream links can expire, so checkpoints are scoped to the current player session. */
internal class StreamPlaybackBookmarks(private val limit: Int = 32) {
    init { require(limit > 0) }
    private var entries by mutableStateOf<List<Pair<String, PlaybackBookmark>>>(emptyList())

    operator fun get(url: String): PlaybackBookmark? {
        val key = remoteVideoId(url)
        return entries.firstOrNull { it.first == key }?.second
    }

    fun record(url: String, bookmark: PlaybackBookmark) {
        val key = remoteVideoId(url)
        entries = (entries.filterNot { it.first == key } + (key to bookmark)).takeLast(limit)
    }
}
