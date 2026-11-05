package com.subtitleplayer.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerTest {
    @Test fun measuresElapsedTimeRatherThanVideoPosition() {
        val deadline = sleepDeadline(1000, 15)
        assertEquals(901000L, deadline)
        assertEquals(840000L, sleepRemainingMs(deadline, 61000))
    }
    @Test fun expiresAtBoundaryAndAfterDelayedWakeup() {
        assertEquals(1L, sleepRemainingMs(1000, 999))
        assertEquals(0L, sleepRemainingMs(1000, 1000))
        assertEquals(0L, sleepRemainingMs(1000, 9000))
    }
    @Test fun deadlineDoesNotOverflow() {
        assertEquals(Long.MAX_VALUE, sleepDeadline(Long.MAX_VALUE - 1, 60))
    }
}
