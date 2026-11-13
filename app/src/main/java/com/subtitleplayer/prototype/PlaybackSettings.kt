package com.subtitleplayer.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.verticalScroll
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.util.Locale
import kotlin.math.roundToInt

private enum class SettingsSection(val label: String) {
    Playback("Play"), Subtitles("Subtitles"), Study("Study"), Help("Help")
}

internal val seekStepChoices = listOf(5, 10, 30)
private val speedChoices = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val delayStepMs = 250L
private const val maxDelayMs = 60_000L

@Composable
internal fun PlaybackSettings(
    englishEncoding: SubtitleTextEncoding,
    onEnglishEncoding: (SubtitleTextEncoding) -> Unit,
    persianEncoding: SubtitleTextEncoding,
    onPersianEncoding: (SubtitleTextEncoding) -> Unit,
    englishSubtitlesVisible: Boolean,
    onEnglishSubtitlesVisible: (Boolean) -> Unit,
    persianSubtitlesVisible: Boolean,
    onPersianSubtitlesVisible: (Boolean) -> Unit,
    videoScaleMode: VideoScaleMode,
    onVideoScaleMode: (VideoScaleMode) -> Unit,
    sleepRemainingMs: Long?,
    onSleepTimer: (Int?) -> Unit,
    skipSeconds: Int,
    onSkipSeconds: (Int) -> Unit,
    swipeToSeek: Boolean,
    onSwipeToSeek: (Boolean) -> Unit,
    orientation: PlaybackOrientation,
    onOrientation: (PlaybackOrientation) -> Unit,
    speed: Float,
    onSpeed: (Float) -> Unit,
    tracks: Tracks,
    onAudioTrack: (TrackSelectionOverride) -> Unit,
    pauseAfterDialogue: Boolean,
    onPauseAfterDialogue: (Boolean) -> Unit,
    subtitleLookupMode: SubtitleLookupMode,
    onSubtitleLookupMode: (SubtitleLookupMode) -> Unit,
    pauseOnWordTap: Boolean,
    onPauseOnWordTap: (Boolean) -> Unit,
    subtitleTextTone: SubtitleTextTone,
    subtitleBackgroundOpacity: Float,
    onSubtitleTextTone: (SubtitleTextTone) -> Unit,
    onSubtitleBackgroundOpacity: (Float) -> Unit,
    onSubtitleStyleReset: () -> Unit,
    subtitleSize: Int,
    onSubtitleSize: (Int) -> Unit,
    subtitleDelayMs: Long?,
    onSubtitleDelay: (Long) -> Unit,
    translationDelayMs: Long?,
    onTranslationDelay: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(SettingsSection.Playback) }
    val scrollState = rememberScrollState()
    LaunchedEffect(section) { scrollState.scrollTo(0) }
    PlayerOverlayPanel("Playback settings", onDismiss) {
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SettingsSection.entries.forEach { option ->
                    FilterChip(
                        selected = section == option,
                        onClick = { section = option },
                        label = { Text(option.label) }
                    )
                }
            }
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (section) {
                    SettingsSection.Playback -> {
                        Text(
                            sleepRemainingMs?.let { "Sleep timer: " + formatTime((it + 999) / 1000 * 1000) + " remaining" }
                                ?: "Sleep timer: off",
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(1, 15, 30, 60).forEach { minutes ->
                                TextButton(onClick = { onSleepTimer(minutes) }) { Text("$minutes min") }
                            }
                            if (sleepRemainingMs != null) {
                                TextButton(onClick = { onSleepTimer(null) }) { Text("Cancel timer") }
                            }
                        }

                        Text("Video scaling", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VideoScaleMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = mode == videoScaleMode,
                                    onClick = { onVideoScaleMode(mode) },
                                    label = { Text(mode.label) }
                                )
                            }
                        }
                        Text(videoScaleMode.description, style = MaterialTheme.typography.bodySmall)

                        Text("Screen orientation", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PlaybackOrientation.entries.forEach { mode ->
                                FilterChip(
                                    selected = orientation == mode,
                                    onClick = { onOrientation(mode) },
                                    label = { Text(mode.name) }
                                )
                            }
                        }
                        PlaybackToggle("Swipe to seek", swipeToSeek, onSwipeToSeek)
                        Text("Seek interval", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            seekStepChoices.forEach { seconds ->
                                FilterChip(
                                    selected = skipSeconds == seconds,
                                    onClick = { onSkipSeconds(seconds) },
                                    label = { Text("$seconds sec") }
                                )
                            }
                        }

                        Text("Playback speed: ${speed}×", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = speedChoices.indexOf(speed).coerceAtLeast(0).toFloat(),
                            onValueChange = { onSpeed(speedChoices[it.roundToInt()]) },
                            valueRange = 0f..speedChoices.lastIndex.toFloat(),
                            steps = speedChoices.size - 2,
                            modifier = Modifier.semantics {
                                contentDescription = "Playback speed"
                                stateDescription = "${speed}×"
                            }
                        )

                        Text("Audio track", style = MaterialTheme.typography.titleSmall)
                        AudioTracks(tracks, onAudioTrack)
                    }
                    SettingsSection.Subtitles -> {
                        SubtitleEncodingChoices("English encoding", englishEncoding, onEnglishEncoding)
                        SubtitleEncodingChoices("Persian encoding", persianEncoding, onPersianEncoding)
                        Text(
                            "Manual encoding applies to files without a byte-order mark. If text looks wrong, change encoding or return to Auto.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        PlaybackToggle("Show English subtitles", englishSubtitlesVisible, onEnglishSubtitlesVisible)
                        PlaybackToggle("Show Persian subtitles", persianSubtitlesVisible, onPersianSubtitlesVisible)
                        Text(
                            "Hiding a track keeps its file and delay. Hide both for listening practice.",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text("English text color", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubtitleTextTone.entries.forEach { tone ->
                                FilterChip(
                                    selected = tone == subtitleTextTone,
                                    onClick = { onSubtitleTextTone(tone) },
                                    label = { Text(tone.label) }
                                )
                            }
                        }
                        Text(
                            "Subtitle background opacity: ${(subtitleBackgroundOpacity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Slider(
                            value = subtitleBackgroundOpacity,
                            onValueChange = onSubtitleBackgroundOpacity,
                            valueRange = 0f..1f,
                            modifier = Modifier.semantics {
                                contentDescription = "Subtitle background opacity"
                                stateDescription = "${(subtitleBackgroundOpacity * 100).roundToInt()} percent"
                            }
                        )
                        Text(
                            "The story continues.",
                            Modifier.background(Color.Black.copy(alpha = subtitleBackgroundOpacity), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            color = Color(subtitleTextTone.argb),
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = onSubtitleStyleReset) { Text("Reset colors") }

                        Text("Subtitle size: $subtitleSize", style = MaterialTheme.typography.titleSmall)
                        Slider(
                            value = subtitleSize.toFloat(),
                            onValueChange = { onSubtitleSize(it.roundToInt()) },
                            valueRange = 16f..32f,
                            steps = 15,
                            modifier = Modifier.semantics {
                                contentDescription = "Subtitle size"
                                stateDescription = "$subtitleSize"
                            }
                        )

                        if (subtitleDelayMs != null) {
                            SubtitleDelay("English", subtitleDelayMs, onSubtitleDelay)
                        }
                        if (translationDelayMs != null) {
                            SubtitleDelay("Persian", translationDelayMs, onTranslationDelay)
                        }

                    }
                    SettingsSection.Study -> {
                        Text("Subtitle selection", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubtitleLookupMode.entries.forEach { mode ->
                                FilterChip(selected = mode == subtitleLookupMode,
                                    onClick = { onSubtitleLookupMode(mode) }, label = { Text(mode.label) })
                            }
                        }
                        Text("Word mode selects one word. Phrase mode also recognizes phrases in the offline and personal dictionaries.",
                            style = MaterialTheme.typography.bodySmall)
                        SubtitleVisibility("Pause on word tap", pauseOnWordTap, onPauseOnWordTap)

                        SubtitleVisibility("Pause after each dialogue", pauseAfterDialogue, onPauseAfterDialogue)
                        Text(
                            "Applies to external English subtitles. Press Play after each line to continue. A–B and dialogue loops take priority.",
                            style = MaterialTheme.typography.bodySmall
                        )

                    }
                    SettingsSection.Help -> PlaybackHelp(skipSeconds)
                }
            }
        }
    }
}

/** Shared window overlay; settings and dictionary never resize the underlying video surface. */
@Composable
internal fun PlayerOverlayPanel(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize()) {
            Spacer(Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ).clearAndSetSemantics { })
            Surface(
                modifier = Modifier.align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
                    .widthIn(max = if (landscape) 440.dp else 640.dp).fillMaxWidth()
                    .fillMaxHeight(if (landscape) 1f else 0.88f)
                    .safeDrawingPadding().imePadding().pointerInput(Unit) { },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = if (landscape) 0.dp else 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text("Velvet Pines", color = PinesPalette.Accent,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = onDismiss) { Text("Close") }
                    }
                    HorizontalDivider(color = PinesPalette.AccentContainer)
                    content()
                }
            }
        }
    }
}

@Composable
private fun SubtitleDelay(language: String, delayMs: Long, onDelay: (Long) -> Unit) {
    val delayLabel = String.format(Locale.ROOT, "%+.2f", delayMs / 1000.0)
    Text("$language subtitle delay: $delayLabel s", style = MaterialTheme.typography.titleSmall)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(
            enabled = delayMs > -maxDelayMs,
            onClick = { onDelay((delayMs - delayStepMs).coerceAtLeast(-maxDelayMs)) }
        ) { Text("Earlier") }
        TextButton(enabled = delayMs != 0L, onClick = { onDelay(0) }) { Text("Reset") }
        TextButton(
            enabled = delayMs < maxDelayMs,
            onClick = { onDelay((delayMs + delayStepMs).coerceAtMost(maxDelayMs)) }
        ) { Text("Later") }
    }
}

@Composable
private fun AudioTracks(tracks: Tracks, onAudioTrack: (TrackSelectionOverride) -> Unit) {
    val options = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { group ->
        (0 until group.length).filter(group::isTrackSupported).map { index -> group to index }
    }
    if (options.isEmpty()) {
        Text("No playable audio tracks.")
        return
    }

    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { number, (group, index) ->
            val format = group.getTrackFormat(index)
            val language = format.language?.takeUnless { it == "und" }?.let { code ->
                Locale.forLanguageTag(code).getDisplayLanguage(Locale.forLanguageTag("fa"))
            }
            val selected = group.isTrackSelected(index)
            val name = format.label?.takeIf(String::isNotBlank) ?: language?.takeIf(String::isNotBlank)
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = selected, role = Role.RadioButton, onClick = {
                        onAudioTrack(TrackSelectionOverride(group.mediaTrackGroup, index))
                    })
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(if (name == null) "Track ${number + 1}" else "${number + 1}. $name")
            }
        }
    }
}

@Composable
private fun PlaybackToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Switch(checked = checked, onCheckedChange = null)
    }
}


@Composable
private fun SubtitleEncodingChoices(
    label: String, selected: SubtitleTextEncoding, onSelected: (SubtitleTextEncoding) -> Unit
) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SubtitleTextEncoding.entries.forEach { encoding ->
            FilterChip(
                selected = encoding == selected,
                onClick = { onSelected(encoding) },
                label = { Text(encoding.label) }
            )
        }
    }
}
