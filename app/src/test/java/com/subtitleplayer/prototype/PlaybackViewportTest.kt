package com.subtitleplayer.prototype

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class PlaybackViewportTest {
    @Test fun automaticOrientationFollowsTheDevice() {
        val viewport = PlaybackViewport()
        assertFalse(viewport.immersive(landscape = false))
        assertTrue(viewport.immersive(landscape = true))
        assertEquals(PlaybackOrientation.Automatic, viewport.requestedOrientation(true, false))
    }

    @Test fun swipeSeekUsesScreenWidthAndClampsToPlayableTime() {
        assertEquals(45_000L, swipeSeekTarget(30_000, 120_000, 250f, 1000))
        assertEquals(15_000L, swipeSeekTarget(30_000, 120_000, -125f, 500))
        assertEquals(120_000L, swipeSeekTarget(110_000, 120_000, 500f, 1000))
        assertEquals(0L, swipeSeekTarget(10_000, 120_000, -500f, 1000))
    }

    @Test fun fullscreenRequestTakesEffectBeforeTheConfigurationCallback() {
        val viewport = PlaybackViewport().toggleFullscreen(landscape = false)
        assertEquals(PlaybackOrientation.Landscape, viewport.requestedOrientation(true, false))
        assertTrue(viewport.immersive(landscape = false))
        assertTrue(viewport.immersive(landscape = true))
    }

    @Test fun backExitsFullscreenEvenWhileTheDeviceIsHeldSideways() {
        val viewport = PlaybackViewport().toggleFullscreen(false).exitFullscreen()
        assertFalse(viewport.immersive(landscape = true))
        assertEquals(PlaybackOrientation.Portrait, viewport.requestedOrientation(true, false))
        assertTrue(viewport.toggleFullscreen(landscape = true).immersive(landscape = false))
    }

    @Test fun landscapeLockSurvivesDeviceRotationAndRejectsFullscreenActions() {
        val viewport = PlaybackViewport().lock(landscape = true)
        assertTrue(viewport.locked)
        assertTrue(viewport.immersive(landscape = false))
        assertEquals(viewport, viewport.toggleFullscreen(landscape = false))
        assertEquals(PlaybackOrientation.Landscape, viewport.requestedOrientation(true, false))
        assertFalse(viewport.unlock().immersive(landscape = false))
    }

    @Test fun lockingImmediatelyAfterEnteringFullscreenKeepsTheRequestedOrientation() {
        val viewport = PlaybackViewport().toggleFullscreen(landscape = false).lock(landscape = false)
        assertEquals(PlaybackOrientation.Landscape, viewport.requestedOrientation(true, false))
        assertTrue(viewport.immersive(landscape = false))
    }
    @Test fun portraitLockDoesNotAccidentallyEnterFullscreen() {
        val viewport = PlaybackViewport().lock(landscape = false)
        assertFalse(viewport.immersive(landscape = true))
        assertEquals(PlaybackOrientation.Portrait, viewport.requestedOrientation(true, false))
        assertTrue(viewport.unlock().immersive(landscape = true))
    }

    @Test fun unlockingPreservesTheExplicitViewingPreference() {
        val viewport = PlaybackViewport().toggleFullscreen(false).lock(true).unlock()
        assertFalse(viewport.locked)
        assertEquals(PlaybackOrientation.Landscape, viewport.requestedOrientation(true, false))
    }

    @Test fun pictureInPictureReleasesWindowOrientationWithoutLosingTheLock() {
        val viewport = PlaybackViewport().lock(landscape = true)
        assertEquals(PlaybackOrientation.Automatic, viewport.requestedOrientation(true, true))
        assertTrue(viewport.locked)
        assertEquals(PlaybackOrientation.Landscape, viewport.requestedOrientation(true, false))
    }

    @Test fun libraryAndNotebookDoNotInheritForcedOrientation() {
        val viewport = PlaybackViewport().toggleFullscreen(false).lock(true)
        assertEquals(PlaybackOrientation.Automatic, viewport.requestedOrientation(false, false))
        assertEquals(PlaybackOrientation.Automatic, viewport.requestedOrientation(false, true))
    }

    @Test fun savedStateRetainsManualOrientationAndLock() {
        val expected = PlaybackViewport().toggleFullscreen(false).lock(true)
        val bytes = ByteArrayOutputStream().also { out -> ObjectOutputStream(out).use { it.writeObject(expected) } }.toByteArray()
        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as PlaybackViewport }
        assertEquals(expected, restored)
        assertEquals(PlaybackOrientation.Landscape, restored.requestedOrientation(true, false))
        assertFalse(restored.exitFullscreen().locked)
    }
}
