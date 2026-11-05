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
            Text(if (speech.initializing) "آماده‌سازی تلفظ…" else "تلفظ", color = contentColor)
        }
        TextButton(enabled = !speech.initializing, onClick = { pronounce(word, true) }) {
            Text("آهسته", color = contentColor)
        }
        if (dialogue != null) {
            TextButton(enabled = !speech.initializing, onClick = { pronounce(dialogue) }) {
                Text("خواندن جمله", color = contentColor)
            }
        }
        if (speech.speaking) {
            TextButton(onClick = speech::stop) { Text("توقف صدا", color = contentColor) }
        }
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("راه‌اندازی تلفظ انگلیسی") },
            text = {
                Text("در تنظیمات گفتار گوشی، یک موتور تبدیل متن به گفتار و صدای آفلاین English را فعال یا دانلود کنید، سپس به برنامه برگردید. تلفظ این برنامه به صدای آفلاین نیاز دارد.")
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false; speech.openSettings() }) { Text("تنظیمات گفتار") }
            },
            dismissButton = {
                TextButton(onClick = { showHelp = false; speech.refresh() }) { Text("بررسی دوباره") }
            }
        )
    }
}
