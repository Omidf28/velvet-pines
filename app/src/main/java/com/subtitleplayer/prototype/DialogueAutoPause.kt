package com.subtitleplayer.prototype

internal fun dialoguePauseBoundaries(ranges: List<DialogueRange>): List<Long> {
    val ends = mutableListOf<Long>()
    ranges.sortedBy { it.startMs }.forEach { range ->
        if (range.endMs <= range.startMs) return@forEach
        if (ends.isNotEmpty() && range.startMs < ends.last()) {
            ends[ends.lastIndex] = maxOf(ends.last(), range.endMs)
        } else ends.add(range.endMs)
    }
    return ends
}

internal fun crossedDialogueEnd(ends: List<Long>, previousMs: Long?, positionMs: Long): Long? {
    val previous = previousMs ?: return null
    if (positionMs <= previous) return null
    val match = ends.binarySearch(previous)
    val next = if (match >= 0) match + 1 else -match - 1
    return ends.getOrNull(next)?.takeIf { it <= positionMs }
}
