package com.subtitleplayer.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun PronunciationControls(
    speech: EnglishPronunciation,
    word: String,
    dialogue: String? = null,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    beforeSpeak: () -> Unit = {}
) {
    var showHelp by remember { mutableStateOf(false) }
    fun pronounce(text: String, slow: Boolean = false) {
        if (speech.ready) {
            speech.speak(text, slow, beforeSpeak)
        } else {
            beforeSpeak()
            showHelp = true
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(enabled = !speech.initializing, onClick = { pronounce(word) }) {
            Text(if (speech.initializing) "Preparing pronunciation…" else "Pronounce", color = contentColor)
        }
        TextButton(enabled = !speech.initializing, onClick = { pronounce(word, true) }) {
            Text("Slow", color = contentColor)
        }
        if (dialogue != null) {
            TextButton(enabled = !speech.initializing, onClick = { pronounce(dialogue) }) {
                Text("Read sentence", color = contentColor)
            }
        }
        if (speech.speaking) {
            TextButton(onClick = speech::stop) { Text("Stop speech", color = contentColor) }
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Set up English pronunciation") },
            text = {
                Text("Enable a text-to-speech engine and download an offline English voice in your device's speech settings, then return to the app.")
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false; speech.openSettings() }) { Text("Speech settings") }
            },
            dismissButton = {
                TextButton(onClick = { showHelp = false; speech.refresh() }) { Text("Check again") }
            }
        )
    }
}
