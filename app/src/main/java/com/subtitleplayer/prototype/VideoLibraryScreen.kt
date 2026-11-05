package com.subtitleplayer.prototype

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LibraryTab { Folder, Recent, Queue }

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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resolver = context.contentResolver
    val orderPrefs = remember(context) { context.getSharedPreferences("display_order", android.content.Context.MODE_PRIVATE) }
    var orderName by rememberSaveable { mutableStateOf(orderPrefs.getString("library", LibraryDisplayOrder.Default.name)) }
    val order = LibraryDisplayOrder.entries.firstOrNull { it.name == orderName } ?: LibraryDisplayOrder.Default
    val showIdentity = LocalWindowInfo.current.containerSize.height / LocalDensity.current.density >= 600f
    var query by rememberSaveable { mutableStateOf("") }
    var showLinkDialog by remember { mutableStateOf(false) }
    var smbBrowse by remember { mutableStateOf<SmbBrowseRequest?>(null) }
    var tab by rememberSaveable {
        mutableStateOf(if (folder == null && recent.isNotEmpty()) LibraryTab.Recent else LibraryTab.Folder)
    }
    var folderVideos by remember(folder) { mutableStateOf<List<LibraryVideo>?>(null) }
    var folderError by remember(folder) { mutableStateOf(false) }
    val libraryListState = rememberLazyListState()
    LaunchedEffect(order, query, tab) { libraryListState.scrollToItem(0) }

    val browse = smbBrowse
    if (browse != null) {
        SmbBrowserScreen(browse, onOpen = onOpenLink, onBack = { smbBrowse = null })
        return
    }

    LaunchedEffect(folder, resolver) {
        if (folder == null) return@LaunchedEffect
        try {
            folderVideos = withContext(Dispatchers.IO) { scanVideoFolder(resolver, folder) }
        } catch (_: IOException) {
            folderError = true
        } catch (_: SecurityException) {
            folderError = true
        } catch (_: IllegalArgumentException) {
            folderError = true
        }
    }

    val showRecent = tab == LibraryTab.Recent
    val videos = if (showRecent) recent else folderVideos.orEmpty()
    val queuedUris = remember(queue) { queue.map(LibraryVideo::uri).toSet() }
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
            it.name.contains(search, ignoreCase = true) ||
                it.parentPath?.contains(search, ignoreCase = true) == true
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).pinesAtmosphere().safeDrawingPadding()) {
        if (showIdentity) PinesIdentityBanner()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (showIdentity) "کتابخانهٔ ویدیو" else "Velvet Pines", Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextButton(onClick = {
                tab = LibraryTab.Folder
                onChooseFolder()
            }) { Text("انتخاب پوشه") }
            TextButton(onClick = onChooseFile) { Text("انتخاب فایل") }
            TextButton(onClick = { showLinkDialog = true }) { Text("لینک ویدیو") }
        }
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == LibraryTab.Folder, onClick = { tab = LibraryTab.Folder }, label = { Text("پوشه") })
            FilterChip(selected = showRecent, onClick = { tab = LibraryTab.Recent }, label = { Text("اخیر") })
            FilterChip(selected = tab == LibraryTab.Queue, onClick = { tab = LibraryTab.Queue }, label = {
                Text(if (queue.isEmpty()) "صف پخش" else "صف پخش (${queue.size})")
            })
        }
        if (tab == LibraryTab.Queue) {
            PlaybackQueueList(queue, onMoveQueued, onRemoveQueued, onPlayQueue, Modifier.weight(1f))
            return@Column
        }
        DisplayOrderMenu(
            order.name,
            if (order == LibraryDisplayOrder.Default && showRecent) "اخیرترین تماشا" else order.label,
            LibraryDisplayOrder.entries.map { it.name to it.label },
            onSelect = { orderName = it; orderPrefs.edit().putString("library", it).apply() }
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            singleLine = true,
            label = { Text("جست‌وجوی ویدیو") }
        )

        val message = when {
            !showRecent && folder == null -> "برای دیدن ویدیوها یک پوشه انتخاب کنید."
            !showRecent && folderError -> "خواندن پوشه ممکن نبود. پوشه را دوباره انتخاب کنید."
            !showRecent && folderVideos == null -> "در حال خواندن پوشه…"
            videos.isEmpty() && showRecent -> "هنوز ویدیویی در تاریخچه نیست."
            videos.isEmpty() -> "ویدیویی در این پوشه پیدا نشد."
            filtered.isEmpty() -> "نتیجه‌ای برای جست‌وجو پیدا نشد."
            else -> null
        }
        if (message != null) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(message)
            }
        } else {
            LazyColumn(
                state = libraryListState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.uri.toString() }) { video ->
                    Card(onClick = { onOpen(video) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f).padding(vertical = 14.dp)) {
                                Text(video.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                video.parentPath?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                            val queued = video.uri in queuedUris
                            TextButton(enabled = !queued, onClick = { onEnqueue(video) }) {
                                Text(if (queued) "در صف" else "افزودن به صف")
                            }
                        }
                    }
                }
            }
        }
    }
    if (showLinkDialog) {
        VideoLinkDialog(
            onDismiss = { showLinkDialog = false },
            onOpen = { link, credentials ->
                showLinkDialog = false
                onOpenLink(link, credentials)
            },
            onBrowse = { request ->
                showLinkDialog = false
                smbBrowse = request
            }
        )
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
