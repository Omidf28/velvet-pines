package com.subtitleplayer.prototype

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class SubtitleTimelineTest {
    @Test fun indexedLookupMatchesLinearOracleForOverlapsAndRandomSeeks() {
        val random = Random(42)
        val cues = List(2000) {
            val start = random.nextLong(100000)
            SubtitleCue(start, start + random.nextLong(1, 20000), "cue$it")
        }.sortedBy { it.startMs }
        val timeline = SubtitleTimeline(cues)
        repeat(500) {
            val position = random.nextLong(-1000, 125000)
            assertEquals(activeSubtitle(cues, position), timeline.activeAt(position))
        }
    }
    @Test fun respectsExclusiveEndsAndStableOrderAtEqualStarts() {
        val timeline = SubtitleTimeline(listOf(SubtitleCue(10, 20, "a"), SubtitleCue(10, 30, "b")))
        assertNull(timeline.activeAt(9))
        assertEquals(ActiveSubtitle("a\nb", 10), timeline.activeAt(10))
        assertEquals(ActiveSubtitle("b", 10), timeline.activeAt(20))
        assertNull(timeline.activeAt(30))
    }
    @Test fun handlesEmptyUnsortedAndLongNestedCues() {
        assertNull(SubtitleTimeline(emptyList()).activeAt(0))
        val cues = listOf(SubtitleCue(100, 200, "short"), SubtitleCue(0, 10000, "long"))
        assertEquals(ActiveSubtitle("long\nshort", 0), SubtitleTimeline(cues).activeAt(150))
    }
    @Test fun replayUsesTheFirstVisibleCueEvenWhenCuesOverlap() {
        val first = SubtitleCue(100, 400, "first")
        val second = SubtitleCue(100, 600, "second")
        val later = SubtitleCue(300, 500, "later")
        val timeline = SubtitleTimeline(listOf(later, first, second))

        assertNull(timeline.firstActiveCueAt(99))
        assertEquals(first, timeline.firstActiveCueAt(350))
        assertEquals(second, timeline.firstActiveCueAt(400))
        assertNull(timeline.firstActiveCueAt(600))
    }
    @Test fun supportsLongSubtitleFilesAndBackwardSeeks() {
        val cues = List(20000) { SubtitleCue(it * 1000L, it * 1000L + 900, "line$it") }
        val timeline = SubtitleTimeline(cues)
        assertEquals(ActiveSubtitle("line19999", 19999000), timeline.activeAt(19999500))
        assertEquals(ActiveSubtitle("line1", 1000), timeline.activeAt(1000))
        assertNull(timeline.activeAt(19999900))
    }
}
