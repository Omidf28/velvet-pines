package com.subtitleplayer.prototype

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
    private var loadedEpisodes by mutableStateOf<Map<String, List<StreamSearchResult>>>(emptyMap())
    private var scannedEpisodes by mutableStateOf<Map<String, List<StreamSearchResult>>>(emptyMap())
    val currentPage get() = pages.lastOrNull()?.page
    val currentLocation get() = pages.takeIf { it.isNotEmpty() }?.joinToString(" / ") { it.page.title }
    val entries get() = pages.lastOrNull()?.entries ?: catalogs.values.flatten()
    val results by derivedStateOf {
        val terms = streamSearchTerms(query)
        val current = entries.map { StreamSearchResult(it) }
        val candidates = if (currentPage == null && terms.isNotEmpty()) {
            current + loadedEpisodes.values.flatten() + scannedEpisodes.values.flatten()
        } else current
        candidates.filter { result ->
            matchesStreamSearch(terms, result.location, result.entry.title, result.entry.fileName)
        }.distinctBy { it.entry.url }
            .sortedWith { a, b -> compareNaturalNames(a.entry.title, b.entry.title) }
    }

    fun addCatalog(url: String, entries: List<StreamIndexEntry>) {
        catalogs = ((catalogs - url).toList() + (url to entries)).takeLast(5).toMap()
        resetList()
    }

    fun addScan(url: String, scan: StreamCatalogScan) {
        addCatalog(url, scan.rootEntries)
        scannedEpisodes = ((scannedEpisodes - url).toList() + (url to scan.episodes)).takeLast(3).toMap()
        val found = (scan.rootEntries.filterIsInstance<StreamIndexEntry.Video>().map { it.url } +
            scan.episodes.map { it.entry.url }).distinct().size
        message = "$found playable links from ${scan.pagesRead} pages." +
            if (scan.failedPages > 0) " Could not load ${scan.failedPages} linked pages." else ""
    }

    fun canOpen(page: StreamIndexEntry.Page): Boolean =
        pages.size < 8 && pages.none { it.page.address == page.address }

    fun openPage(page: StreamIndexEntry.Page, entries: List<StreamIndexEntry>) {
        val location = listOfNotNull(currentLocation, page.title).joinToString(" / ")
        val episodes = entries.filterIsInstance<StreamIndexEntry.Video>().map { StreamSearchResult(it, location) }
        loadedEpisodes = ((loadedEpisodes - page.url).toList() + (page.url to episodes)).takeLast(16).toMap()
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
    bookmarks: StreamPlaybackBookmarks,
    onPlay: (OnlineVideoLink, String?) -> Unit,
    onReturnToPlayback: (() -> Unit)?,
    bottomBar: @Composable () -> Unit
) {
    val context = LocalContext.current
    val sourcePrefs = remember(context) { context.getSharedPreferences("stream_sources", Context.MODE_PRIVATE) }
    var savedSources by remember(sourcePrefs) {
        mutableStateOf(sourcePrefs.getString("indexes", "").orEmpty().lineSequence()
            .mapNotNull(::savedStreamSource).distinct().take(10).toList())
    }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var loading by remember { mutableStateOf(false) }
    var request by remember { mutableStateOf<Job?>(null) }
    val page = state.currentPage
    val sourceEntries = state.entries
    val results = state.results

    fun back() {
        keyboard?.hide()
        if (loading) request?.cancel() else state.back()
    }
    BackHandler(enabled = loading || page != null, onBack = ::back)

    fun updateSources(sources: List<URI>) {
        savedSources = sources
        sourcePrefs.edit().putString("indexes", sources.joinToString("\n")).apply()
    }

    fun loadIndex(url: URI, child: StreamIndexEntry.Page? = null, scan: Boolean = false) {
        keyboard?.hide()
        if (child != null && !state.canOpen(child)) {
            state.message = "This page is already open or the index is too deeply nested."
            return
        }
        loading = true
        state.message = null
        request = scope.launch {
            try {
                if (scan) {
                    val catalog = withContext(Dispatchers.IO) { withTimeout(45_000) { scanStreamCatalog(url) } }
                    state.addScan(url.toString(), catalog)
                } else {
                    val found = withContext(Dispatchers.IO) { readStreamIndex(url) }
                    when {
                        found.isEmpty() -> state.message = "This index has no video or catalog links."
                        child == null -> state.addCatalog(url.toString(), found)
                        else -> state.openPage(child, found)
                    }
                }
            } catch (_: TimeoutCancellationException) {
                state.message = "The archive scan timed out. Try a smaller index."
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
        if (loading) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(Modifier.weight(1f))
            TextButton(onClick = ::back) { Text("Cancel") }
        }
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
                                onPlay(link, null)
                            }
                        }) { Text("Play URL") }
                        OutlinedButton(enabled = !loading && state.address.isNotBlank(), onClick = {
                            val url = parseStreamIndexUrl(state.address)
                            if (url == null) {
                                state.message = "Enter an HTTPS index URL."
                            } else loadIndex(url)
                        }) { Text("Load index") }
                        OutlinedButton(enabled = !loading && state.address.isNotBlank(), onClick = {
                            val url = parseStreamIndexUrl(state.address)
                            if (url == null) state.message = "Enter an HTTPS index URL."
                            else loadIndex(url, scan = true)
                        }) { Text("Scan archive") }
                        TextButton(enabled = !loading && state.address.isNotBlank(), onClick = {
                            val url = savedStreamSource(state.address)
                            when {
                                url == null -> state.message = "Save a public HTTPS index URL without query parameters."
                                url in savedSources -> state.message = "This source is already saved."
                                savedSources.size >= 10 -> state.message = "Remove a source before saving another."
                                else -> {
                                    updateSources(savedSources + url)
                                    state.message = "Source saved."
                                }
                            }
                        }) { Text("Save source") }
                    }
                    Text(
                        "Load one index or scan up to 12 pages on the same server.",
                        color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (page == null && savedSources.isNotEmpty()) {
                item(key = "saved-stream-heading") { Text("Saved sources", style = MaterialTheme.typography.titleSmall) }
                item(key = "saved-stream-sources") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedSources, key = { remoteVideoId(it.toString()) }) { url ->
                            ListItem(
                                headlineContent = { Text(url.host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(url.path.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingContent = {
                                    TextButton(enabled = !loading, onClick = { updateSources(savedSources - url) }) { Text("Remove") }
                                },
                                modifier = Modifier.width(280.dp)
                                    .clickable(enabled = !loading, onClickLabel = "Use saved source") {
                                        state.address = url.toString()
                                        state.message = "Source selected. Choose Load index or Scan archive."
                                        state.listState.requestScrollToItem(0)
                                    },
                                colors = ListItemDefaults.colors(containerColor = PinesPalette.Card)
                            )
                        }
                    }
                }
            }
            if (sourceEntries.isNotEmpty()) {
                item(key = "stream-search") {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { state.query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (page == null) "Search loaded titles or episodes" else "Search this index") }
                    )
                }
                if (results.isEmpty()) item { Text("No results match your search.", color = PinesPalette.Muted) }
                items(results, key = { remoteVideoId(it.entry.url) }) { result ->
                    val entry = result.entry
                    val isPage = entry is StreamIndexEntry.Page
                    val details = result.location ?: entry.fileName.takeIf { !isPage && it != entry.title }
                    val bookmark = (entry as? StreamIndexEntry.Video)?.let { bookmarks[it.url] }
                    val status = bookmark?.statusLabel()
                    ListItem(
                        headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = if (isPage) ({ HomeNavigationIcon(folder = true, ink = PinesPalette.Accent) }) else null,
                        supportingContent = if (details != null || status != null) {
                            {
                                Column {
                                    details?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                    status?.let {
                                        Text(
                                            it,
                                            color = if (bookmark?.completed == true) PinesPalette.Success else PinesPalette.Muted,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        } else null,
                        modifier = Modifier.clickable(enabled = !loading, onClickLabel = if (isPage) "Open index" else "Play stream") {
                            keyboard?.hide()
                            when (entry) {
                                is StreamIndexEntry.Page -> loadIndex(entry.address, entry)
                                is StreamIndexEntry.Video -> onPlay(
                                    entry.link,
                                    streamEpisodeTitle(entry, result.location ?: state.currentLocation)
                                )
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
