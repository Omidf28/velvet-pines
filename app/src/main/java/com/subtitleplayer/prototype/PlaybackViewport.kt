package com.subtitleplayer.prototype

import java.io.Serializable

internal enum class PlaybackOrientation { Automatic, Portrait, Landscape }

/** Window policy, independent of Android configuration delivery and playback position. */
internal data class PlaybackViewport(
    val orientation: PlaybackOrientation = PlaybackOrientation.Automatic,
    val lockedOrientation: PlaybackOrientation? = null
) : Serializable {
    val locked: Boolean get() = lockedOrientation != null

    fun toggleFullscreen(landscape: Boolean): PlaybackViewport =
        if (locked) this else copy(orientation = if (immersive(landscape))
            PlaybackOrientation.Portrait else PlaybackOrientation.Landscape)

    fun exitFullscreen(): PlaybackViewport =
        copy(orientation = PlaybackOrientation.Portrait, lockedOrientation = null)

    fun lock(landscape: Boolean): PlaybackViewport = copy(
        lockedOrientation = if (immersive(landscape)) PlaybackOrientation.Landscape else PlaybackOrientation.Portrait
    )

    fun unlock(): PlaybackViewport = copy(lockedOrientation = null)

    fun immersive(landscape: Boolean): Boolean = when (lockedOrientation ?: orientation) {
        PlaybackOrientation.Automatic -> landscape
        PlaybackOrientation.Portrait -> false
        PlaybackOrientation.Landscape -> true
    }

    /** PiP and non-player screens never force the host Activity's orientation. */
    fun requestedOrientation(playerVisible: Boolean, pictureInPicture: Boolean): PlaybackOrientation =
        if (playerVisible && !pictureInPicture) lockedOrientation ?: orientation else PlaybackOrientation.Automatic
}
