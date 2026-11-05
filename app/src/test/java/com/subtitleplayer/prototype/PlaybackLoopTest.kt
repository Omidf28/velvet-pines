package com.subtitleplayer.prototype

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackLoopTest {
    @Test fun acceptsHalfSecondAndVideoEndBoundaries() {
        assertEquals(PlaybackLoop(0, 500), playbackLoop(0, 500, 500))
        assertEquals(PlaybackLoop(500, 1000), playbackLoop(500, 1000, 1000))
    }
    @Test fun rejectsReversedTinyAndOutOfVideoRanges() {
        listOf(Triple(100L, 599L, 1000L), Triple(600L, 100L, 1000L),
            Triple(-1L, 600L, 1000L), Triple(0L, 1001L, 1000L),
            Triple(0L, 500L, 0L), Triple(1000L, 1500L, 1000L)).forEach {
            assertNull(playbackLoop(it.first, it.second, it.third))
        }
    }
    @Test fun usesExclusiveEndAndRecoversFromOvershoot() {
        val loop = PlaybackLoop(1000, 3000)
        assertNull(loop.seekTarget(1000))
        assertNull(loop.seekTarget(2999))
        assertEquals(1000L, loop.seekTarget(3000))
        assertEquals(1000L, loop.seekTarget(9000))
        assertEquals(1000L, loop.seekTarget(999))
    }
}
