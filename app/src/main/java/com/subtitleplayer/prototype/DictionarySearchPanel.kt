package com.subtitleplayer.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Independent offline lookup; opening the window does not alter playback or its bounds. */
@Composable
internal fun DictionarySearchPanel(
    dictionary: PackagedDictionary,
    englishDictionary: PackagedEnglishDictionary,
    personal: Map<String, WordMeaning>,
    personalReady: Boolean,
    dao: PersonalDictionaryDao,
    bookmarksDao: DictionaryBookmarksDao,
    speech: EnglishPronunciation,
    initialTerm: String,
    beforeSpeak: () -> Unit,
    onDismiss: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    onReturnToPlayback: (() -> Unit)? = null
) {
    var query by rememberSaveable { mutableStateOf(initialTerm) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var entryHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var editing by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var savedOnly by rememberSaveable { mutableStateOf(false) }
    var levelName by rememberSaveable { mutableStateOf<String?>(null) }
    val level = DictionaryLevel.entries.firstOrNull { it.name == levelName }
    var showLevelMenu by remember { mutableStateOf(false) }
    val bookmarks by remember(bookmarksDao) { bookmarksDao.observeAll() }.collectAsState(initial = null)
    val savedByKey = remember(bookmarks) { bookmarks.orEmpty().associateBy { it.wordKey } }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var saveErrorTerm by remember { mutableStateOf<String?>(null) }
    val prefix = remember(query) { normalizeDictionaryPrefix(query) }
    val persianTokens = remember(query) { persianSearchTokens(query) }
    val validQuery = prefix != null || persianTokens != null
    val exactKey = remember(query) { normalizeDictionaryTerm(query) }
    val browsing = query.isBlank()
    var pageStarts by rememberSaveable(query, savedOnly, level) { mutableStateOf(listOf("")) }
    val after = pageStarts.last()
    var results by remember(query, after, personal, bookmarks, savedOnly, level, retry) { mutableStateOf(DictionarySearchResult(emptyList(), false)) }
    var searching by remember(query, after, personal, bookmarks, savedOnly, level, retry) { mutableStateOf(browsing || validQuery) }
    var failed by remember(query, after, personal, bookmarks, savedOnly, level, retry) { mutableStateOf(false) }
    var detail by remember(selected, personal, bookmarks, retry) { mutableStateOf<DictionaryResult?>(null) }
    var detailFailed by remember(selected, personal, bookmarks, retry) { mutableStateOf(false) }
    var profiles by remember(selected, retry) { mutableStateOf<List<LearnerProfile>>(emptyList()) }
    var profileFailed by remember(selected, retry) { mutableStateOf(false) }
    var englishEntries by remember(selected, retry) { mutableStateOf<List<EnglishLexicalEntry>>(emptyList()) }
    var englishLoading by remember(selected, retry) { mutableStateOf(selected != null) }
    var englishFailed by remember(selected, retry) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(query, after, personal, bookmarks, savedOnly, level, retry) {
        if (!browsing && !validQuery) return@LaunchedEffect
        if (savedOnly && bookmarks == null) return@LaunchedEffect
        if (!browsing) delay(200)
        try {
            results = withContext(Dispatchers.IO) {
                val eligible = level?.let { dictionary.levelTerms(it) }
                if (savedOnly) searchDictionaryBookmarks(bookmarks.orEmpty(), query, after, personal,
                    allowed = eligible)
                else englishDictionary.completeExactSearch(dictionary.search(query, personal, after, level), query, eligible)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        searching = false
        listState.requestScrollToItem(0)
    }
    LaunchedEffect(selected, retry) {
        val term = selected ?: return@LaunchedEffect
        try {
            englishEntries = withContext(Dispatchers.IO) { englishDictionary.lookup(term) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { englishFailed = true }
        englishLoading = false
    }
    LaunchedEffect(selected, retry) {
        val term = selected ?: return@LaunchedEffect
        try {
            profiles = withContext(Dispatchers.IO) { dictionary.profiles(term) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { profileFailed = true }
    }
    LaunchedEffect(selected, personal, bookmarks, retry) {
        val term = selected ?: return@LaunchedEffect
        val saved = savedByKey[term]?.let {
            DictionaryResult(WordMeaning(it.headword, it.meaning), "Saved dictionary entry")
        }
        try {
            val result = withContext(Dispatchers.IO) { dictionary.resolve(term, personal) }
            detail = if (result.meaning == null && saved != null) saved else result
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            detail = immediateDictionaryMeaning(term, personal) ?: saved
            detailFailed = detail == null
        }
    }
    DisposableEffect(speech) { onDispose { speech.stop() } }
    fun close() { keyboard?.hide(); speech.stop(); onDismiss() }
    fun select(term: String) { keyboard?.hide(); speech.stop(); entryHistory = emptyList(); selected = term }
    fun selectRelated(term: String) {
        val key = normalizeDictionaryTerm(term) ?: return
        if (key == selected) return
        keyboard?.hide()
        speech.stop()
        selected?.let { entryHistory = entryHistory + it }
        selected = key
    }
    val content: @Composable ColumnScope.() -> Unit = {
        BackHandler(enabled = selected != null && !editing) {
            keyboard?.hide()
            speech.stop()
            selected = entryHistory.lastOrNull()
            entryHistory = entryHistory.dropLast(1)
        }
        val term = selected
        if (term == null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !savedOnly, onClick = { savedOnly = false }, label = { Text("All words") })
                FilterChip(selected = savedOnly, onClick = { savedOnly = true }, label = { Text("Saved words") })
                Box {
                    TextButton(onClick = { showLevelMenu = true }) { Text(level?.let { "Level · ${it.name}" } ?: "All levels") }
                    DropdownMenu(expanded = showLevelMenu, onDismissRequest = { showLevelMenu = false }) {
                        DropdownMenuItem(text = { Text("All levels") }, onClick = { levelName = null; showLevelMenu = false })
                        for (option in DictionaryLevel.entries) DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = { levelName = option.name; showLevelMenu = false }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(160) },
                label = { Text("English word or Persian meaning") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                trailingIcon = if (query.isEmpty()) null else ({ TextButton(onClick = { query = "" }) { Text("Clear") } }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr)
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "status") {
                    Text(when {
                        !browsing && !validQuery -> "Use English or Persian letters. Up to 8 words and 80 characters."
                        searching -> "Searching…"
                        failed -> "Could not read the dictionary. Check available storage and try again."
                        level != null && results.entries.isEmpty() -> "No matches at this level. Try another level or clear the search."
                        savedOnly && results.entries.isEmpty() -> "No saved words match. Open a word and choose Save word."
                        browsing && results.entries.isEmpty() -> "No more entries."
                        browsing -> "Page ${pageStarts.size} · Tap a word to see its meaning."
                        results.entries.isEmpty() -> "No matches for this search. You can add a personal definition."
                        results.hasMore -> "First ${results.entries.size} results. Type more to narrow the search."
                        else -> "${results.entries.size} results"
                    }, color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall)
                    if (failed) TextButton(onClick = { retry++ }) { Text("Try again") }
                    if (exactKey != null && !savedOnly && level == null) TextButton(onClick = { select(exactKey) }) {
                        Text("Show meaning for “$exactKey”", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!searching && !failed) items(results.entries, key = { it.term }) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = PinesPalette.Card), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Show full meaning") { select(entry.term) }
                            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(entry.term, Modifier.weight(1f), color = PinesPalette.Accent,
                                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                                if (entry.term in savedByKey) Text("Saved", color = PinesPalette.Accent,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            if (!browsing) Text(entry.meaning.persian, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr))
                            Text(entry.source, style = MaterialTheme.typography.labelSmall, color = PinesPalette.Muted)
                        }
                    }
                }
            }
            if (browsing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    enabled = pageStarts.size > 1 && !searching,
                    onClick = { pageStarts = pageStarts.dropLast(1) }
                ) { Text("Previous") }
                TextButton(
                    enabled = !searching && !failed && results.hasMore,
                    onClick = { pageStarts = pageStarts + results.entries.last().term }
                ) { Text("Next") }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(term, Modifier.weight(1f), color = PinesPalette.Accent,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                TextButton(onClick = { speech.stop(); entryHistory = emptyList(); selected = null }) { Text("Results") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val result = detail
                    Text(if (detailFailed) "Could not load the meaning. Try again."
                        else if (result == null) "Loading meaning…"
                        else result.meaning?.persian ?: "No Persian meaning found. You can add a personal definition.",
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr))
                    result?.let { Text(it.source, color = PinesPalette.Muted, style = MaterialTheme.typography.labelSmall) }
                    if (detailFailed) TextButton(onClick = { retry++ }) { Text("Try again") }
                    if (profiles.isNotEmpty()) {
                        Text("CEFR vocabulary profile", style = MaterialTheme.typography.titleSmall)
                        for (profile in profiles) Text(
                            listOf(term, profile.level, profile.partOfSpeech, profile.source)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (profileFailed && !detailFailed) TextButton(onClick = { retry++ }) {
                        Text("Retry vocabulary profile")
                    }
                }
                item {
                    val saved = savedByKey[term]
                    val englishDefinition = englishEntries.asSequence()
                        .flatMap { it.senses.asSequence() }
                        .flatMap { it.definitions.asSequence() }
                        .mapNotNull(::validPersonalMeaning).firstOrNull()
                    val meaning = detail?.meaning ?: englishDefinition?.takeIf { detail != null || detailFailed }
                        ?.let { WordMeaning(term, it) }
                    OutlinedButton(
                        enabled = bookmarks != null && !saving && (saved != null || (personalReady && meaning != null)),
                        onClick = {
                            saving = true
                            saveErrorTerm = null
                            scope.launch {
                                try {
                                    if (saved != null) bookmarksDao.remove(term)
                                    else if (meaning != null) bookmarksDao.save(
                                        DictionaryBookmark(term, meaning.headword, meaning.persian.trim())
                                    )
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { saveErrorTerm = term }
                                finally { saving = false }
                            }
                        }
                    ) { Text(if (saved != null) "Remove from saved" else "Save word") }
                    if (saveErrorTerm == term) Text("Could not update saved words. Try again.",
                        color = MaterialTheme.colorScheme.error)
                    PronunciationControls(speech, term, contentColor = PinesPalette.Accent, beforeSpeak = beforeSpeak)
                    TextButton(enabled = personalReady && (detail != null || detailFailed), onClick = {
                        speech.stop(); editing = true
                    }) { Text(if (term in personal) "Edit personal definition" else "Add personal definition") }
                    Text("Personal definitions take priority across videos. To save a word with its line and timestamp, use the card in the player.",
                        color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall)
                }
                englishDictionarySection(englishEntries, englishLoading, englishFailed,
                    onRetry = { retry++ }, onSelectWord = ::selectRelated)
            }
            if (editing) PersonalMeaningDialog(
                dao, term, detail?.meaning, term in personal,
                onSaved = { retry++ }, onDismiss = { editing = false }
            )
        }
    }
    if (bottomBar == null) {
        PlayerOverlayPanel("English–Persian dictionary", onDismiss = ::close, content = content)
    } else {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Dictionary", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (onReturnToPlayback != null) PlayerIconButton(PlayerGlyph.Play, "Return to video", onClick = {
                            keyboard?.hide()
                            speech.stop()
                            onReturnToPlayback()
                        })
                    }
                    content()
                }
                bottomBar()
            }
        }
    }
}
