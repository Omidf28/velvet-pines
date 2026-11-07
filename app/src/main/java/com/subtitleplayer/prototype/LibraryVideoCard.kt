package com.subtitleplayer.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

/** File artwork is deliberately generic; it does not pretend to be a frame from the video. */
@Composable
private fun LibraryArtwork(name: String) {
    Box(Modifier.size(48.dp).background(
        Brush.linearGradient(listOf(PinesPalette.AccentContainer, PinesPalette.Card)),
        RoundedCornerShape(10.dp))) {
        Canvas(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(16.dp)) {
            drawPath(Path().apply {
                moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2)
                lineTo(size.width * 0.2f, size.height); close()
            }, PinesPalette.Accent)
        }
        Text(name.substringAfterLast('.', "VIDEO").take(6).uppercase(Locale.ROOT),
            Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp), color = PinesPalette.Muted,
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LibraryVideoCard(
    video: LibraryVideo,
    showParentPath: Boolean,
    onOpen: () -> Unit,
    onRestart: () -> Unit
) {
    var menuOpen by remember(video.uri) { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PinesPalette.Card.copy(alpha = 0.94f))) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryArtwork(video.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr))
                if (showParentPath) video.parentPath?.let {
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = PinesPalette.Muted,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr))
                }
                val bookmark = video.bookmark
                val label = when {
                    bookmark?.completed == true -> "Watched · Replay"
                    bookmark?.resumable == true -> "Resume at " + formatTime(bookmark.resumePositionMs)
                    else -> null
                }
                if (label != null) {
                    Text(label, color = if (bookmark?.completed == true) PinesPalette.Success else PinesPalette.Muted,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                bookmark?.progress?.let { fraction ->
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = PinesPalette.Accent, trackColor = PinesPalette.AccentContainer.copy(alpha = 0.5f))
                }
            }
            if (video.bookmark?.resumable == true) {
                Box {
                    PlayerIconButton(PlayerGlyph.More, "Options for ${video.name}", onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Play from beginning") }, onClick = {
                            menuOpen = false
                            onRestart()
                        })
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContinueWatchingCard(video: LibraryVideo, onOpen: () -> Unit) {
    val bookmark = video.bookmark ?: return
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().glassPanel(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Continue watching", color = PinesPalette.Accent, style = MaterialTheme.typography.labelLarge)
            Text(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Resume at", color = PinesPalette.Ink, style = MaterialTheme.typography.bodySmall)
                Text(formatTime(bookmark.resumePositionMs) +
                    if (bookmark.durationMs > 0) " / " + formatTime(bookmark.durationMs) else "",
                    color = PinesPalette.Ink, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
            }
            bookmark.progress?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(),
                    color = PinesPalette.Accent, trackColor = PinesPalette.Night.copy(alpha = 0.45f))
            }
        }
    }
}
