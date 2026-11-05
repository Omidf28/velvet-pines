package com.subtitleplayer.prototype

internal fun sleepDeadline(nowElapsedMs: Long, minutes: Int): Long {
    require(nowElapsedMs >= 0 && minutes in 1..120)
    val delay = minutes * 60_000L
    return if (nowElapsedMs > Long.MAX_VALUE - delay) Long.MAX_VALUE else nowElapsedMs + delay
}

internal fun sleepRemainingMs(deadline: Long, nowElapsedMs: Long): Long =
    if (deadline <= nowElapsedMs) 0 else deadline - nowElapsedMs.coerceAtLeast(0)
