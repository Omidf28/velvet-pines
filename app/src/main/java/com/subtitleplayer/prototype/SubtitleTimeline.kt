package com.subtitleplayer.prototype

/** Indexed interval lookup; inactive branches are skipped even for overlapping cues. */
internal class SubtitleTimeline(source: List<SubtitleCue>) {
    private val cues = source.sortedBy { it.startMs }
    private val maxEnd = LongArray(maxOf(1, cues.size * 4))
    init { if (cues.isNotEmpty()) build(1, 0, cues.lastIndex) }

    private fun build(node: Int, left: Int, right: Int): Long {
        val end = if (left == right) cues[left].endMs else {
            val middle = (left + right) / 2
            maxOf(build(node * 2, left, middle), build(node * 2 + 1, middle + 1, right))
        }
        maxEnd[node] = end
        return end
    }

    fun activeAt(positionMs: Long): ActiveSubtitle? {
        if (cues.isEmpty()) return null
        val visible = mutableListOf<SubtitleCue>()
        fun collect(node: Int, left: Int, right: Int) {
            if (maxEnd[node] <= positionMs || cues[left].startMs > positionMs) return
            if (left == right) { visible.add(cues[left]); return }
            val middle = (left + right) / 2
            collect(node * 2, left, middle)
            collect(node * 2 + 1, middle + 1, right)
        }
        collect(1, 0, cues.lastIndex)
        val first = visible.firstOrNull() ?: return null
        return ActiveSubtitle(visible.joinToString("\n") { it.text }, first.startMs)
    }
}
