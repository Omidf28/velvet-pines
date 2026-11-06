package com.subtitleplayer.prototype

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Measure subtitles first so a definition never pushes them or needs a second layout frame. */
@Composable
internal fun SubtitleOverlay(
    topReserved: Dp,
    meaningVisible: Boolean,
    subtitles: @Composable () -> Unit,
    meaning: @Composable () -> Unit
) {
    Layout(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        content = {
            Box { subtitles() }
            Box(Modifier.padding(horizontal = 16.dp)) { if (meaningVisible) meaning() }
        }
    ) { children, constraints ->
        val subtitle = children[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val gap = 8.dp.roundToPx()
        val available = (constraints.maxHeight - subtitle.height - gap - topReserved.roundToPx()).coerceAtLeast(0)
        val card = children[1].measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = available))
        layout(constraints.maxWidth, constraints.maxHeight) {
            subtitle.placeRelative((constraints.maxWidth - subtitle.width) / 2, constraints.maxHeight - subtitle.height)
            if (meaningVisible && available > 0) {
                card.placeRelative((constraints.maxWidth - card.width) / 2,
                    constraints.maxHeight - subtitle.height - gap - card.height)
            }
        }
    }
}
