package com.subtitleplayer.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object PinesPalette {
    val Night = Color(0xFF15161B)
    val Card = Color(0xFF23252F)
    val AccentContainer = Color(0xFF30365C)
    val Accent = Color(0xFFA9B4FF)
    val Ink = Color(0xFFF2F3F8)
    val Muted = Color(0xFFB6BACB)
    val Highlight = Color(0xFFF0C779)
    val Success = Color(0xFF8ED7AE)
    val Border = Color(0xFF414554)
}

private val pinesColors = darkColorScheme(
    primary = PinesPalette.Accent, onPrimary = Color(0xFF151F52),
    primaryContainer = PinesPalette.AccentContainer, onPrimaryContainer = Color(0xFFE0E4FF),
    secondary = Color(0xFFC2C7ED), onSecondary = Color(0xFF292E48),
    secondaryContainer = Color(0xFF33394F), onSecondaryContainer = Color(0xFFE1E4FA),
    tertiary = PinesPalette.Success, onTertiary = Color(0xFF0D3121),
    tertiaryContainer = Color(0xFF204838), onTertiaryContainer = Color(0xFFB8F2D0),
    background = PinesPalette.Night, onBackground = PinesPalette.Ink,
    surface = Color(0xFF1B1D25), onSurface = PinesPalette.Ink,
    surfaceVariant = Color(0xFF2B2E39), onSurfaceVariant = PinesPalette.Muted,
    surfaceDim = PinesPalette.Night, surfaceBright = Color(0xFF373B49),
    surfaceContainerLowest = Color(0xFF111217), surfaceContainerLow = Color(0xFF1D1F28),
    surfaceContainer = PinesPalette.Card, surfaceContainerHigh = Color(0xFF2B2E39),
    surfaceContainerHighest = Color(0xFF343744),
    outline = Color(0xFF808699), outlineVariant = PinesPalette.Border,
    inverseSurface = PinesPalette.Ink, inverseOnSurface = PinesPalette.Night,
    inversePrimary = Color(0xFF4555A9), surfaceTint = PinesPalette.Accent,
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF5D1A19), onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black
)

private val defaults = Typography()
private val pinesTypography = Typography(
    titleLarge = defaults.titleLarge.copy(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp
    ),
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = defaults.titleSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp)
)

@Composable
internal fun VelvetPinesTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        MaterialTheme(
            colorScheme = pinesColors,
            typography = pinesTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(22.dp)
            )
        ) {
            Surface(Modifier.fillMaxSize(), color = PinesPalette.Night, content = content)
        }
    }
}

private val workspaceGradient = Brush.verticalGradient(
    listOf(Color(0xFF222535), PinesPalette.Night, PinesPalette.Night)
)
private val glassGradient = Brush.verticalGradient(
    listOf(Color(0xEB32374A), Color(0xEB22242F))
)
private val videoGlassGradient = Brush.verticalGradient(
    listOf(Color(0xF02C3040), Color(0xF0191B25))
)

internal fun Modifier.pinesAtmosphere(): Modifier = background(workspaceGradient)

internal fun Modifier.glassPanel(shape: Shape, overVideo: Boolean = false): Modifier =
    clip(shape)
        .background(if (overVideo) videoGlassGradient else glassGradient)
        .border(1.dp, PinesPalette.Accent.copy(alpha = 0.22f), shape)

@Composable
internal fun PinesLogo(size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(PinesPalette.AccentContainer),
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
internal fun PinesIdentityBanner() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .glassPanel(MaterialTheme.shapes.large).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PinesLogo(40.dp)
        Text("Velvet Pines", color = PinesPalette.Ink, style = MaterialTheme.typography.titleMedium)
    }
}
