package com.subtitleplayer.prototype

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SubtitlePhraseChoices(
    selection: SubtitleSelection,
    personalMeanings: Map<String, WordMeaning>,
    dictionary: PackagedDictionary,
    onSelect: (SubtitleTerm) -> Unit
) {
    val candidates = remember(selection) { subtitlePhraseCandidates(selection) }
    if (candidates.isEmpty()) return

    var open by remember(selection) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) {
        Text(if (open) "Hide phrases" else "Phrases in this subtitle", color = PinesPalette.Ink)
    }
    if (!open) return

    val keys = remember(candidates) { candidates.mapNotNull { normalizeDictionaryTerm(it.text) }.toSet() }
    val known = remember(keys, personalMeanings) {
        keys.filterTo(mutableSetOf()) { it in personalMeanings || defaultTermMeaning(it) != null }
    }
    var retry by remember { mutableIntStateOf(0) }
    var matches by remember(keys, known, retry) { mutableStateOf<Set<String>?>(null) }
    var failed by remember(keys, known, retry) { mutableStateOf(false) }
    LaunchedEffect(dictionary, keys, known, retry) {
        try {
            matches = known + withContext(Dispatchers.IO) { dictionary.matchingPhrases(keys - known) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
            matches = known
        }
    }

    val found = matches
    val status = when {
        found == null -> "Checking dictionary…"
        failed -> "Could not read part of the dictionary."
        found.isEmpty() -> "No matching phrases found."
        else -> "Dictionary matches"
    }
    Text(status, color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall)
    if (failed) TextButton(onClick = { retry++ }) { Text("Try again") }
    if (found != null) {
        candidates.filter { normalizeDictionaryTerm(it.text) in found }.forEach { term ->
            TextButton(onClick = { onSelect(term) }) {
                Text(term.text, color = PinesPalette.Accent,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
            }
        }
    }
}
