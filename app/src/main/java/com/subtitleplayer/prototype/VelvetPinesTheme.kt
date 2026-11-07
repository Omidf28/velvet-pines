package com.subtitleplayer.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object PinesPalette {
    val Night = Color(0xFF100D0F)
    val Card = Color(0xFF211A1C)
    val Curtain = Color(0xFF5A202D)
    val Rose = Color(0xFFE0A1AA)
    val Cream = Color(0xFFE8DDD0)
    val Muted = Color(0xFFBDAFA7)
    val Brass = Color(0xFFCCAC82)
    val Forest = Color(0xFFAAC3AD)
}

private val pinesColors = darkColorScheme(
    primary = PinesPalette.Rose, onPrimary = Color(0xFF3C0B18),
    primaryContainer = Color(0xFF4E1C2A), onPrimaryContainer = Color(0xFFF3CBD0),
    secondary = PinesPalette.Brass, onSecondary = Color(0xFF382614),
    secondaryContainer = Color(0xFF3B2D24), onSecondaryContainer = Color(0xFFF0D6B6),
    tertiary = PinesPalette.Forest, onTertiary = Color(0xFF152B1D),
    tertiaryContainer = Color(0xFF25372B), onTertiaryContainer = Color(0xFFD0E5D1),
    background = PinesPalette.Night, onBackground = PinesPalette.Cream,
    surface = Color(0xFF191416), onSurface = PinesPalette.Cream,
    surfaceVariant = Color(0xFF302629), onSurfaceVariant = PinesPalette.Muted,
    surfaceDim = PinesPalette.Night, surfaceBright = Color(0xFF3C3034),
    surfaceContainerLowest = Color(0xFF0C090B), surfaceContainerLow = Color(0xFF1C1619),
    surfaceContainer = PinesPalette.Card, surfaceContainerHigh = Color(0xFF2B2226),
    surfaceContainerHighest = Color(0xFF362B2F),
    outline = Color(0xFF8D747A), outlineVariant = Color(0xFF49383E),
    inverseSurface = PinesPalette.Cream, inverseOnSurface = PinesPalette.Night,
    inversePrimary = Color(0xFF783344), surfaceTint = PinesPalette.Rose,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF5D1A19), onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black
)

@Composable
internal fun VelvetPinesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = pinesColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(20.dp)
        ),
        content = { Surface(Modifier.fillMaxSize(), color = PinesPalette.Night) { content() } }
    )
}

/** Quiet, static curtain folds and a low-contrast chevron floor, outside the video. */
internal fun Modifier.pinesAtmosphere(): Modifier = drawBehind {
    drawRect(PinesPalette.Night)
    val edge = minOf(52.dp.toPx(), size.width * 0.12f)
    for (i in 0..5) {
        val x = edge * i / 6f
        val fold = Brush.horizontalGradient(
            listOf(Color(0xFF281218), Color(0xFF170E12)),
            startX = x, endX = x + edge / 6f
        )
        drawRect(fold, topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
            size = androidx.compose.ui.geometry.Size(edge / 6f, size.height))
        val right = size.width - edge + x
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF170E12), Color(0xFF281218)), right, right + edge / 6f),
            topLeft = androidx.compose.ui.geometry.Offset(right, 0f),
            size = androidx.compose.ui.geometry.Size(edge / 6f, size.height))
    }
    val step = 28.dp.toPx()
    val floor = Path()
    var x = -step
    floor.moveTo(x, size.height - 20.dp.toPx())
    while (x < size.width + step) {
        floor.lineTo(x + step / 2f, size.height - 30.dp.toPx())
        floor.lineTo(x + step, size.height - 20.dp.toPx())
        x += step
    }
    drawPath(floor, Color(0xFF30252A), style = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx()))
}

@Composable
internal fun PinesLogo(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(PinesPalette.Night),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.55f)
        )
    }
}
@Composable
internal fun PinesIdentityBanner(compact: Boolean = false) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF31151F), PinesPalette.Card, Color(0xFF20281F))),
                RoundedCornerShape(12.dp))
    ) {
        Row(Modifier.padding(if (compact) 6.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PinesLogo(if (compact) 48.dp else 64.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("VELVET PINES", color = PinesPalette.Cream,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 18.sp else 21.sp, letterSpacing = 2.sp)
                if (!compact) Text("کاج‌های مخملی · سینما و واژه‌ها", color = PinesPalette.Muted,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val path = Path()
            val step = 20.dp.toPx()
            var x = -step
            path.moveTo(x, 0f)
            while (x < size.width + step) {
                path.lineTo(x + step / 2, size.height)
                path.lineTo(x + step, 0f)
                x += step
            }
            drawPath(path, PinesPalette.Brass.copy(alpha = 0.4f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
    }
}
