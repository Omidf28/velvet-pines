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
import kotlinx.coroutines.withContext

/** Independent offline lookup; opening the window does not alter playback or its bounds. */
@Composable
internal fun DictionarySearchPanel(
    dictionary: PackagedDictionary,
    personal: Map<String, WordMeaning>,
    personalReady: Boolean,
    dao: PersonalDictionaryDao,
    speech: EnglishPronunciation,
    initialTerm: String,
    beforeSpeak: () -> Unit,
    onDismiss: () -> Unit,
    bottomBar: (@Composable () -> Unit)? = null,
    onReturnToPlayback: (() -> Unit)? = null
) {
    var query by rememberSaveable { mutableStateOf(initialTerm) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val prefix = remember(query) { normalizeDictionaryPrefix(query) }
    val exactKey = remember(query) { normalizeDictionaryTerm(query) }
    val browsing = query.isBlank()
    var pageStarts by rememberSaveable(query) { mutableStateOf(listOf("")) }
    val after = pageStarts.last()
    var results by remember(query, after, personal, retry) { mutableStateOf(DictionarySearchResult(emptyList(), false)) }
    var searching by remember(query, after, personal, retry) { mutableStateOf(browsing || prefix != null) }
    var failed by remember(query, after, personal, retry) { mutableStateOf(false) }
    var detail by remember(selected, personal, retry) { mutableStateOf<DictionaryResult?>(null) }
    var detailFailed by remember(selected, personal, retry) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(query, after, personal, retry) {
        if (!browsing && prefix == null) return@LaunchedEffect
        if (!browsing) delay(200)
        try {
            results = withContext(Dispatchers.IO) { dictionary.search(query, personal, after) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        searching = false
        listState.scrollToItem(0)
    }
    LaunchedEffect(selected, personal, retry) {
        val term = selected ?: return@LaunchedEffect
        try {
            detail = withContext(Dispatchers.IO) { dictionary.resolve(term, personal) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { detailFailed = true }
    }
    DisposableEffect(speech) { onDispose { speech.stop() } }
    fun close() { keyboard?.hide(); speech.stop(); onDismiss() }
    fun select(term: String) { keyboard?.hide(); speech.stop(); selected = term }
    BackHandler(enabled = selected != null && !editing) {
        keyboard?.hide()
        speech.stop()
        selected = null
    }
    val content: @Composable ColumnScope.() -> Unit = {
        val term = selected
        if (term == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(160) },
                label = { Text("English word or phrase") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                trailingIcon = if (query.isEmpty()) null else ({ TextButton(onClick = { query = "" }) { Text("Clear") } }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "status") {
                    Text(when {
                        !browsing && prefix == null -> "Use English letters. Up to 8 words and 80 characters."
                        searching -> "Searching…"
                        failed -> "Could not read the dictionary. Check available storage and try again."
                        browsing && results.entries.isEmpty() -> "No more entries."
                        browsing -> "Page ${pageStarts.size} · Tap a word to see its meaning."
                        results.entries.isEmpty() -> "No matches for this search. You can add a personal definition."
                        results.hasMore -> "First ${results.entries.size} results. Type more to narrow the search."
                        else -> "${results.entries.size} results"
                    }, color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall)
                    if (failed) TextButton(onClick = { retry++ }) { Text("Try again") }
                    if (exactKey != null) TextButton(onClick = { select(exactKey) }) {
                        Text("Show meaning for “$exactKey”", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!searching && !failed) items(results.entries, key = { it.term }) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = PinesPalette.Card), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Show full meaning") { select(entry.term) }
                            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.term, color = PinesPalette.Accent,
                                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                            if (!browsing) Text(entry.meaning.persian, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl))
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
                TextButton(onClick = { speech.stop(); selected = null }) { Text("Results") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val result = detail
                    Text(if (detailFailed) "Could not load the meaning. Try again."
                        else if (result == null) "Loading meaning…"
                        else result.meaning?.persian ?: "No offline meaning found. You can add a personal definition.",
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr))
                    result?.let { Text(it.source, color = PinesPalette.Muted, style = MaterialTheme.typography.labelSmall) }
                    if (detailFailed) TextButton(onClick = { retry++ }) { Text("Try again") }
                }
                item {
                    PronunciationControls(speech, term, contentColor = PinesPalette.Accent, beforeSpeak = beforeSpeak)
                    TextButton(enabled = personalReady && (detail != null || detailFailed), onClick = {
                        speech.stop(); editing = true
                    }) { Text(if (term in personal) "Edit personal definition" else "Add personal definition") }
                    Text("Personal definitions take priority across videos. To save a word with its line and timestamp, use the card in the player.",
                        color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall)
                }
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
        Scaffold(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = bottomBar
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
        }
    }
}
