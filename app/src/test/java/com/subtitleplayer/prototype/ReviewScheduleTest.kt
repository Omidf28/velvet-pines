package com.subtitleplayer.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewScheduleTest {
    private val day = 86_400_000L

    @Test
    fun firstSuccessIsDueOneDayAfterAnswer() {
        assertEquals(ReviewPlan(1, 12_345L + day, day), planReview(0, true, 12_345L))
    }

    @Test
    fun consecutiveSuccessesUseIncreasingIntervalsAndCapAtThirtyDays() {
        var level = 0
        listOf(1L, 3L, 7L, 14L, 30L, 30L, 30L).forEach { days ->
            val plan = planReview(level, true, day * 100)
            assertEquals(days * day, plan.intervalMs)
            assertEquals((100 + days) * day, plan.dueAt)
            level = plan.level
        }
        assertEquals(5, level)
    }

    @Test
    fun forgettingResetsProgressAndSchedulesTenMinuteRetry() {
        assertEquals(ReviewPlan(0, day + 600_000L, 600_000L), planReview(5, false, day))
        assertEquals(1, planReview(planReview(5, false, day).level, true, day).level)
    }

    @Test
    fun overdueSuccessStartsFromAnswerTime() {
        val now = day * 900
        assertEquals(now + day * 7, planReview(2, true, now).dueAt)
    }

    @Test
    fun outOfRangeLevelsStayWithinSchedule() {
        assertEquals(1, planReview(-10, true, 0).level)
        assertEquals(5, planReview(Int.MAX_VALUE, true, 0).level)
    }

    @Test
    fun dueTimestampDoesNotOverflow() {
        assertEquals(Long.MAX_VALUE, planReview(5, true, Long.MAX_VALUE - 1).dueAt)
    }
}
