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
    Box(Modifier.size(width = 64.dp, height = 76.dp).background(
        Brush.linearGradient(listOf(PinesPalette.Curtain, PinesPalette.Card, Color(0xFF18231C))),
        RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            drawPath(Path().apply {
                moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2)
                lineTo(size.width * 0.2f, size.height); close()
            }, PinesPalette.Cream)
        }
        Text(name.substringAfterLast('.', "VIDEO").take(6).uppercase(Locale.ROOT),
            Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp), color = PinesPalette.Muted,
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun LibraryVideoCard(
    video: LibraryVideo,
    onOpen: () -> Unit,
    onRestart: () -> Unit
) {
    var menuOpen by remember(video.uri) { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PinesPalette.Card.copy(alpha = 0.94f))) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LibraryArtwork(video.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr))
                video.parentPath?.let {
                    Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, color = PinesPalette.Muted,
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr))
                }
                val bookmark = video.bookmark
                val label = when {
                    bookmark?.completed == true -> "تماشا شد · پخش دوباره"
                    bookmark?.resumable == true -> "ادامه از " + formatTime(bookmark.resumePositionMs)
                    else -> "پخش"
                }
                Text(label, color = if (bookmark?.completed == true) PinesPalette.Forest else PinesPalette.Muted,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                bookmark?.progress?.let { fraction ->
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = PinesPalette.Rose, trackColor = PinesPalette.Curtain.copy(alpha = 0.5f))
                }
            }
            if (video.bookmark?.resumable == true) {
                Box {
                    PlayerIconButton(PlayerGlyph.More, "گزینه‌های ${video.name}", onClick = { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("پخش از ابتدا") }, onClick = {
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
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PinesPalette.Curtain.copy(alpha = 0.70f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ادامهٔ تماشا", color = PinesPalette.Rose, style = MaterialTheme.typography.labelLarge)
            Text(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ادامه از", color = PinesPalette.Cream, style = MaterialTheme.typography.bodySmall)
                Text(formatTime(bookmark.resumePositionMs) +
                    if (bookmark.durationMs > 0) " / " + formatTime(bookmark.durationMs) else "",
                    color = PinesPalette.Cream, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr))
            }
            bookmark.progress?.let { fraction ->
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(),
                    color = PinesPalette.Rose, trackColor = PinesPalette.Night.copy(alpha = 0.45f))
            }
        }
    }
}
