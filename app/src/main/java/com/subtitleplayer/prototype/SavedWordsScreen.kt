package com.subtitleplayer.prototype

import android.widget.Toast
import android.database.sqlite.SQLiteException
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun SavedWordsScreen(
    dao: SavedWordsDao,
    pronunciation: EnglishPronunciation,
    onOpenScene: (SavedWord) -> Unit,
    onBack: (() -> Unit)?,
    bottomBar: @Composable () -> Unit
) {
    DisposableEffect(pronunciation) { onDispose { pronunciation.stop() } }
    val words by remember(dao) { dao.observeAll() }.collectAsState(initial = null)
    val loadedWords = words
    var showStats by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val orderPrefs = remember(context) { context.getSharedPreferences("display_order", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var deletionCandidate by remember { mutableStateOf<SavedWord?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var exportRows by remember { mutableStateOf<List<VocabularyExportRow>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var exportFormat by remember { mutableStateOf(VocabularyExportFormat.Csv) }
    fun completeExport(uri: android.net.Uri?) {
        val format = exportFormat
        val snapshot = exportRows
        exportRows = null
        if (uri == null) {
            exporting = false
        } else if (snapshot == null) {
            exporting = false
            Toast.makeText(context, "Export again. This export could not be recovered.", Toast.LENGTH_LONG).show()
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IOException("No output stream")
                        stream.bufferedWriter(Charsets.UTF_8).use {
                            if (format == VocabularyExportFormat.Anki) writeAnkiVocabulary(snapshot, it)
                            else writeVocabularyCsv(snapshot, it)
                        }
                    }
                    Toast.makeText(context, "Exported ${snapshot.size} words.", Toast.LENGTH_LONG).show()
                } catch (_: IOException) {
                    Toast.makeText(context, "Could not save the export. Create it again.", Toast.LENGTH_LONG).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, "Could not write the file.", Toast.LENGTH_LONG).show()
                } finally {
                    exporting = false
                }
            }
        }
    }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { completeExport(it) }
    val createAnki = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { completeExport(it) }
    var managingDictionary by rememberSaveable { mutableStateOf(false) }

    var reviewing by rememberSaveable { mutableStateOf(false) }
    var reviewingSource by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(reviewing) {
        pronunciation.stop()
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(VocabularyFilter.All.name) }
    var selectedSource by rememberSaveable { mutableStateOf<String?>(null) }
    var orderName by rememberSaveable { mutableStateOf(orderPrefs.getString("words", WordDisplayOrder.Newest.name)) }
    var managingBackup by rememberSaveable { mutableStateOf(false) }
    if (managingBackup) {
        LearningBackupScreen(SavedWordsDatabase.get(context).backup(), onBack = { managingBackup = false })
        return
    }
    if (managingDictionary) {
        PersonalDictionaryScreen(SavedWordsDatabase.get(context).dictionary(), onBack = { managingDictionary = false })
        return
    }
    if (reviewing) {
        WordReviewScreen(dao, pronunciation, source = reviewingSource, onBack = { reviewing = false })
        return
    }
    val wordOrder = WordDisplayOrder.entries.firstOrNull { it.name == orderName } ?: WordDisplayOrder.Newest
    val orderedWords = remember(loadedWords, wordOrder) { orderedSavedWords(loadedWords.orEmpty(), wordOrder) }
    val filter = VocabularyFilter.entries.firstOrNull { it.name == filterName } ?: VocabularyFilter.All
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    var sourceMenuOpen by remember { mutableStateOf(false) }
    val sources = remember(loadedWords) {
        loadedWords.orEmpty().groupBy { it.sourceUri }.entries
            .sortedWith(compareBy({ it.value.first().sourceName.lowercase(Locale.ROOT) }, { it.key }))
    }
    LaunchedEffect(loadedWords, selectedSource) {
        if (loadedWords != null && selectedSource != null && sources.none { it.key == selectedSource }) {
            selectedSource = null
        }
    }
    val selectedSourceIndex = sources.indexOfFirst { it.key == selectedSource }
    val sourceLabel = sources.getOrNull(selectedSourceIndex)?.let {
        "${selectedSourceIndex + 1}. ${it.value.first().sourceName} (${it.value.size})"
    } ?: "All videos"
    val visibleWords = remember(orderedWords, query, filter, now, selectedSource) {
        val search = normalizeVocabularySearch(query.trim())
        orderedWords.filter { entry ->
            val matchesStatus = when (filter) {
                VocabularyFilter.All -> true
                VocabularyFilter.Due -> entry.reviewDueAt <= now
                VocabularyFilter.New -> entry.reviewCount == 0
            }
            matchesStatus && (selectedSource == null || entry.sourceUri == selectedSource) &&
                (search.isEmpty() || listOf(
                entry.word, entry.headword, entry.meaning, entry.dialogue, entry.sourceName
            ).any { search in normalizeVocabularySearch(it) })
        }
    }
    LaunchedEffect(query, filter, selectedSource, wordOrder) {
        pronunciation.stop()
        listState.scrollToItem(0)
    }
    val dueCount = loadedWords?.count { it.reviewDueAt <= now } ?: 0
    val sourceDueCount = loadedWords?.count { it.sourceUri == selectedSource && it.reviewDueAt <= now } ?: 0
    if (showStats && loadedWords != null) {
        VocabularyStatsDialog(loadedWords, now, onDismiss = { showStats = false })
    }
    deletionCandidate?.let { entry ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deletionCandidate = null },
            title = { Text("Delete ${entry.word}?") },
            text = { Text("This removes the card and its review progress. Your personal definition is kept.") },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    pronunciation.stop()
                    scope.launch {
                        try {
                            val removed = dao.deleteWord(entry.id)
                            deletionCandidate = null
                            if (removed == 1) {
                                val result = snackbar.showSnackbar(
                                    message = "Deleted “${entry.word}”",
                                    actionLabel = "Undo",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    if (dao.insertWord(entry) == -1L) {
                                        Toast.makeText(context, "A matching word already exists. The existing entry was kept.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } catch (_: SQLiteException) {
                            Toast.makeText(context, "Could not finish. Check Flashcards and try again.", Toast.LENGTH_LONG).show()
                        } finally {
                            deleting = false
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { deletionCandidate = null }) { Text("Cancel") }
            }
        )
    }
    var toolsOpen by remember { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var expandedWordId by rememberSaveable { mutableStateOf<Long?>(null) }
    fun export(format: VocabularyExportFormat) {
        keyboard?.hide()
        pronunciation.stop()
        exportRows = visibleWords.map { entry ->
            VocabularyExportRow(
                entry.word, entry.headword, entry.meaning, entry.dialogue, entry.sourceName,
                entry.positionMs, entry.reviewLevel, entry.reviewDueAt, entry.reviewCount
            )
        }
        exporting = true
        exportFormat = format
        try {
            if (format == VocabularyExportFormat.Anki) createAnki.launch("velvet-pines-anki.txt")
            else createCsv.launch("velvet-pines-vocabulary.csv")
        } catch (_: android.content.ActivityNotFoundException) {
            exporting = false
            exportRows = null
            Toast.makeText(context, "The file picker is unavailable.", Toast.LENGTH_LONG).show()
        }
    }

    Box(Modifier.fillMaxSize().pinesAtmosphere().safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Flashcards", style = MaterialTheme.typography.titleLarge)
                    if (loadedWords != null) {
                        Text("${loadedWords.size} saved words",
                            color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (onBack != null) TextButton(onClick = onBack) { Text("Back") }
                Box {
                    PlayerIconButton(PlayerGlyph.More, "Flashcard options", onClick = { toolsOpen = true })
                    DropdownMenu(expanded = toolsOpen, onDismissRequest = { toolsOpen = false }) {
                        DropdownMenuItem(text = { Text("Review stats") }, enabled = loadedWords != null, onClick = {
                            toolsOpen = false
                            keyboard?.hide()
                            pronunciation.stop()
                            showStats = true
                        })
                        DropdownMenuItem(text = { Text("Personal dictionary") }, onClick = {
                            toolsOpen = false
                            keyboard?.hide()
                            managingDictionary = true
                        })
                        DropdownMenuItem(text = { Text("Backup and restore") }, onClick = {
                            toolsOpen = false
                            keyboard?.hide()
                            managingBackup = true
                        })
                        for (format in VocabularyExportFormat.entries) {
                            DropdownMenuItem(
                                text = { Text(if (format == VocabularyExportFormat.Anki) "Export results to Anki" else "Export results as CSV") },
                                enabled = visibleWords.isNotEmpty() && !exporting,
                                onClick = { toolsOpen = false; export(format) }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!loadedWords.isNullOrEmpty()) item(key = "controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (dueCount > 0) {
                            Button(
                                enabled = !exporting,
                                onClick = { keyboard?.hide(); reviewingSource = null; reviewing = true }
                            ) { Text("Review ${dueCount} due cards") }
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search words or meanings") },
                            singleLine = true,
                            trailingIcon = {
                                if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(onClick = {
                            keyboard?.hide()
                            filtersOpen = !filtersOpen
                        }) {
                            Text(when {
                                filtersOpen -> "Hide filters"
                                selectedSource != null || filter != VocabularyFilter.All -> "Filter and sort · Active"
                                else -> "Filter and sort"
                            })
                        }
                        if (filtersOpen) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                VocabularyFilter.entries.forEach { option ->
                                    FilterChip(
                                        selected = filter == option,
                                        onClick = { filterName = option.name },
                                        label = { Text(option.label) }
                                    )
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DisplayOrderMenu(
                                    wordOrder.name, wordOrder.label,
                                    WordDisplayOrder.entries.map { it.name to it.label },
                                    onSelect = {
                                        keyboard?.hide()
                                        orderName = it
                                        orderPrefs.edit().putString("words", it).apply()
                                    }
                                )
                                Box {
                                    TextButton(onClick = { keyboard?.hide(); sourceMenuOpen = true }) {
                                        Text(sourceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    DropdownMenu(
                                        expanded = sourceMenuOpen,
                                        onDismissRequest = { sourceMenuOpen = false },
                                        modifier = Modifier.heightIn(max = 320.dp)
                                    ) {
                                        DropdownMenuItem(text = { Text("All videos") }, onClick = {
                                            selectedSource = null
                                            sourceMenuOpen = false
                                        })
                                        sources.forEach { source ->
                                            DropdownMenuItem(
                                                text = { Text(source.value.first().sourceName) },
                                                onClick = { selectedSource = source.key; sourceMenuOpen = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (selectedSource != null && sourceDueCount > 0) {
                            TextButton(enabled = !exporting, onClick = {
                                keyboard?.hide()
                                reviewingSource = selectedSource
                                reviewing = true
                            }) { Text("Review selected video ($sourceDueCount)") }
                        }
                        Text("Tap a word to reveal its meaning.", color = PinesPalette.Muted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                when {
                    loadedWords.isNullOrEmpty() -> item(key = "empty") {
                        Text(
                            if (loadedWords == null) "Loading words…"
                            else "Tap a word in the subtitles and choose Save.",
                            Modifier.padding(vertical = 24.dp)
                        )
                    }
                    visibleWords.isEmpty() -> item(key = "no-results") {
                        Column {
                            Text("No words match your search and filters.")
                            TextButton(onClick = {
                                query = ""
                                filterName = VocabularyFilter.All.name
                                selectedSource = null
                            }) { Text("Show all words") }
                        }
                    }
                    else -> items(visibleWords, key = SavedWord::id) { entry ->
                        val expanded = expandedWordId == entry.id
                        SavedWordCard(
                            entry, pronunciation, now, expanded, actionsEnabled = !deleting && !exporting,
                            onToggle = {
                                keyboard?.hide()
                                pronunciation.stop()
                                expandedWordId = if (expanded) null else entry.id
                            },
                            onOpenScene = { keyboard?.hide(); onOpenScene(entry) },
                            onDelete = { keyboard?.hide(); deletionCandidate = entry }
                        )
                    }
                }
            }
            bottomBar()
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }
}

@Composable
private fun SavedWordCard(
    entry: SavedWord,
    pronunciation: EnglishPronunciation,
    now: Long,
    expanded: Boolean,
    actionsEnabled: Boolean,
    onToggle: () -> Unit,
    onOpenScene: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = MaterialTheme.shapes.medium
    Card(
        Modifier.fillMaxWidth().then(if (expanded) Modifier.glassPanel(shape) else Modifier),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (expanded) Color.Transparent else PinesPalette.Card)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(onClickLabel = if (expanded) "Hide details" else "Show meaning", onClick = onToggle)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(entry.word, Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
            Text(if (expanded) "−" else "+", color = PinesPalette.Accent)
        }
        if (expanded) {
            HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = PinesPalette.Border)
            Column(
                Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (!entry.word.equals(entry.headword, ignoreCase = true)) {
                    Text(entry.headword, color = PinesPalette.Muted,
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr))
                }
                Text(
                    entry.meaning,
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl)
                )
                Text(
                    entry.dialogue,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
                )
                Text(
                    "${entry.sourceName} · ${formatTime(entry.positionMs)}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    when {
                        entry.reviewCount == 0 -> "New · Not reviewed"
                        entry.reviewDueAt <= now -> "Due for review"
                        else -> "Next review: " + DateFormat.getDateTimeInstance(
                            DateFormat.SHORT, DateFormat.SHORT, Locale.ENGLISH
                        ).format(Date(entry.reviewDueAt))
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                PronunciationControls(pronunciation, entry.word, entry.dialogue)
                TextButton(enabled = actionsEnabled, onClick = onOpenScene) { Text("Go to scene") }
                TextButton(enabled = actionsEnabled, onClick = onDelete) { Text("Remove from flashcards") }
            }
        }
    }
}

private enum class VocabularyFilter(val label: String) {
    All("All"), Due("Due for review"), New("New")
}

private fun normalizeVocabularySearch(value: String): String =
    value.lowercase(Locale.ROOT).replace(Char(0x2019), Char(0x27))
        .replace(Char(0x064A), Char(0x06CC)).replace(Char(0x0643), Char(0x06A9))
        .replace(Char(0x200C), Char(0x20))
