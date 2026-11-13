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

/** Keep checkpoints by the hash of the exact URL so the stored history contains no stream links. */
internal class StreamPlaybackBookmarks(
    private val limit: Int = 32,
    saved: String? = null,
    private val persist: (String) -> Unit = {}
) {
    init { require(limit > 0) }
    private var entries by mutableStateOf(
        saved.orEmpty().lineSequence().mapNotNull(::parseEntry).toList()
            .asReversed().distinctBy { it.first }.take(limit).asReversed()
    )

    operator fun get(url: String): PlaybackBookmark? {
        val key = remoteVideoId(url)
        return entries.firstOrNull { it.first == key }?.second
    }

    fun record(url: String, bookmark: PlaybackBookmark) {
        if (bookmark.durationMs <= 0) return
        val key = remoteVideoId(url)
        val updated = (entries.filterNot { it.first == key } + (key to bookmark)).takeLast(limit)
        if (updated == entries) return
        entries = updated
        persist(updated.joinToString("\n") { (id, progress) ->
            "$id ${progress.positionMs} ${progress.durationMs} ${progress.completed}"
        })
    }

    private fun parseEntry(line: String): Pair<String, PlaybackBookmark>? {
        val fields = line.split(' ', limit = 4)
        if (fields.size != 4 || !streamBookmarkId.matches(fields[0])) return null
        val position = fields[1].toLongOrNull()?.takeIf { it >= 0 } ?: return null
        val duration = fields[2].toLongOrNull()?.takeIf { it > 0 } ?: return null
        val completed = fields[3].toBooleanStrictOrNull() ?: return null
        return fields[0] to capturePlaybackBookmark(position, duration, completed)
    }
}

private val streamBookmarkId = Regex("remote:[A-Za-z0-9_-]{43}")
