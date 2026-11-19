package com.subtitleplayer.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

internal fun LazyListScope.englishDictionarySection(
    entries: List<EnglishLexicalEntry>,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onSelectWord: (String) -> Unit
) {
    item(key = "english-source") {
        Text("English definitions", style = MaterialTheme.typography.titleMedium)
        Text("Open English WordNet 2025", style = MaterialTheme.typography.labelSmall, color = PinesPalette.Muted)
        if (loading) Text("Loading English entry…", color = PinesPalette.Muted)
        else if (failed) TextButton(onClick = onRetry) { Text("Retry English entry") }
        else if (entries.isEmpty()) Text("No English entry in the bundled source.", color = PinesPalette.Muted)
    }
    for (entry in entries) {
        item(key = "english-entry-${entry.id}") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${entry.lemma} · ${entry.partOfSpeech}", color = PinesPalette.Accent,
                    style = MaterialTheme.typography.titleSmall)
                for (pronunciation in entry.pronunciations) {
                    val variety = if (pronunciation.variety == "GB") "UK" else pronunciation.variety
                    val label = if (variety.isBlank()) "IPA" else variety
                    Text("$label · /${pronunciation.ipa}/",
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                }
                if (entry.forms.isNotEmpty()) RelatedEnglishWords("Word forms", entry.forms, onSelectWord)
            }
        }
        items(entry.senses, key = { "english-sense-${it.id}" }) { sense ->
            var expanded by rememberSaveable(sense.id) { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = PinesPalette.Card), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (definition in sense.definitions) Text(definition,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                    val hasDetails = sense.examples.isNotEmpty() || sense.synonyms.isNotEmpty() ||
                        sense.antonyms.isNotEmpty() || sense.related.isNotEmpty()
                    if (hasDetails) TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Hide details" else "Examples and related words")
                    }
                    if (expanded) {
                        if (sense.examples.isNotEmpty()) {
                            Text("Examples", style = MaterialTheme.typography.labelLarge)
                            for (example in sense.examples) Text(example, color = PinesPalette.Muted,
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                        }
                        RelatedEnglishWords("Synonyms", sense.synonyms, onSelectWord)
                        RelatedEnglishWords("Antonyms", sense.antonyms, onSelectWord)
                        RelatedEnglishWords("Related forms", sense.related, onSelectWord)
                    }
                }
            }
        }
    }
}

@Composable
private fun RelatedEnglishWords(label: String, words: List<String>, onSelectWord: (String) -> Unit) {
    if (words.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (word in words) TextButton(enabled = normalizeDictionaryTerm(word) != null,
            onClick = { onSelectWord(word) }) { Text(word) }
    }
}
