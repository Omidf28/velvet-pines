package com.subtitleplayer.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialogueAutoPauseTest {
    @Test fun overlappingCuesPauseAfterTheWholeVisibleGroup() {
        assertEquals(listOf(4000L, 5000L), dialoguePauseBoundaries(listOf(
            DialogueRange("b", 2000, 4000), DialogueRange("a", 1000, 3000),
            DialogueRange("next", 4000, 5000)
        )))
    }
    @Test fun detectsCrossingAndDoesNotRepeatAfterResume() {
        val ends = listOf(1000L, 2000L)
        assertNull(crossedDialogueEnd(ends, 900, 999))
        assertEquals(1000L, crossedDialogueEnd(ends, 999, 1000))
        assertNull(crossedDialogueEnd(ends, 1000, 1001))
        assertEquals(2000L, crossedDialogueEnd(ends, 1000, 2050))
    }
    @Test fun initialPositionAndBackwardMovementDoNotPause() {
        assertNull(crossedDialogueEnd(listOf(1000), null, 1500))
        assertNull(crossedDialogueEnd(listOf(1000), 1500, 500))
        assertNull(crossedDialogueEnd(emptyList(), 0, 5000))
    }
    @Test fun overshootFindsFirstBoundaryAndInvalidRangesAreIgnored() {
        assertEquals(1000L, crossedDialogueEnd(listOf(1000, 2000), 0, 5000))
        assertEquals(emptyList<Long>(), dialoguePauseBoundaries(listOf(DialogueRange("bad", 1000, 500))))
    }
}
