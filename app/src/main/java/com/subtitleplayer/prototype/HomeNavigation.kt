package com.subtitleplayer.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeNavigation(showingWords: Boolean, onFiles: () -> Unit, onWords: () -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = PinesPalette.Accent,
        selectedTextColor = PinesPalette.Ink,
        indicatorColor = PinesPalette.AccentContainer,
        unselectedIconColor = PinesPalette.Muted,
        unselectedTextColor = PinesPalette.Muted
    )
    NavigationBar(
        modifier = Modifier.border(1.dp, PinesPalette.Border),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        NavigationBarItem(
            selected = !showingWords,
            onClick = onFiles,
            icon = { HomeNavigationIcon(folder = true) },
            label = { Text("Files") },
            colors = colors
        )
        NavigationBarItem(
            selected = showingWords,
            onClick = onWords,
            icon = { HomeNavigationIcon(folder = false) },
            label = { Text("Flashcards") },
            colors = colors
        )
    }
}

@Composable
internal fun HomeNavigationIcon(folder: Boolean, ink: Color = LocalContentColor.current) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.7f * u, join = StrokeJoin.Round)
        if (folder) {
            drawPath(Path().apply {
                moveTo(3 * u, 6 * u)
                lineTo(9 * u, 6 * u)
                lineTo(12 * u, 9 * u)
                lineTo(21 * u, 9 * u)
                lineTo(21 * u, 20 * u)
                lineTo(3 * u, 20 * u)
                close()
            }, ink, style = stroke)
        } else {
            drawRoundRect(ink, Offset(6 * u, 7 * u), Size(15 * u, 13 * u), CornerRadius(2 * u), style = stroke)
            drawPath(Path().apply {
                moveTo(3 * u, 16 * u)
                lineTo(3 * u, 4 * u)
                lineTo(17 * u, 4 * u)
            }, ink, style = stroke)
            drawLine(ink, Offset(10 * u, 12 * u), Offset(17 * u, 12 * u), 1.7f * u)
            drawLine(ink, Offset(10 * u, 16 * u), Offset(15 * u, 16 * u), 1.7f * u)
        }
    }
}
