package com.subtitleplayer.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Locale
import java.util.Date

@Composable
internal fun VocabularyStatsDialog(words: List<SavedWord>, now: Long, onDismiss: () -> Unit) {
    val unique = remember(words) { words.map { normalizeDictionaryTerm(it.wordKey) ?: normalizeWord(it.wordKey) }.toSet().size }
    val answers = remember(words) { words.sumOf { it.reviewCount.coerceAtLeast(0).toLong() } }
    val newCount = remember(words) { words.count { it.reviewCount == 0 } }
    val due = remember(words, now) { words.count { it.reviewDueAt <= now } }
    val next = remember(words, now) { words.filter { it.reviewDueAt > now }.minOfOrNull { it.reviewDueAt } }
    val levels = remember(words) { words.filter { it.reviewCount > 0 }.groupingBy { it.reviewLevel }.eachCount() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Flashcard statistics") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsRow("Saved cards", words.size.toString())
                StatsRow("Unique words and phrases", unique.toString())
                StatsRow("New cards", newCount.toString())
                StatsRow("Reviewed cards", (words.size - newCount).toString())
                StatsRow("Due, including new cards", due.toString())
                StatsRow("Scheduled for later", (words.size - due).toString())
                StatsRow("Review answers for current cards", answers.toString())
                next?.let {
                    Text(
                        "Next scheduled review: " +
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH).format(Date(it)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text("Reviewed cards by level", style = MaterialTheme.typography.titleSmall)
                listOf("Restart · 10 minutes", "Level 1 · 1 day", "Level 2 · 3 days",
                    "Level 3 · 7 days", "Level 4 · 14 days", "Level 5 · 30 days").forEachIndexed { level, label ->
                    StatsRow(label, (levels[level] ?: 0).toString())
                }
                Text(
                    "Stats cover all flashcards. A word can have a card for each dialogue. Review levels determine the interval between reviews.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
