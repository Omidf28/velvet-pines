package com.subtitleplayer.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class StreamsBrowseState {
    var address by mutableStateOf("")
    var query by mutableStateOf("")
    var catalogs by mutableStateOf<Map<String, List<StreamIndexEntry>>>(emptyMap())
    var message by mutableStateOf<String?>(null)

    fun addCatalog(url: String, entries: List<StreamIndexEntry>) {
        catalogs = ((catalogs - url).toList() + (url to entries)).takeLast(5).toMap()
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
    val listState = rememberLazyListState()
    val catalogs = state.catalogs
    val query = state.query.trim()
    val entries = remember(catalogs, query) {
        val terms = streamSearchTerms(query)
        catalogs.values.flatten().distinctBy { it.link.url }.filter {
            terms.all { term -> it.title.contains(term, ignoreCase = true) || it.fileName.contains(term, ignoreCase = true) }
        }.sortedWith { a, b -> compareNaturalNames(a.title, b.title) }
    }

    Column(Modifier.fillMaxSize().pinesAtmosphere().safeDrawingPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Streams", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (onReturnToPlayback != null) {
                PlayerIconButton(PlayerGlyph.Play, "Return to video", onClick = onReturnToPlayback)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "stream-source") {
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
                            } else {
                                keyboard?.hide()
                                loading = true
                                state.message = null
                                scope.launch {
                                    try {
                                        val found = withContext(Dispatchers.IO) { readStreamIndex(url) }
                                        if (found.isEmpty()) {
                                            state.message = "This index has no direct video links."
                                        } else {
                                            state.addCatalog(url.toString(), found)
                                            state.query = ""
                                            listState.requestScrollToItem(0)
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
                        }) { Text("Load index") }
                    }
                    Text(
                        "Play a direct link or search video links from an HTTPS index.",
                        color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall
                    )
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (catalogs.isNotEmpty()) {
                item(key = "stream-search") {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { state.query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Search titles or episodes") }
                    )
                }
                if (entries.isEmpty()) item { Text("No videos match your search.", color = PinesPalette.Muted) }
                items(entries, key = { remoteVideoId(it.link.url) }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = if (entry.title != entry.fileName) {
                            { Text(entry.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        } else null,
                        modifier = Modifier.clickable(onClickLabel = "Play stream") {
                            keyboard?.hide()
                            onPlay(entry.link)
                        },
                        colors = ListItemDefaults.colors(containerColor = PinesPalette.Card)
                    )
                }
            }
        }
        bottomBar()
    }
}
