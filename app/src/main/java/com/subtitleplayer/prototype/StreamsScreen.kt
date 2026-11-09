package com.subtitleplayer.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class StreamsBrowseState {
    private data class OpenPage(
        val page: StreamIndexEntry.Page,
        val entries: List<StreamIndexEntry>,
        val parentQuery: String,
        val parentIndex: Int,
        val parentOffset: Int
    )

    var address by mutableStateOf("")
    var query by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    val listState = LazyListState()
    private var catalogs by mutableStateOf<Map<String, List<StreamIndexEntry>>>(emptyMap())
    private var pages by mutableStateOf<List<OpenPage>>(emptyList())
    val currentPage get() = pages.lastOrNull()?.page
    val entries get() = pages.lastOrNull()?.entries ?: catalogs.values.flatten()

    fun addCatalog(url: String, entries: List<StreamIndexEntry>) {
        catalogs = ((catalogs - url).toList() + (url to entries)).takeLast(5).toMap()
        resetList()
    }

    fun canOpen(page: StreamIndexEntry.Page): Boolean =
        pages.size < 8 && pages.none { it.page.address == page.address }

    fun openPage(page: StreamIndexEntry.Page, entries: List<StreamIndexEntry>) {
        pages = pages + OpenPage(page, entries, query, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        resetList()
    }

    fun back() {
        val current = pages.lastOrNull() ?: return
        pages = pages.dropLast(1)
        query = current.parentQuery
        message = null
        listState.requestScrollToItem(current.parentIndex, current.parentOffset)
    }

    private fun resetList() {
        query = ""
        message = null
        listState.requestScrollToItem(0)
    }
}

@Composable
internal fun StreamsScreen(
    state: StreamsBrowseState,
    onPlay: (OnlineVideoLink) -> Unit,
    onReturnToPlayback: (() -> Unit)?,
    bottomBar: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var loading by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf<Job?>(null) }
    val page = state.currentPage
    val sourceEntries = state.entries
    val query = state.query.trim()
    val entries = remember(sourceEntries, query) {
        val terms = streamSearchTerms(query)
        sourceEntries.distinctBy { it.url }.filter {
            terms.all { term -> it.title.contains(term, ignoreCase = true) || it.fileName.contains(term, ignoreCase = true) }
        }.sortedWith { a, b -> compareNaturalNames(a.title, b.title) }
    }

    fun back() {
        keyboard?.hide()
        if (loading) request?.cancel() else state.back()
    }
    BackHandler(enabled = loading || page != null, onBack = ::back)

    fun loadIndex(url: URI, child: StreamIndexEntry.Page? = null) {
        keyboard?.hide()
        if (child != null && !state.canOpen(child)) {
            state.message = "This page is already open or the index is too deeply nested."
            return
        }
        loading = true
        state.message = null
        request = scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { readStreamIndex(url) }
                when {
                    found.isEmpty() -> state.message = "This index has no video or catalog links."
                    child == null -> state.addCatalog(url.toString(), found)
                    else -> state.openPage(child, found)
                }
            } catch (error: StreamIndexHttpException) {
                state.message = "Index server returned HTTP ${error.status}."
            } catch (_: IOException) {
                state.message = "Could not load this index. Check the URL or try again."
            } catch (_: SecurityException) {
                state.message = "Access to this index was denied."
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize().pinesAtmosphere().safeDrawingPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page != null) PlayerIconButton(PlayerGlyph.Back, "Parent index", onClick = ::back)
            Text(page?.title ?: "Streams", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onReturnToPlayback != null) {
                PlayerIconButton(PlayerGlyph.Play, "Return to video", onClick = onReturnToPlayback)
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let {
            Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = state.listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (page == null) item(key = "stream-source") {
                Column(
                    Modifier.fillMaxWidth().glassPanel(MaterialTheme.shapes.large).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.address,
                        onValueChange = { state.address = it; state.message = null },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading,
                        singleLine = true,
                        label = { Text("Video or index URL") }
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !loading && state.address.isNotBlank(), onClick = {
                            keyboard?.hide()
                            val link = parseOnlineVideoLink(state.address)
                            if (link == null || link.format == OnlineVideoFormat.Smb) {
                                state.message = "Enter a direct HTTPS or RTSP video URL."
                            } else {
                                onPlay(link)
                            }
                        }) { Text("Play URL") }
                        OutlinedButton(enabled = !loading && state.address.isNotBlank(), onClick = {
                            val url = parseStreamIndexUrl(state.address)
                            if (url == null) {
                                state.message = "Enter an HTTPS index URL."
                            } else loadIndex(url)
                        }) { Text("Load index") }
                    }
                    Text(
                        "Play a direct link or browse an HTTPS index.",
                        color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (sourceEntries.isNotEmpty()) {
                item(key = "stream-search") {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { state.query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search titles or episodes") }
                    )
                }
                if (entries.isEmpty()) item { Text("No results match your search.", color = PinesPalette.Muted) }
                items(entries, key = { remoteVideoId(it.url) }) { entry ->
                    val isPage = entry is StreamIndexEntry.Page
                    ListItem(
                        headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = if (isPage) ({ HomeNavigationIcon(folder = true, ink = PinesPalette.Accent) }) else null,
                        supportingContent = if (!isPage && entry.title != entry.fileName) {
                            { Text(entry.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        } else null,
                        modifier = Modifier.clickable(enabled = !loading, onClickLabel = if (isPage) "Open index" else "Play stream") {
                            keyboard?.hide()
                            when (entry) {
                                is StreamIndexEntry.Page -> loadIndex(entry.address, entry)
                                is StreamIndexEntry.Video -> onPlay(entry.link)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = PinesPalette.Card)
                    )
                }
            }
        }
        bottomBar()
    }
}
