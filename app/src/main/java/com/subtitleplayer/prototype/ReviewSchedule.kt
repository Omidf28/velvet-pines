package com.subtitleplayer.prototype

internal data class ReviewPlan(val level: Int, val dueAt: Long, val intervalMs: Long)

private const val dayMs = 24 * 60 * 60 * 1_000L
private val reviewDays = listOf(1L, 3L, 7L, 14L, 30L)

/** A small, deterministic schedule; forgetting starts the sequence again. */
internal fun planReview(currentLevel: Int, remembered: Boolean, now: Long): ReviewPlan {
    val level = if (remembered) (currentLevel.coerceIn(0, reviewDays.size) + 1)
        .coerceAtMost(reviewDays.size) else 0
    val interval = if (level == 0) 10 * 60 * 1_000L else reviewDays[level - 1] * dayMs
    val start = now.coerceAtLeast(0)
    val dueAt = if (start > Long.MAX_VALUE - interval) Long.MAX_VALUE else start + interval
    return ReviewPlan(level, dueAt, interval)
}
