package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test

class PlaybackBookmarkTest {
    @Test fun aPartialMovieResumesAtItsRecordedPosition() {
        val bookmark = capturePlaybackBookmark(34_000, 120_000, false)
        assertEquals(34_000L, bookmark.resumePositionMs)
        assertTrue(bookmark.resumable)
        assertFalse(bookmark.completed)
        assertEquals(34f / 120f, bookmark.progress!!, 0.00001f)
    }

    @Test fun aNewlyStartedMovieHasKnownZeroProgressWithoutAContinueAction() {
        val bookmark = capturePlaybackBookmark(0, 120_000, false)
        assertEquals(0L, bookmark.resumePositionMs)
        assertFalse(bookmark.resumable)
        assertEquals(0f, bookmark.progress!!, 0f)
    }

    @Test fun reachingTheActualEndRecordsCompletionAndReplaysFromTheStart() {
        val bookmark = capturePlaybackBookmark(120_000, 120_000, true)
        assertTrue(bookmark.completed)
        assertFalse(bookmark.resumable)
        assertEquals(0L, bookmark.resumePositionMs)
        assertEquals(1f, bookmark.progress!!, 0f)
    }

    @Test fun creditsNearTheEndAreNotAutomaticallyTreatedAsFinished() {
        val bookmark = capturePlaybackBookmark(119_000, 120_000, false)
        assertFalse(bookmark.completed)
        assertEquals(119_000L, bookmark.resumePositionMs)
    }

    @Test fun anExactEndPositionWithoutTheEndedCallbackStillReplaysSafely() {
        assertEquals(0L, capturePlaybackBookmark(120_000, 120_000, false).resumePositionMs)
    }

    @Test fun positionsBeyondAKnownDurationCannotProduceAnInvalidResumeSeek() {
        val bookmark = capturePlaybackBookmark(Long.MAX_VALUE, 120_000, false)
        assertTrue(bookmark.completed)
        assertEquals(0L, bookmark.resumePositionMs)
    }

    @Test fun negativePositionAndUnsetDurationAreNormalized() {
        val bookmark = capturePlaybackBookmark(-1, Long.MIN_VALUE, false)
        assertEquals(0L, bookmark.positionMs)
        assertEquals(0L, bookmark.durationMs)
        assertFalse(bookmark.completed)
        assertNull(bookmark.progress)
    }

    @Test fun unknownDurationRetainsAValidResumeTimeWithoutInventingAPercentage() {
        val bookmark = capturePlaybackBookmark(50_000, 0, false)
        assertEquals(50_000L, bookmark.resumePositionMs)
        assertTrue(bookmark.resumable)
        assertNull(bookmark.progress)
    }

    @Test fun anEndedUnknownLengthSourceCanStillBeMarkedCompleted() {
        val bookmark = capturePlaybackBookmark(50_000, Long.MIN_VALUE, true)
        assertTrue(bookmark.completed)
        assertEquals(0L, bookmark.resumePositionMs)
        assertEquals(1f, bookmark.progress!!, 0f)
    }

    @Test fun explicitlyStartingOverReplacesThePreviousCompletionState() {
        val watched = capturePlaybackBookmark(120_000, 120_000, true)
        val restarted = capturePlaybackBookmark(0, watched.durationMs, false)
        assertFalse(restarted.completed)
        assertFalse(restarted.resumable)
        assertEquals(0f, restarted.progress!!, 0f)
    }

    @Test fun veryLongDurationsDoNotOverflowPercentageCalculations() {
        val bookmark = capturePlaybackBookmark(Long.MAX_VALUE / 2, Long.MAX_VALUE, false)
        assertEquals(0.5f, bookmark.progress!!, 0.00001f)
        assertTrue(bookmark.resumePositionMs > 0)
    }

    @Test fun reopeningAPersistedSnapshotPreservesItsResumeAndCompletionMeaning() {
        listOf(capturePlaybackBookmark(42_000, 120_000, false), capturePlaybackBookmark(120_000, 120_000, true)).forEach {
            assertEquals(it, capturePlaybackBookmark(it.positionMs, it.durationMs, it.completed))
        }
    }
    @Test fun explicitStartOverOverridesBothStoredCheckpoints() {
        assertEquals(0L, resumePositionForOpen(0, capturePlaybackBookmark(90, 100, false), capturePlaybackBookmark(80, 100, false)))
    }

    @Test fun theLatestCheckpointWinsOverThePreOpenSnapshot() {
        assertEquals(90L, resumePositionForOpen(null, capturePlaybackBookmark(90, 100, false), capturePlaybackBookmark(80, 100, false)))
    }

    @Test fun recordingAnotherMovieCannotEraseTheIncomingResumePoint() {
        val incoming = capturePlaybackBookmark(80, 100, false)
        assertEquals(80L, resumePositionForOpen(null, null, incoming))
    }

    @Test fun aCompletedLatestCheckpointDoesNotReviveAnOlderPartialPosition() {
        assertEquals(0L, resumePositionForOpen(null, capturePlaybackBookmark(100, 100, true), capturePlaybackBookmark(80, 100, false)))
    }

    @Test fun missingOrInvalidExplicitPositionsStartSafely() {
        assertEquals(0L, resumePositionForOpen(null, null, null))
        assertEquals(0L, resumePositionForOpen(-1, capturePlaybackBookmark(80, 100, false), null))
    }

    @Test fun streamCheckpointsAreSeparateAndCompletionClearsTheResumePosition() {
        val bookmarks = StreamPlaybackBookmarks()
        val first = "https://media.example/episode-1.mp4?token=one"
        val second = "https://media.example/episode-2.mp4"
        bookmarks.record(first, capturePlaybackBookmark(30, 100, false))
        bookmarks.record(second, capturePlaybackBookmark(60, 100, false))
        assertEquals(30L, bookmarks[first]?.resumePositionMs)
        assertEquals(60L, bookmarks[second]?.resumePositionMs)
        assertNull(bookmarks["https://media.example/episode-1.mp4?token=two"])
        bookmarks.record(first, capturePlaybackBookmark(100, 100, true))
        assertEquals(0L, bookmarks[first]?.resumePositionMs)
        assertNull(StreamPlaybackBookmarks()[first])
    }

    @Test fun streamCheckpointHistoryKeepsTheMostRecentlyUpdatedEntries() {
        val bookmarks = StreamPlaybackBookmarks(limit = 2)
        val first = "https://media.example/first.mp4"
        val second = "https://media.example/second.mp4"
        val third = "https://media.example/third.mp4"
        bookmarks.record(first, capturePlaybackBookmark(10, 100, false))
        bookmarks.record(second, capturePlaybackBookmark(20, 100, false))
        bookmarks.record(first, capturePlaybackBookmark(30, 100, false))
        bookmarks.record(third, capturePlaybackBookmark(40, 100, false))
        assertEquals(30L, bookmarks[first]?.resumePositionMs)
        assertNull(bookmarks[second])
        assertEquals(40L, bookmarks[third]?.resumePositionMs)
    }
}
