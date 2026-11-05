package com.subtitleplayer.prototype

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal enum class PlayerGlyph { Back, Play, Pause, Previous, Next, Expand, Settings, More, Subtitles, Lock }

@Composable
internal fun PlayerIconButton(
    glyph: PlayerGlyph,
    label: String,
    enabled: Boolean = true,
    large: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(if (large) 64.dp else 48.dp).semantics { contentDescription = label }
    ) {
        Canvas(Modifier.size(if (large) 38.dp else 24.dp)) {
            val ink = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
            val unit = size.width / 24f
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(
                ink, Offset(x * unit, y * unit), Offset(x2 * unit, y2 * unit), 1.8f * unit, StrokeCap.Round
            )
            when (glyph) {
                PlayerGlyph.Play -> drawPath(Path().apply {
                    moveTo(8 * unit, 4 * unit); lineTo(20 * unit, 12 * unit)
                    lineTo(8 * unit, 20 * unit); close()
                }, ink)
                PlayerGlyph.Pause -> {
                    drawRect(ink, Offset(6 * unit, 4 * unit), androidx.compose.ui.geometry.Size(4 * unit, 16 * unit))
                    drawRect(ink, Offset(14 * unit, 4 * unit), androidx.compose.ui.geometry.Size(4 * unit, 16 * unit))
                }
                PlayerGlyph.Back -> { line(19f,12f,5f,12f); line(5f,12f,11f,6f); line(5f,12f,11f,18f) }
                PlayerGlyph.Previous, PlayerGlyph.Next -> {
                    val forward = glyph == PlayerGlyph.Next
                    for (start in listOf(4f, 13f)) {
                        val a = if (forward) start else 24f-start
                        val b = if (forward) start+7 else 17f-start
                        line(a,5f,b,12f); line(b,12f,a,19f)
                    }
                }
                PlayerGlyph.Expand -> {
                    line(3f,9f,3f,3f); line(3f,3f,9f,3f); line(15f,3f,21f,3f); line(21f,3f,21f,9f)
                    line(3f,15f,3f,21f); line(3f,21f,9f,21f); line(15f,21f,21f,21f); line(21f,21f,21f,15f)
                }
                PlayerGlyph.More -> for (y in listOf(5f,12f,19f)) drawCircle(ink, 1.6f*unit, Offset(12*unit,y*unit))
                PlayerGlyph.Subtitles -> {
                    drawRoundRect(ink, Offset(2*unit,4*unit), androidx.compose.ui.geometry.Size(20*unit,16*unit),
                        androidx.compose.ui.geometry.CornerRadius(2*unit), style=Stroke(1.6f*unit))
                    line(5f,10f,10f,10f); line(14f,10f,19f,10f); line(5f,15f,12f,15f); line(16f,15f,19f,15f)
                }
                PlayerGlyph.Lock -> {
                    drawRoundRect(ink, Offset(5*unit,10*unit), androidx.compose.ui.geometry.Size(14*unit,11*unit),
                        androidx.compose.ui.geometry.CornerRadius(2*unit), style=Stroke(1.8f*unit))
                    drawArc(ink,180f,180f,false, Offset(8*unit,2*unit), androidx.compose.ui.geometry.Size(8*unit,16*unit),style=Stroke(1.8f*unit))
                }
                PlayerGlyph.Settings -> {
                    drawCircle(ink,7*unit,style=Stroke(1.8f*unit)); drawCircle(ink,2.5f*unit,style=Stroke(1.8f*unit))
                    line(12f,1f,12f,4f); line(12f,20f,12f,23f); line(1f,12f,4f,12f); line(20f,12f,23f,12f)
                }
            }
        }
    }
}

/** Controls are layered over the video; their visibility never changes its measurement. */
@Composable
internal fun PlayerOverlay(
    title: String,
    playing: Boolean,
    canPlay: Boolean,
    canSeek: Boolean,
    progress: Float,
    positionLabel: String,
    fullscreen: Boolean,
    skipSeconds: Int,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onProgress: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onFullscreen: () -> Unit,
    onLock: () -> Unit,
    topActions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier) {
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=0.85f),Color.Transparent)))
                    .safeDrawingPadding().padding(horizontal=8.dp,vertical=6.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                PlayerIconButton(PlayerGlyph.Back,"بازگشت به کتابخانه",onClick=onBack)
                Column(Modifier.weight(1f).padding(horizontal=6.dp)) {
                    Text(title,color=Color.White,maxLines=1,overflow=TextOverflow.Ellipsis,
                        style=MaterialTheme.typography.titleSmall)
                    Text("VELVET PINES",color=PinesPalette.Rose,style=MaterialTheme.typography.labelSmall)
                }
                topActions()
            }
            Row(Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=0.35f), RoundedCornerShape(48.dp))
                .padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(32.dp)) {
                PlayerIconButton(PlayerGlyph.Previous,"$skipSeconds ثانیه عقب",canSeek,onClick=onSeekBack)
                PlayerIconButton(if(playing) PlayerGlyph.Pause else PlayerGlyph.Play,
                    if(playing) "مکث" else "پخش",canPlay,large=true,onClick=onPlayPause)
                PlayerIconButton(PlayerGlyph.Next,"$skipSeconds ثانیه جلو",canSeek,onClick=onSeekForward)
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=0.92f))))
                    .safeDrawingPadding().padding(horizontal=16.dp)
            ) {
                Slider(value=progress,onValueChange=onProgress,onValueChangeFinished=onSeekFinished,
                    enabled=canSeek,modifier=Modifier.fillMaxWidth().height(32.dp).semantics {
                        contentDescription="موقعیت پخش"; stateDescription=positionLabel
                    },colors=SliderDefaults.colors(thumbColor=PinesPalette.Cream,activeTrackColor=PinesPalette.Rose))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(positionLabel,color=Color.White,modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.labelMedium.copy(textDirection=TextDirection.Ltr))
                    PlayerIconButton(PlayerGlyph.Lock,"قفل کنترل‌ها",onClick=onLock)
                    PlayerIconButton(PlayerGlyph.Expand,if(fullscreen) "خروج از تمام‌صفحه" else "تمام‌صفحه",onClick=onFullscreen)
                }
            }
        }
    }
}
