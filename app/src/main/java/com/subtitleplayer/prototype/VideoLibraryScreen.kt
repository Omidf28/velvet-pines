package com.subtitleplayer.prototype

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class LibraryTab(val label: String) { Folder("Folders"), Recent("Recent") }

internal class LibraryBrowseState {
    var query by mutableStateOf("")
    val listState = LazyListState()
    var folderListing by mutableStateOf<LibraryFolderListing?>(null)
    var folderError by mutableStateOf(false)
    var scanRevision by mutableIntStateOf(0)
        private set

    fun refreshFolder() {
        folderListing = null
        folderError = false
        scanRevision++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoLibraryScreen(
    folder: Uri?,
    folderRevision: Int,
    folderPath: List<LibrarySubfolder>,
    onFolderPathChange: (List<LibrarySubfolder>) -> Unit,
    favoriteFolders: List<LibraryFolderShortcut>,
    recent: List<LibraryVideo>,
    tab: LibraryTab,
    onTabChange: (LibraryTab) -> Unit,
    browseState: LibraryBrowseState,
    includeSubfolders: Boolean,
    onIncludeSubfoldersChange: (Boolean) -> Unit,
    onChooseFolder: () -> Unit,
    onOpenFavorite: (LibraryFolderShortcut) -> Unit,
    onToggleFavorite: (LibraryFolderShortcut) -> Unit,
    onChooseFile: () -> Unit,
    onOpenLink: (OnlineVideoLink, SmbCredentials?) -> Unit,
    onOpen: (LibraryVideo) -> Unit,
    onRestart: (LibraryVideo) -> Unit,
    canReturnToPlayback: Boolean,
    skipSeconds: Int,
    onPlaySample: () -> Unit,
    bottomBar: @Composable () -> Unit,
    onDictionary: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val orderPrefs = remember(context) { context.getSharedPreferences("display_order", android.content.Context.MODE_PRIVATE) }
    var orderName by rememberSaveable { mutableStateOf(orderPrefs.getString("library", LibraryDisplayOrder.Default.name)) }
    val order = LibraryDisplayOrder.entries.firstOrNull { it.name == orderName } ?: LibraryDisplayOrder.Default
    val query = browseState.query
    var searchOpen by remember(browseState) { mutableStateOf(query.isNotEmpty()) }
    var requestSearchFocus by remember(browseState) { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var searchFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchEditing = searchFocused && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var showLibraryMenu by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var smbBrowse by remember { mutableStateOf<SmbBrowseRequest?>(null) }
    val folderListing = browseState.folderListing
    val folderError = browseState.folderError
    val libraryListState = browseState.listState

    fun changeQuery(value: String) {
        if (browseState.query == value) return
        browseState.query = value
        libraryListState.requestScrollToItem(0)
    }

    fun closeSearch() {
        focusManager.clearFocus()
        keyboard?.hide()
        changeQuery("")
        searchOpen = false
        requestSearchFocus = false
    }
    LaunchedEffect(searchOpen, requestSearchFocus) {
        if (searchOpen && requestSearchFocus) {
            searchFocus.requestFocus()
            keyboard?.show()
            requestSearchFocus = false
        }
    }

    val browse = smbBrowse
    if (browse != null) {
        SmbBrowserScreen(browse, onOpen = onOpenLink, onBack = { smbBrowse = null })
        return
    }
    fun navigateTo(path: List<LibrarySubfolder>) {
        keyboard?.hide()
        onFolderPathChange(path)
    }
    BackHandler(enabled = tab == LibraryTab.Folder && folderPath.isNotEmpty() && !searchEditing) {
        navigateTo(folderPath.dropLast(1))
    }
    BackHandler(enabled = searchOpen && !searchEditing, onBack = ::closeSearch)
    LaunchedEffect(browseState, resolver, browseState.scanRevision) {
        if (folder == null || tab != LibraryTab.Folder || folderListing != null || folderError) return@LaunchedEffect
        try {
            browseState.folderListing = withContext(Dispatchers.IO) {
                scanVideoFolder(resolver, folder, folderPath, includeSubfolders)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            browseState.folderError = true
        }
    }
    var folderNames by remember { mutableStateOf<Map<Uri, String>>(emptyMap()) }
    val namedFolders = remember(folder, favoriteFolders) {
        favoriteFolders.map { it.tree }.toSet() + listOfNotNull(folder)
    }
    LaunchedEffect(resolver, namedFolders, folderRevision, tab) {
        if (tab != LibraryTab.Folder) return@LaunchedEffect
        folderNames = withContext(Dispatchers.IO) {
            buildMap {
                for (uri in namedFolders) {
                    val name = try {
                        readFolderName(resolver, uri)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    put(uri, name ?: fallbackFolderName(uri))
                }
            }
        }
    }
    fun chooseFolder() {
        keyboard?.hide()
        onTabChange(LibraryTab.Folder)
        onChooseFolder()
    }
    fun folderName(uri: Uri): String = folderNames[uri] ?: fallbackFolderName(uri)
    val currentFolder = folder?.let { LibraryFolderShortcut(it, folderPath) }
    val currentIsFavorite = currentFolder != null && favoriteFolders.any { it.sameFolder(currentFolder) }
    val favoriteAction = if (currentIsFavorite) "Remove from favorites" else "Add to favorites"
    val bookmarksByKey = remember(recent) {
        buildMap<String, PlaybackBookmark> {
            recent.asReversed().forEach { video ->
                video.bookmark?.let { put(videoSubtitleKey(video.uri), it) }
            }
        }
    }
    val showRecent = tab == LibraryTab.Recent
    val videos = remember(showRecent, recent, folderListing, bookmarksByKey) {
        (if (showRecent) recent else folderListing?.videos.orEmpty()).map { video ->
            video.copy(bookmark = video.bookmark ?: bookmarksByKey[videoSubtitleKey(video.uri)])
        }
    }
    val orderedVideos = remember(videos, order) {
        val comparator = Comparator<LibraryVideo> { a, b ->
            val name = compareNaturalNames(a.name, b.name)
            val path = if (name == 0) compareNaturalNames(a.parentPath.orEmpty(), b.parentPath.orEmpty()) else name
            if (path != 0) path else a.uri.toString().compareTo(b.uri.toString())
        }
        when (order) {
            LibraryDisplayOrder.Default -> videos
            LibraryDisplayOrder.NameAscending -> videos.sortedWith(comparator)
            LibraryDisplayOrder.NameDescending -> videos.sortedWith(comparator.reversed())
        }
    }
    val search = query.trim()
    val filtered = remember(orderedVideos, search) {
        if (search.isEmpty()) orderedVideos else orderedVideos.filter {
            it.name.contains(search, ignoreCase = true) || it.parentPath?.contains(search, ignoreCase = true) == true
        }
    }
    val visibleFolders = remember(folderListing, search) {
        folderListing?.folders.orEmpty().filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
    }
    val continuation = if (showRecent && search.isEmpty()) videos.firstOrNull { it.bookmark?.resumable == true } else null
    Column(Modifier.fillMaxSize().pinesAtmosphere().safeDrawingPadding()) {
        if (!searchEditing) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PinesLogo(40.dp)
                Column(Modifier.weight(1f)) {
                    Text("Files", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Velvet Pines", color = PinesPalette.Muted, style = MaterialTheme.typography.labelSmall)
                }
                if (canReturnToPlayback) PlayerIconButton(PlayerGlyph.Play, "Return to video", onClick = onBack)
                if (!searchOpen && (showRecent || folder != null)) {
                    PlayerIconButton(PlayerGlyph.Search, "Search files", onClick = {
                        searchOpen = true
                        requestSearchFocus = true
                    })
                }
                Box {
                    PlayerIconButton(PlayerGlyph.More, "Files menu", onClick = { showLibraryMenu = true })
                    DropdownMenu(expanded = showLibraryMenu, onDismissRequest = { showLibraryMenu = false }) {
                        DropdownMenuItem(text = { Text("Open file") }, onClick = { showLibraryMenu = false; onChooseFile() })
                        DropdownMenuItem(text = { Text(if (folder == null) "Choose folder" else "Change root folder") },
                            onClick = { showLibraryMenu = false; chooseFolder() })
                        if (!showRecent && folder != null) {
                            DropdownMenuItem(
                                text = { Text("Refresh folder") },
                                enabled = folderListing != null || folderError,
                                onClick = { showLibraryMenu = false; browseState.refreshFolder() }
                            )
                            DropdownMenuItem(text = {
                                Text(if (includeSubfolders) "Show only this folder" else "Include subfolders")
                            }, onClick = {
                                showLibraryMenu = false
                                onIncludeSubfoldersChange(!includeSubfolders)
                                browseState.refreshFolder()
                                libraryListState.requestScrollToItem(0)
                            })
                        }
                        HorizontalDivider()
                        for (option in LibraryDisplayOrder.entries) {
                            val label = if (option == LibraryDisplayOrder.Default && showRecent) "Last watched" else option.label
                            DropdownMenuItem(text = { Text((if (order == option) "✓ " else "") + label) },
                                onClick = {
                                    showLibraryMenu = false
                                    if (orderName != option.name) libraryListState.requestScrollToItem(0)
                                    orderName = option.name
                                    orderPrefs.edit().putString("library", orderName).apply()
                                })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Open network stream") }, onClick = { showLibraryMenu = false; showLinkDialog = true })
                        DropdownMenuItem(text = { Text("Play sample") }, onClick = { showLibraryMenu = false; onPlaySample() })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Dictionary") }, onClick = { showLibraryMenu = false; onDictionary() })
                        DropdownMenuItem(text = { Text("Help") }, onClick = { showLibraryMenu = false; showHelp = true })
                    }
                }
            }
            if (!searchOpen) SecondaryTabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
                LibraryTab.entries.forEach { option ->
                    Tab(selected = tab == option, onClick = { onTabChange(option) }, text = {
                        Text(option.label,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    })
                }
            }
        }
        if (searchOpen) OutlinedTextField(value = query, onValueChange = ::changeQuery, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(searchFocus).onFocusChanged { searchFocused = it.isFocused },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            label = { Text(if (showRecent) "Search videos" else "Search this folder") },
            trailingIcon = { TextButton(onClick = ::closeSearch) { Text("Close") } })
        LazyColumn(state = libraryListState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!searchOpen && showRecent) item(key = "library-actions") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onChooseFile) { Text("Open file") }
                    OutlinedButton(onClick = ::chooseFolder) { Text("Choose folder") }
                    if (!canReturnToPlayback) TextButton(onClick = onPlaySample) { Text("Play sample") }
                }
            }
            if (!searchOpen && !showRecent && folderPath.isEmpty() && favoriteFolders.isNotEmpty()) {
                item(key = "favorite-folders") {
                    FavoriteFoldersSection(
                        folders = favoriteFolders.sortedBy { it.path.lastOrNull()?.name ?: folderName(it.tree) },
                        selected = currentFolder,
                        folderName = ::folderName,
                        onOpen = { shortcut ->
                            keyboard?.hide()
                            onOpenFavorite(shortcut)
                        },
                        onRemove = onToggleFavorite
                    )
                }
            }
            if (!searchOpen && !showRecent && folderPath.isEmpty()) item(key = "current-folder") {
                Box(Modifier.fillMaxWidth().glassPanel(MaterialTheme.shapes.large)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Storage", color = PinesPalette.Accent, style = MaterialTheme.typography.labelLarge)
                        Text(folder?.let(::folderName) ?: "Choose a video folder",
                            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = ::chooseFolder) {
                                Text(if (folder == null) "Choose folder" else "Change folder")
                            }
                            TextButton(onClick = onChooseFile) { Text("Open file") }
                            if (currentFolder != null) {
                                TextButton(onClick = { onToggleFavorite(currentFolder) }) { Text(favoriteAction) }
                            } else if (!canReturnToPlayback) {
                                TextButton(onClick = onPlaySample) { Text("Play sample") }
                            }
                        }
                    }
                }
            }
            if (continuation != null && !searchOpen) item(key = "continue-watching") {
                ContinueWatchingCard(continuation, onOpen = { keyboard?.hide(); onOpen(continuation) })
            }
            if (!searchOpen && !showRecent && folder != null && folderPath.isNotEmpty()) item(key = "folder-navigation") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (folderPath.isNotEmpty()) {
                        Text("Folder path", color = PinesPalette.Accent, style = MaterialTheme.typography.labelLarge)
                        Text((listOf(folderName(folder)) + folderPath.map { it.name }).joinToString(" / "),
                            style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { navigateTo(folderPath.dropLast(1)) }) { Text("Parent folder") }
                            TextButton(onClick = { navigateTo(emptyList()) }) { Text("Root folder") }
                            if (currentFolder != null) {
                                TextButton(onClick = { onToggleFavorite(currentFolder) }) { Text(favoriteAction) }
                            }
                        }
                    }
                }
            }
            if (!showRecent && visibleFolders.isNotEmpty()) {
                item(key = "subfolder-heading") { Text("Subfolders", style = MaterialTheme.typography.titleSmall) }
                items(visibleFolders, key = { "folder:" + it.documentId }) { subfolder ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .clickable(onClickLabel = "Open folder") { navigateTo(folderPath + subfolder) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeNavigationIcon(folder = true, ink = PinesPalette.Accent)
                        Text(subfolder.name, style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (filtered.isNotEmpty()) item(key = "video-heading") {
                Text(
                    if (showRecent) "Recently watched" else "Videos",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            val message = when {
                !showRecent && folder == null -> null
                !showRecent && folderError -> "Could not read this folder. Try again or grant access again."
                !showRecent && folderListing == null -> "Scanning folder…"
                search.isNotEmpty() && filtered.isEmpty() -> "No videos match your search."
                showRecent && filtered.isEmpty() -> "Videos you watch will appear here."
                filtered.isEmpty() -> "No videos in this folder."
                else -> null
            }
            if (message != null) item(key = "library-message") {
                Card(colors = CardDefaults.cardColors(containerColor = PinesPalette.Card), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!showRecent && folder != null && folderListing == null && !folderError)
                            CircularProgressIndicator(Modifier.size(24.dp), color = PinesPalette.Accent)
                        Text(message, color = PinesPalette.Muted)
                        if (!showRecent && folderError) TextButton(onClick = browseState::refreshFolder) { Text("Try again") }
                    }
                }
            } else {
                items(filtered, key = { it.uri.toString() }) { video ->
                    LibraryVideoCard(video, showParentPath = showRecent || includeSubfolders,
                        onOpen = { keyboard?.hide(); onOpen(video) }, onRestart = { keyboard?.hide(); onRestart(video) })
                }
            }
        }
        if (!searchEditing) bottomBar()
    }
    if (showHelp) PlaybackHelpDialog(skipSeconds, onDismiss = { showHelp = false })
    if (showLinkDialog) VideoLinkDialog(onDismiss = { showLinkDialog = false },
        onOpen = { link, credentials -> showLinkDialog = false; onOpenLink(link, credentials) },
        onBrowse = { request -> showLinkDialog = false; smbBrowse = request })
}

@Composable
private fun FavoriteFoldersSection(
    folders: List<LibraryFolderShortcut>,
    selected: LibraryFolderShortcut?,
    folderName: (Uri) -> String,
    onOpen: (LibraryFolderShortcut) -> Unit,
    onRemove: (LibraryFolderShortcut) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Favorites", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(folders, key = { it.tree.toString() + "/" + Uri.encode(it.path.lastOrNull()?.documentId.orEmpty()) }) { shortcut ->
                val root = folderName(shortcut.tree)
                val name = shortcut.path.lastOrNull()?.name ?: root
                val parent = if (shortcut.path.isEmpty()) null
                    else (listOf(root) + shortcut.path.dropLast(1).map { it.name }).joinToString(" / ")
                val isSelected = selected != null && shortcut.sameFolder(selected)
                Card(colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else PinesPalette.Card
                )) {
                    Row(Modifier.width(208.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).heightIn(min = 56.dp)
                                .clickable(onClickLabel = "Open folder") { onOpen(shortcut) }.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HomeNavigationIcon(folder = true, ink = PinesPalette.Accent)
                            Column(Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (parent != null) {
                                    Text(parent, color = PinesPalette.Muted, style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        IconButton(
                            onClick = { onRemove(shortcut) },
                            modifier = Modifier.semantics { contentDescription = "Remove $name from Favorites" }
                        ) {
                            Text("★", color = PinesPalette.Highlight, modifier = Modifier.clearAndSetSemantics { })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoLinkDialog(
    onDismiss: () -> Unit,
    onOpen: (OnlineVideoLink, SmbCredentials?) -> Unit,
    onBrowse: (SmbBrowseRequest) -> Unit
) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(OnlineVideoFormat.Auto) }
    var smbUsername by remember { mutableStateOf("") }
    var smbPassword by remember { mutableStateOf("") }
    val isSmb = input.trimStart().startsWith("smb://", ignoreCase = true)
    val canSubmit = input.isNotBlank() && (!isSmb || smbPassword.isEmpty() || smbUsername.isNotBlank())
    fun credentials(): SmbCredentials? = if (isSmb && smbUsername.isNotBlank()) {
        SmbCredentials(smbUsername.trim(), smbPassword.toCharArray())
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open network stream") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        invalid = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = invalid,
                    label = { Text("HTTPS, RTSP, or SMB URL") },
                    supportingText = {
                        Text(if (invalid) "Enter a valid URL for the selected stream type."
                        else "For SMB, enter a shared folder or file path.")
                    }
                )
                if (isSmb) {
                    OutlinedTextField(
                        value = smbUsername,
                        onValueChange = { smbUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("SMB username") }
                    )
                    OutlinedTextField(
                        value = smbPassword,
                        onValueChange = { smbPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        label = { Text("SMB password") },
                        supportingText = { Text("Leave both fields empty for guest access.") }
                    )
                } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(OnlineVideoFormat.Auto, OnlineVideoFormat.Hls, OnlineVideoFormat.Dash).forEach { option ->
                        FilterChip(
                            selected = option == format,
                            onClick = { format = option },
                            label = { Text(if (option == OnlineVideoFormat.Auto) "Auto" else option.name.uppercase()) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    val link = parseOnlineVideoLink(input, if (isSmb) OnlineVideoFormat.Auto else format)
                    if (link == null) {
                        invalid = true
                    } else {
                        onOpen(link, if (link.format == OnlineVideoFormat.Smb) credentials() else null)
                    }
                }
            ) { Text("Play") }
        },
        dismissButton = {
            Row {
                if (isSmb) {
                    TextButton(enabled = canSubmit, onClick = {
                        val location = parseSmbDirectory(input)
                        if (location == null) invalid = true
                        else onBrowse(SmbBrowseRequest(location, credentials()))
                    }) { Text("Browse share") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
