package com.subtitleplayer.prototype

import android.provider.Settings
import android.net.Uri
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.delay

private const val minimumBrightness = 0.05f

private enum class GestureControl(val label: String, val minimumLevel: Float) {
    Brightness("Brightness", minimumBrightness),
    Volume("Video volume", 0f)
}

private data class GestureLevel(val control: GestureControl, val percent: Int)

private data class SeekPreview(val startMs: Long, val targetMs: Long)

internal fun swipeSeekTarget(startMs: Long, durationMs: Long, dragPx: Float, widthPx: Int): Long {
    if (durationMs <= 0) return 0
    // A full-width drag moves one minute, even on a long movie.
    val offset = (dragPx.toDouble() / widthPx.coerceAtLeast(1) * 60_000).roundToLong()
    return (startMs + offset).coerceIn(0L, durationMs)
}

@Composable
internal fun PlaybackGestureLayer(
    window: Window,
    player: Player,
    enabled: Boolean,
    onToggleControls: (() -> Unit)?,
    onSkip: ((Long) -> Unit)?,
    onSwipeSeek: ((Long) -> Unit)?,
    mediaKey: Uri?,
    landscape: Boolean,
    skipSeconds: Int,
    modifier: Modifier
) {
    var feedback by remember { mutableStateOf<GestureLevel?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var skipFeedback by remember { mutableStateOf<String?>(null) }
    var tapSequence by remember { mutableIntStateOf(0) }
    val currentSkip by rememberUpdatedState(onSkip)
    val currentToggle by rememberUpdatedState(onToggleControls)
    val currentSwipeSeek by rememberUpdatedState(onSwipeSeek)
    var seekPreview by remember(mediaKey, landscape, enabled, onSwipeSeek != null) {
        mutableStateOf<SeekPreview?>(null)
    }
    LaunchedEffect(enabled, tapSequence) {
        if (!enabled) skipFeedback = null
        else if (skipFeedback != null) {
            delay(1_100)
            skipFeedback = null
        }
    }

    DisposableEffect(window) {
        val original = window.attributes.screenBrightness
        onDispose { window.updateVideoBrightness(original) }
    }
    LaunchedEffect(enabled, dragging, feedback) {
        if (!enabled) {
            dragging = false
            feedback = null
        } else if (!dragging && feedback != null) {
            delay(1_100)
            feedback = null
        }
    }

    val tapTarget = modifier.semantics {
        if (enabled && onToggleControls != null) {
            onClick(label = "Show or hide playback controls") { currentToggle?.invoke(); true }
        }
        if (enabled && onSkip != null) {
            customActions = listOf(
                CustomAccessibilityAction("Back $skipSeconds seconds") { currentSkip?.invoke(-skipSeconds * 1_000L); true },
                CustomAccessibilityAction("Forward $skipSeconds seconds") { currentSkip?.invoke(skipSeconds * 1_000L); true }
            )
        }
    }.pointerInput(enabled, onSkip != null, onToggleControls != null, skipSeconds) {
        if (!enabled) return@pointerInput
        val doubleTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = if (onSkip == null) null else { point ->
            val forward = point.x >= size.width / 2f
            currentSkip?.invoke((if (forward) 1 else -1) * skipSeconds * 1_000L)
            skipFeedback = if (forward) "Forward $skipSeconds seconds" else "Back $skipSeconds seconds"
            tapSequence++
        }
        detectTapGestures(
            onTap = { currentToggle?.invoke() },
            onDoubleTap = doubleTap
        )
    }

    val input = if (!enabled) tapTarget else tapTarget.pointerInput(window, player) {
        var control = GestureControl.Volume
        var level = 0f
        detectVerticalDragGestures(
            onDragStart = { start ->
                dragging = true
                skipFeedback = null
                control = if (start.x < size.width / 2f) GestureControl.Brightness else GestureControl.Volume
                level = when (control) {
                    GestureControl.Brightness -> window.readVideoBrightness()
                    GestureControl.Volume -> player.volume
                }
                feedback = GestureLevel(control, (level * 100).roundToInt())
            },
            onDragEnd = { dragging = false },
            onDragCancel = { dragging = false },
            onVerticalDrag = { change, delta ->
                change.consume()
                level = (level - delta / (size.height.coerceAtLeast(1) * 0.75f))
                    .coerceIn(control.minimumLevel, 1f)
                when (control) {
                    GestureControl.Brightness -> window.updateVideoBrightness(level)
                    GestureControl.Volume -> player.volume = level
                }
                feedback = GestureLevel(control, (level * 100).roundToInt())
            }
        )
    }

    val seekInput = if (!enabled || onSwipeSeek == null) input else input.pointerInput(player, mediaKey, landscape) {
        var distance = 0f
        var durationMs = 0L
        try {
            detectHorizontalDragGestures(
                onDragStart = {
                    distance = 0f
                    durationMs = player.duration
                    skipFeedback = null
                    feedback = null
                    val position = player.currentPosition
                    seekPreview = SeekPreview(position, position)
                },
                onDragEnd = {
                    val preview = seekPreview
                    seekPreview = null
                    if (preview != null && preview.targetMs != preview.startMs) {
                        currentSwipeSeek?.invoke(preview.targetMs)
                    }
                },
                onDragCancel = { seekPreview = null },
                onHorizontalDrag = { change, delta ->
                    change.consume()
                    distance += delta
                    seekPreview = seekPreview?.let { preview ->
                        preview.copy(targetMs = swipeSeekTarget(preview.startMs, durationMs, distance, size.width))
                    }
                }
            )
        } finally {
            seekPreview = null
        }
    }

    Box(seekInput) {
        val seekLabel = seekPreview?.let { preview ->
            val delta = preview.targetMs - preview.startMs
            val sign = if (delta < 0) "−" else "+"
            "${formatTime(preview.targetMs)} ($sign${formatTime(kotlin.math.abs(delta))})"
        }
        (seekLabel ?: skipFeedback)?.let { label ->
            Text(
                label,
                Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(10.dp)).padding(14.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
        feedback?.let { indicator ->
            Text(
                "${indicator.control.label} ${indicator.percent}%",
                Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
                    .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

private fun Window.readVideoBrightness(): Float {
    val windowLevel = attributes.screenBrightness
    if (windowLevel >= 0f) return windowLevel.coerceIn(minimumBrightness, 1f)

    val systemLevel = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    return (systemLevel / 255f).coerceIn(minimumBrightness, 1f)
}

private fun Window.updateVideoBrightness(level: Float) {
    val params = attributes
    if (params.screenBrightness == level) return
    params.screenBrightness = level
    attributes = params
}
