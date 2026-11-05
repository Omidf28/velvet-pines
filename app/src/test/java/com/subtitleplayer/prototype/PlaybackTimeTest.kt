package com.subtitleplayer.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeTest {
    @Test fun shortTimesRoundDownToSeconds() {
        assertEquals("0:00", formatTime(0))
        assertEquals("1:02", formatTime(62999))
    }
    @Test fun longVideosUseHoursAndZeroPaddedMinutesAndSeconds() {
        assertEquals("59:59", formatTime(3599999))
        assertEquals("1:00:00", formatTime(3600000))
        assertEquals("12:03:04", formatTime(43384000))
    }
    @Test fun negativeUnknownPositionsDoNotAppearAsNegativeTimes() {
        assertEquals("0:00", formatTime(-1))
        assertEquals("0:00", formatTime(Long.MIN_VALUE))
    }
}
