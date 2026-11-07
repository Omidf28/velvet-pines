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

    /** Return true from the visitor to stop at the first required cue. */
    private fun visitActive(positionMs: Long, visit: (SubtitleCue) -> Boolean) {
        if (cues.isEmpty()) return
        fun collect(node: Int, left: Int, right: Int): Boolean {
            if (maxEnd[node] <= positionMs || cues[left].startMs > positionMs) return false
            if (left == right) return visit(cues[left])
            val middle = (left + right) / 2
            return collect(node * 2, left, middle) || collect(node * 2 + 1, middle + 1, right)
        }
        collect(1, 0, cues.lastIndex)
    }

    fun firstActiveCueAt(positionMs: Long): SubtitleCue? {
        var first: SubtitleCue? = null
        visitActive(positionMs) {
            first = it
            true
        }
        return first
    }

    /** Map offsets in the displayed, newline-joined text back to its source cue. */
    fun cueForSelectionAt(positionMs: Long, start: Int, endExclusive: Int): SubtitleCue? {
        if (start < 0 || endExclusive <= start) return null
        var offset = 0
        var selected: SubtitleCue? = null
        visitActive(positionMs) { cue ->
            val cueEnd = offset + cue.text.length
            if (start < cueEnd) {
                if (start >= offset && endExclusive <= cueEnd) selected = cue
                true
            } else {
                offset = cueEnd + 1
                false
            }
        }
        return selected
    }
    fun activeAt(positionMs: Long): ActiveSubtitle? {
        val visible = mutableListOf<SubtitleCue>()
        visitActive(positionMs) {
            visible.add(it)
            false
        }
        val first = visible.firstOrNull() ?: return null
        return ActiveSubtitle(visible.joinToString("\n") { it.text }, first.startMs)
    }
}
