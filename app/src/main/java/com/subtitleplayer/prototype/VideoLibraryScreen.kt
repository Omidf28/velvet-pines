package com.subtitleplayer.prototype

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LibraryTab(val label: String) { Folder("پوشه"), Recent("اخیرها"), Queue("صف پخش") }

@Composable
internal fun VideoLibraryScreen(
    folder: Uri?,
    recent: List<LibraryVideo>,
    queue: List<LibraryVideo>,
    onEnqueue: (LibraryVideo) -> Unit,
    onMoveQueued: (Uri, Int) -> Unit,
    onRemoveQueued: (Uri) -> Unit,
    onPlayQueue: () -> Unit,
    onChooseFolder: () -> Unit,
    onChooseFile: () -> Unit,
    onOpenLink: (OnlineVideoLink, SmbCredentials?) -> Unit,
    onOpen: (LibraryVideo) -> Unit,
    onRestart: (LibraryVideo) -> Unit,
    canReturnToPlayback: Boolean,
    skipSeconds: Int,
    onPlaySample: () -> Unit,
    onDictionary: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val orderPrefs = remember(context) { context.getSharedPreferences("display_order", android.content.Context.MODE_PRIVATE) }
    var orderName by rememberSaveable { mutableStateOf(orderPrefs.getString("library", LibraryDisplayOrder.Default.name)) }
    val order = LibraryDisplayOrder.entries.firstOrNull { it.name == orderName } ?: LibraryDisplayOrder.Default
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchEditing = searchFocused && WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var showLinkDialog by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var smbBrowse by remember { mutableStateOf<SmbBrowseRequest?>(null) }
    var tab by rememberSaveable { mutableStateOf(if (recent.isNotEmpty()) LibraryTab.Recent else LibraryTab.Folder) }
    var folderVideos by remember(folder) { mutableStateOf<List<LibraryVideo>?>(null) }
    var folderError by remember(folder) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var loadedRefresh by remember(folder) { mutableIntStateOf(-1) }
    val libraryListState = rememberLazyListState()
    LaunchedEffect(order, query, tab) { libraryListState.scrollToItem(0) }

    val browse = smbBrowse
    if (browse != null) {
        SmbBrowserScreen(browse, onOpen = onOpenLink, onBack = { smbBrowse = null })
        return
    }
    LaunchedEffect(folder, resolver, refresh, tab) {
        if (folder == null || tab != LibraryTab.Folder || loadedRefresh == refresh) return@LaunchedEffect
        folderVideos = null
        folderError = false
        try {
            folderVideos = withContext(Dispatchers.IO) { scanVideoFolder(resolver, folder) }
            loadedRefresh = refresh
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            folderError = true
        }
    }
    val bookmarksByKey = remember(recent, queue) {
        buildMap<String, PlaybackBookmark> {
            (queue.asReversed() + recent.asReversed()).forEach { video ->
                video.bookmark?.let { put(videoSubtitleKey(video.uri), it) }
            }
        }
    }
    val showRecent = tab == LibraryTab.Recent
    val videos = remember(showRecent, recent, folderVideos, bookmarksByKey) {
        (if (showRecent) recent else folderVideos.orEmpty()).map { video ->
            video.copy(bookmark = video.bookmark ?: bookmarksByKey[videoSubtitleKey(video.uri)])
        }
    }
    val queuedKeys = remember(queue) { queue.map { videoSubtitleKey(it.uri) }.toSet() }
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
    val continuation = if (showRecent && search.isEmpty()) videos.firstOrNull { it.bookmark?.resumable == true } else null
    Column(Modifier.fillMaxSize().pinesAtmosphere().safeDrawingPadding()) {
        if (!searchEditing) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text("VELVET PINES", color = PinesPalette.Rose, style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Serif, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("کتابخانه", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (canReturnToPlayback) TextButton(onClick = onBack) { Text("بازگشت به فیلم") }
                else TextButton(onClick = onPlaySample) { Text("نمونه") }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibraryTab.entries.forEach { option ->
                    FilterChip(selected = tab == option, onClick = { tab = option }, label = {
                        Text(option.label + if (option == LibraryTab.Queue && queue.isNotEmpty()) " (${queue.size})" else "")
                    })
                }
            }
        }
        if (tab == LibraryTab.Queue) {
            PlaybackQueueList(queue, onMoveQueued, onRemoveQueued, onPlayQueue, Modifier.weight(1f))
        } else {
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).onFocusChanged { searchFocused = it.isFocused },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                label = { Text("جست‌وجوی فیلم یا پوشه") },
                trailingIcon = if (query.isEmpty()) null else ({ TextButton(onClick = { query = "" }) { Text("پاک") } }))
            LazyColumn(state = libraryListState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!searchEditing) item(key = "library-actions") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onChooseFile) { Text("باز کردن فایل") }
                        OutlinedButton(onClick = { tab = LibraryTab.Folder; onChooseFolder() }) { Text("انتخاب پوشه") }
                        TextButton(onClick = { showLinkDialog = true }) { Text("لینک ویدیو") }
                        if (canReturnToPlayback) TextButton(onClick = onPlaySample) { Text("نمونه") }
                        TextButton(onClick = onDictionary) { Text("واژه‌نامه") }
                        TextButton(onClick = { showHelp = true }) { Text("راهنما") }
                    }
                }
                if (continuation != null && !searchEditing) item(key = "continue-watching") {
                    ContinueWatchingCard(continuation, onOpen = { keyboard?.hide(); onOpen(continuation) })
                }
                if (!searchEditing) item(key = "library-order") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${filtered.size} ویدیو", color = PinesPalette.Muted, style = MaterialTheme.typography.labelMedium)
                        Box(Modifier.weight(1f)) {
                            DisplayOrderMenu(order.name,
                                if (order == LibraryDisplayOrder.Default && showRecent) "اخیرترین تماشا" else order.label,
                                LibraryDisplayOrder.entries.map { it.name to it.label },
                                onSelect = { orderName = it; orderPrefs.edit().putString("library", it).apply() })
                        }
                        if (!showRecent && folder != null) TextButton(enabled = folderVideos != null || folderError, onClick = { refresh++ }) { Text("تازه‌سازی") }
                    }
                }
                val message = when {
                    !showRecent && folder == null -> "فیلم‌هایت را از یک پوشه اضافه کن؛ زیرنویس‌های هم‌نام هم از همان پوشه در دسترس خواهند بود."
                    !showRecent && folderError -> "پوشه خوانده نشد. دوباره تلاش کن یا دسترسی پوشه را انتخاب کن."
                    !showRecent && folderVideos == null -> "در حال بررسی پوشه…"
                    search.isNotEmpty() && filtered.isEmpty() -> "ویدیویی با این جست‌وجو پیدا نشد."
                    showRecent && filtered.isEmpty() -> "پس از تماشای اولین فیلم، ادامهٔ تماشا و فیلم‌های اخیر اینجا نمایش داده می‌شوند."
                    filtered.isEmpty() -> "در این پوشه ویدیویی پیدا نشد."
                    else -> null
                }
                if (message != null) item(key = "library-message") {
                    Card(colors = CardDefaults.cardColors(containerColor = PinesPalette.Card), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (!showRecent && folder != null && folderVideos == null && !folderError)
                                CircularProgressIndicator(Modifier.size(24.dp), color = PinesPalette.Rose)
                            Text(message, color = PinesPalette.Muted)
                            if (!showRecent && folderError) TextButton(onClick = { refresh++ }) { Text("تلاش دوباره") }
                        }
                    }
                } else {
                    items(filtered, key = { it.uri.toString() }) { video ->
                        LibraryVideoCard(video, queued = videoSubtitleKey(video.uri) in queuedKeys,
                            onOpen = { keyboard?.hide(); onOpen(video) }, onRestart = { keyboard?.hide(); onRestart(video) }, onEnqueue = { onEnqueue(video) })
                    }
                }
            }
        }
    }
    if (showHelp) PlaybackHelpDialog(skipSeconds, onDismiss = { showHelp = false })
    if (showLinkDialog) VideoLinkDialog(onDismiss = { showLinkDialog = false },
        onOpen = { link, credentials -> showLinkDialog = false; onOpenLink(link, credentials) },
        onBrowse = { request -> showLinkDialog = false; smbBrowse = request })
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
        title = { Text("باز کردن لینک ویدیو") },
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
                    label = { Text("آدرس HTTPS، RTSP یا SMB") },
                    supportingText = {
                        Text(if (invalid) "آدرس معتبر و سازگار با نوع پخش وارد کنید."
                        else "برای SMB مسیر فایل یا پوشهٔ اشتراک را وارد کنید.")
                    }
                )
                if (isSmb) {
                    OutlinedTextField(
                        value = smbUsername,
                        onValueChange = { smbUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("نام کاربری SMB") }
                    )
                    OutlinedTextField(
                        value = smbPassword,
                        onValueChange = { smbPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        label = { Text("رمز SMB") },
                        supportingText = { Text("برای ورود مهمان، هر دو فیلد را خالی بگذارید.") }
                    )
                } else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(OnlineVideoFormat.Auto, OnlineVideoFormat.Hls, OnlineVideoFormat.Dash).forEach { option ->
                        FilterChip(
                            selected = option == format,
                            onClick = { format = option },
                            label = { Text(if (option == OnlineVideoFormat.Auto) "خودکار" else option.name.uppercase()) }
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
            ) { Text("پخش") }
        },
        dismissButton = {
            Row {
                if (isSmb) {
                    TextButton(enabled = canSubmit, onClick = {
                        val location = parseSmbDirectory(input)
                        if (location == null) invalid = true
                        else onBrowse(SmbBrowseRequest(location, credentials()))
                    }) { Text("مرور پوشه") }
                }
                TextButton(onClick = onDismiss) { Text("لغو") }
            }
        }
    )
}

@Composable
private fun PlaybackQueueList(
    queue: List<LibraryVideo>,
    onMove: (Uri, Int) -> Unit,
    onRemove: (Uri) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier
) {
    if (queue.isEmpty()) {
        Box(modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Text("از بخش پوشه یا اخیر، ویدیو به صف اضافه کنید.")
        }
        return
    }

    Column(modifier.fillMaxWidth()) {
        Button(onClick = onPlay, modifier = Modifier.padding(horizontal = 12.dp)) { Text("پخش صف") }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(queue, key = { _, video -> video.uri.toString() }) { index, video ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            "${index + 1}. ${video.name}",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(enabled = index > 0, onClick = { onMove(video.uri, -1) }) { Text("بالا") }
                            TextButton(enabled = index < queue.lastIndex, onClick = { onMove(video.uri, 1) }) {
                                Text("پایین")
                            }
                            TextButton(onClick = { onRemove(video.uri) }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }
}
