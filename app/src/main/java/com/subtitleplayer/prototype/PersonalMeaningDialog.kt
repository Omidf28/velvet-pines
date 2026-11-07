package com.subtitleplayer.prototype

import android.database.sqlite.SQLiteException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun PersonalMeaningDialog(
    dao: PersonalDictionaryDao,
    term: String,
    initialMeaning: WordMeaning?,
    hasPersonalMeaning: Boolean,
    onSaved: (WordMeaning?) -> Unit,
    onDismiss: () -> Unit
) {
    var headword by rememberSaveable(term) { mutableStateOf(initialMeaning?.headword ?: term) }
    var definition by rememberSaveable(term) { mutableStateOf(initialMeaning?.persian.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val key = normalizeDictionaryTerm(term)
    val headwordValid = normalizeDictionaryTerm(headword) != null
    val meaning = validPersonalMeaning(definition)
    fun persist(reset: Boolean) {
        val wordKey = key ?: return
        if (saving || (!reset && (!headwordValid || meaning == null))) return
        saving = true
        failed = false
        scope.launch {
            try {
                if (reset) dao.remove(wordKey)
                else dao.put(PersonalDictionaryEntry(wordKey, headword.trim(), meaning!!))
                onSaved(if (reset) defaultTermMeaning(term) else WordMeaning(headword.trim(), meaning!!))
                onDismiss()
            } catch (_: SQLiteException) {
                failed = true
            } finally {
                saving = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Personal definition: $term") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("This definition applies to this term across all videos.")
                OutlinedTextField(
                    value = headword,
                    onValueChange = { headword = it },
                    label = { Text("Base form or English phrase") },
                    singleLine = true,
                    enabled = !saving,
                    isError = !headwordValid,
                    supportingText = { Text("Up to 8 words and 80 English characters") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
                )
                OutlinedTextField(
                    value = definition,
                    onValueChange = { definition = it },
                    label = { Text("Persian meaning") },
                    enabled = !saving,
                    minLines = 2,
                    maxLines = 5,
                    isError = definition.isNotEmpty() && meaning == null,
                    supportingText = { Text("Up to 1,000 characters") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)
                )
                if (hasPersonalMeaning) {
                    TextButton(enabled = !saving, onClick = { persist(true) }) {
                        Text("Remove personal definition")
                    }
                }
                if (saving) Text("Saving…")
                if (failed) Text("Could not save. Try again.")
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && key != null && headwordValid && meaning != null,
                onClick = { persist(false) }
            ) { Text("Save definition") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    )
}
