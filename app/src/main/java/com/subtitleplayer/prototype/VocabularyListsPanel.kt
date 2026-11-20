package com.subtitleplayer.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun VocabularyListsPanel(
    dao: DictionaryBookmarksDao,
    word: DictionaryBookmark?,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onRenamed: (String, String) -> Unit
) {
    val lists by remember(dao) { dao.observeLists() }.collectAsState(initial = null)
    val included by remember(dao, word?.wordKey) {
        word?.let { dao.observeListsForWord(it.wordKey) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<VocabularyListSummary?>(null) }
    fun write(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Could not update lists. Try again." }
            finally { busy = false }
        }
    }
    PlayerOverlayPanel(if (word == null) "Saved lists" else "Lists for ${word.headword}", onDismiss = onDismiss) {
        Text(if (word == null) "Organize saved words for study. Deleting a list keeps its words in Saved words."
            else "Choose any lists. Adding this word also saves it for offline study.",
            style = MaterialTheme.typography.bodySmall, color = PinesPalette.Muted)
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (lists == null) item { Text("Loading lists…") }
            else if (lists!!.isEmpty()) item { Text("No lists yet. Create one to get started.") }
            items(lists.orEmpty(), key = { it.nameKey }) { list ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (word != null) {
                        Checkbox(checked = list.nameKey in included.orEmpty(), enabled = !busy && included != null,
                            onCheckedChange = { checked -> write {
                                if (!dao.setListWord(word, list.nameKey, checked)) error = "This list was removed. Choose another list."
                            } }, modifier = Modifier.semantics { contentDescription = "Include in ${list.name}" })
                    }
                    Column(Modifier.weight(1f).then(if (word == null) Modifier.clickable(
                        enabled = !busy, onClickLabel = "Open list ${list.name}"
                    ) { onOpen(list.nameKey) } else Modifier).padding(vertical = 12.dp)) {
                        Text(list.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${list.wordCount} words", style = MaterialTheme.typography.bodySmall, color = PinesPalette.Muted)
                    }
                    if (word == null) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            PlayerIconButton(PlayerGlyph.More, "List options for ${list.name}", enabled = !busy,
                                onClick = { menu = true })
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                    menu = false; editKey = list.nameKey; name = list.name; error = null; editing = true
                                }, modifier = Modifier.semantics { contentDescription = "Rename list ${list.name}" })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = list },
                                    modifier = Modifier.semantics { contentDescription = "Delete list ${list.name}" })
                            }
                        }
                    }
                }
            }
        }
        Button(enabled = !busy, onClick = { editKey = null; name = ""; error = null; editing = true }) { Text("New list") }
    }
    if (editing) AlertDialog(
        onDismissRequest = { if (!busy) editing = false },
        title = { Text(if (editKey == null) "New list" else "Rename list") },
        text = {
            val keyboard = LocalSoftwareKeyboardController.current
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it.take(160); error = null },
                    enabled = !busy, label = { Text("List name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
                Text("Up to 80 characters.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy && validVocabularyListName(name) != null, onClick = {
            val oldKey = editKey
            val newName = validVocabularyListName(name) ?: return@TextButton
            write {
                val result = if (oldKey == null) dao.createList(newName, word) else dao.renameList(oldKey, newName)
                when (result) {
                    VocabularyListWriteResult.NameTaken -> error = "A list with this name already exists."
                    VocabularyListWriteResult.Missing -> error = "This list was removed. Close and try again."
                    VocabularyListWriteResult.Updated -> {
                        if (oldKey != null) onRenamed(oldKey, vocabularyListKey(newName))
                        editing = false
                    }
                }
            }
        }) { Text(if (editKey == null) "Create" else "Save") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { editing = false }) { Text("Cancel") } }
    )
    deleting?.let { list -> AlertDialog(
        onDismissRequest = { if (!busy) deleting = null },
        title = { Text("Delete ${list.name}?") },
        text = { Text("The list will be removed. Its words will remain in Saved words and in other lists.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { write { dao.deleteList(list.nameKey); deleting = null } }) { Text("Delete list") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { deleting = null }) { Text("Cancel") } }
    ) }
}
