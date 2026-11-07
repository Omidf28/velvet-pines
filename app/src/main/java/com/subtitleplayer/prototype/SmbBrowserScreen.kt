package com.subtitleplayer.prototype

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun SmbBrowserScreen(
    request: SmbBrowseRequest,
    onOpen: (OnlineVideoLink, SmbCredentials?) -> Unit,
    onBack: () -> Unit
) {
    var folder by remember(request) { mutableStateOf(request.location) }
    var refresh by remember { mutableIntStateOf(0) }
    var entries by remember(folder, refresh) { mutableStateOf<List<SmbBrowserEntry>?>(null) }
    var failed by remember(folder, refresh) { mutableStateOf(false) }

    BackHandler {
        if (folder.path.isEmpty()) onBack() else folder = folder.parent()
    }
    LaunchedEffect(folder, refresh) {
        try {
            entries = withContext(Dispatchers.IO) { readSmbDirectory(folder, request.credentials) }
        } catch (_: IOException) {
            failed = true
        }
    }

    val loaded = entries
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SMB folders", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("Back to Files") }
        }
        Text(
            folder.share + if (folder.path.isEmpty()) "" else " / " + folder.path.replace('\\', '/'),
            Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
        )
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(enabled = folder.path.isNotEmpty(), onClick = { folder = folder.parent() }) {
                Text("Parent folder")
            }
            TextButton(onClick = { refresh++ }) { Text("Refresh") }
        }
        if (failed || loaded == null || loaded.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        failed -> "Could not read the folder. Check the address and credentials, then try again."
                        loaded == null -> "Loading folder…"
                        else -> "No folders or videos found."
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(loaded, key = SmbBrowserEntry::name) { entry ->
                    Card(
                        onClick = {
                            val child = folder.child(entry.name)
                            if (entry.directory) folder = child
                            else onOpen(OnlineVideoLink(child.toUrl(), OnlineVideoFormat.Smb), request.credentials)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(entry.name)
                            Text(if (entry.directory) "Folder" else "Video", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
