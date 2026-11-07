package com.subtitleplayer.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaybackHelp(skipSeconds: Int) {
    PinesIdentityBanner()
    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
    HelpCard("Player controls",
        "Tap the video to show or hide controls. Fullscreen rotates the player. Double-tap left or right to seek $skipSeconds seconds. Swipe vertically on the left for brightness or on the right for volume. Use Lock to lock controls and orientation.")
    HelpCard("English subtitles",
        "The player automatically selects an embedded English text track when available. Use Subtitles to choose a track or external file, hide the text, or turn the track off. Previously opened videos keep their saved selection.")
    HelpCard("Matching subtitle files",
        "Open the video from an accessible folder. In automatic mode, a single readable match such as Episode.en.srt opens automatically. Choose unlabeled files or ties from Subtitles. The player searches only that folder and honors your previous selection.")
    HelpCard("Tap to translate",
        "Tap an English word for a Persian meaning over the video. The card stays for about three seconds after the result arrives and respects accessibility timeouts. For phrases or a card that stays open, use Study settings. The card menu offers pronunciation and editing. Offline lookup checks your definitions first. Online translation sends the selected term only when you ask for it.")
    HelpCard("Search the dictionary",
        "Open Dictionary from Files, playback options, or a word card. Enter an English word or prefix, then tap a result for its meaning and pronunciation. Add or edit personal definitions from the result.")
    HelpCard("Dual subtitles",
        "Choose English and Persian files separately in Subtitles. Use playback settings to change visibility, delay, encoding, and style. If text looks wrong, try Auto encoding first.")
    HelpCard("Repeat and listen",
        "Set A at the start and B at least half a second later. Clear the loop or seek manually to end it. Dialogue navigation and pause after dialogue use an external subtitle file.")
    HelpCard("Continue watching",
        "Open Recent in Files to resume each video at its saved position. Choose Play from beginning in a video's menu to start over. Progress appears when duration is known.")
    HelpCard("Flashcards and review",
        "Save a word from its card and open Flashcards from the bottom bar. Tap a word to reveal its meaning. Flashcards also offers review, statistics, and CSV or Anki export. Pronunciation uses your device's offline English voice.")
    HelpCard("Backups and personal definitions",
        "Use the Flashcards menu to manage personal definitions or back up cards and review progress. Grant folder access again on a new installation.")
}

@Composable
private fun HelpCard(title: String, description: String) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
