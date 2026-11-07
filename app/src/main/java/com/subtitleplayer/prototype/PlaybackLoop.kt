package com.subtitleplayer.prototype

internal const val minimumPlaybackLoopMs = 500L

internal data class PlaybackLoop(val startMs: Long, val endMs: Long)

internal fun playbackLoop(startMs: Long, endMs: Long, durationMs: Long): PlaybackLoop? {
    if (durationMs <= 0 || startMs < 0 || startMs >= durationMs || endMs > durationMs) return null
    if (endMs <= startMs || endMs - startMs < minimumPlaybackLoopMs) return null
    return PlaybackLoop(startMs, endMs)
}

internal fun PlaybackLoop.seekTarget(positionMs: Long): Long? =
    startMs.takeIf { positionMs < startMs || positionMs >= endMs }
