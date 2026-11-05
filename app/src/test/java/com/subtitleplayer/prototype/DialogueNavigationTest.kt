package com.subtitleplayer.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialogueNavigationTest {
    private val rows = listOf(
        DialogueRange("one", 1000, 2000),
        DialogueRange("two", 3000, 4000),
        DialogueRange("three", 5000, 6000)
    )

    @Test fun nextSkipsCurrentStartAndWorksAcrossSilentGaps() {
        assertEquals(rows[0], adjacentDialogue(rows, 0, true))
        assertEquals(rows[1], adjacentDialogue(rows, 1000, true))
        assertEquals(rows[1], adjacentDialogue(rows, 2500, true))
        assertNull(adjacentDialogue(rows, 5000, true))
    }
    @Test fun previousRewindsCurrentSentenceThenMovesToPreviousNearItsStart() {
        assertEquals(rows[1], adjacentDialogue(rows, 3500, false))
        assertEquals(rows[0], adjacentDialogue(rows, 3100, false))
        assertEquals(rows[0], adjacentDialogue(rows, 3000, false))
        assertNull(adjacentDialogue(rows, 1000, false))
    }
    @Test fun emptyAndClippedSubtitlesHaveValidBoundaries() {
        assertNull(adjacentDialogue(emptyList(), 1000, true))
        assertNull(adjacentDialogue(emptyList(), 1000, false))
        val clipped = listOf(DialogueRange("start", 0, 1000))
        assertNull(adjacentDialogue(clipped, 0, false))
        assertEquals(clipped[0], adjacentDialogue(clipped, 251, false))
    }
}
