package com.subtitleplayer.prototype

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LearningBackupScreen(dao: LearningBackupDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<LearningBackup?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler { if (!busy) onBack() }
    fun fail() { message = "Could not finish. Check the file and storage access." }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            busy = true
            message = null
            scope.launch {
                try {
                    val snapshot = dao.snapshot()
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Unavailable output")
                        stream.use { writeLearningBackup(snapshot, it) }
                    }
                    message = "Backed up ${snapshot.words.size} cards and ${snapshot.dictionary.size} personal definitions."
                } catch (_: IOException) { fail() }
                catch (_: SecurityException) { fail() }
                catch (_: SQLiteException) { fail() }
                finally { busy = false }
            }
        }
    }
    fun openBackup(uri: Uri?, restore: Boolean) {
        if (uri == null) return
        busy = true
        message = null
        scope.launch {
            try {
                val backup = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use {
                        readSubtitleBytes(it, learningBackupLimit)
                    } ?: throw IOException("Unavailable input")
                    parseLearningBackup(bytes)
                }
                if (restore) pending = backup
                else message = "Backup verified: ${backup.words.size} cards and ${backup.dictionary.size} personal definitions."
            } catch (_: IOException) {
                message = "Could not verify this backup. Select a complete .spbackup file and check storage access."
            } catch (_: SecurityException) {
                fail()
            } finally {
                busy = false
            }
        }
    }
    val verify = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { openBackup(it, restore = false) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { openBackup(it, restore = true) }
    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("Restore learning data") },
            text = { Text("The backup contains ${backup.words.size} cards and ${backup.dictionary.size} personal definitions. New entries will be added. Existing entries are kept when there is a duplicate.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val added = dao.restore(backup)
                            pending = null
                            message = "Restored ${added.cards} cards and ${added.personalDefinitions} personal definitions. Existing duplicates were kept."
                        } catch (_: SQLiteException) { fail() }
                        finally { busy = false }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("Cancel") } }
        )
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Learning backup", style = MaterialTheme.typography.titleLarge)
            Text("The backup includes saved words, personal definitions, dialogue, source names, and review progress in an unencrypted file.")
            Text("Videos, audio, file permissions, and player settings are excluded. Stream URLs and network passwords are not saved by the app.")
            Button(enabled = !busy && pending == null, onClick = {
                try { export.launch("velvet-pines-learning.spbackup") }
                catch (_: android.content.ActivityNotFoundException) { fail() }
            }) { Text("Create backup") }
            OutlinedButton(enabled = !busy && pending == null, onClick = {
                try { verify.launch(arrayOf("*/*")) }
                catch (_: android.content.ActivityNotFoundException) { fail() }
            }) { Text("Verify backup") }
            OutlinedButton(enabled = !busy && pending == null, onClick = {
                try { restore.launch(arrayOf("*/*")) }
                catch (_: android.content.ActivityNotFoundException) { fail() }
            }) { Text("Restore backup") }
            Text("Before uninstalling, verify the backup and check the card count. Verification leaves your data as it is.")
            Text("Limit: 32 MiB and 20,000 entries per section.")
            if (busy) Text("Working…")
            message?.let { Text(it) }
            TextButton(enabled = !busy, onClick = onBack) { Text("Back") }
        }
    }
}
