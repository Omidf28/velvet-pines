package com.subtitleplayer.prototype

import android.database.sqlite.SQLiteException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PersonalDictionaryScreen(dao: PersonalDictionaryDao, onBack: () -> Unit) {

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by remember(dao) { dao.observeAll() }.collectAsState(initial = null)
    var query by rememberSaveable { mutableStateOf("") }
    var term by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<PersonalDictionaryEntry?>(null) }
    var pending by remember { mutableStateOf<List<PersonalDictionaryEntry>?>(null) }
    var exporting by remember { mutableStateOf<List<PersonalDictionaryEntry>?>(null) }
    var busy by remember { mutableStateOf(false) }
    BackHandler { if (!busy) onBack() }
    var message by remember { mutableStateOf<String?>(null) }
    fun fail() { message = "عملیات انجام نشد. قالب فایل، دسترسی و فضای ذخیره را بررسی کنید." }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    pending = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { readSubtitleBytes(it) }
                            ?: throw IOException("Unavailable file")
                        parseDictionary(Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString())
                    }
                } catch (_: IOException) { fail() }
                catch (_: SecurityException) { fail() }
                finally { busy = false }
            }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val snapshot = exporting
        exporting = null
        if (uri != null) {
            if (snapshot == null) message = "دوباره خروجی بگیرید." else {
                busy = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Unavailable file")
                            stream.bufferedWriter(Charsets.UTF_8).use { writeDictionary(snapshot, it) }
                        }
                        message = "خروجی ${snapshot.size} معنی شخصی ذخیره شد."
                    } catch (_: IOException) { fail() }
                    catch (_: SecurityException) { fail() }
                    finally { busy = false }
                }
            }
        }
    }
    selected?.let { entry ->
        PersonalMeaningDialog(
            dao, entry.wordKey, WordMeaning(entry.headword, entry.meaning),
            entries.orEmpty().any { it.wordKey == entry.wordKey },
            onSaved = {}, onDismiss = { selected = null }
        )
    }
    pending?.let { imported ->
        val currentKeys = entries.orEmpty().mapTo(hashSetOf()) { it.wordKey }
        val replacements = imported.count { it.wordKey in currentKeys }
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("ورود ${imported.size} معنی شخصی") },
            text = { Text("$replacements معنی موجود جایگزین می‌شود. بقیه اضافه می‌شوند؛ هیچ مدخل دیگری حذف نمی‌شود. در کلیدهای تکراری فایل، آخرین ردیف استفاده شده است.") },
            confirmButton = {
                TextButton(enabled = !busy && entries != null, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            dao.merge(imported)
                            pending = null
                            message = "واژه‌نامه وارد شد."
                        } catch (_: SQLiteException) { fail() }
                        finally { busy = false }
                    }
                }) { Text("وارد کردن") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("لغو") } }
        )
    }
    val visible = remember(entries, query) {
        val key = query.trim().lowercase(java.util.Locale.ROOT)
        entries.orEmpty().filter {
            key.isEmpty() || listOf(it.wordKey, it.headword, it.meaning).any { value ->
                value.lowercase(java.util.Locale.ROOT).contains(key)
            }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text("واژه‌نامهٔ شخصی", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = !busy, onClick = onBack) { Text("بازگشت") }
            }
            LazyColumn(
                Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("معنی‌های این بخش برای همهٔ فیلم‌ها هستند؛ دفترچه و پیشرفت مرور مستقل‌اند.")
                        OutlinedTextField(query, { query = it }, label = { Text("جست‌وجو در واژه‌نامه") }, singleLine = true)
                        OutlinedTextField(term, { term = it }, label = { Text("واژه یا عبارت انگلیسی تازه") }, singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr))
                        TextButton(enabled = !busy && entries != null && normalizeDictionaryTerm(term) != null, onClick = {
                            val key = normalizeDictionaryTerm(term) ?: return@TextButton
                            selected = entries.orEmpty().firstOrNull { it.wordKey == key }
                                ?: PersonalDictionaryEntry(key, key, "")
                        }) { Text("افزودن / ویرایش") }
                        Row {
                            TextButton(enabled = !busy && entries != null && pending == null, onClick = {
                                try { importFile.launch(arrayOf("*/*")) }
                                catch (_: android.content.ActivityNotFoundException) { fail() }
                            }) { Text("ورود فایل") }
                            TextButton(enabled = !busy && !entries.isNullOrEmpty() && pending == null, onClick = {
                                exporting = entries.orEmpty().toList()
                                try { exportFile.launch("velvet-pines-dictionary.tsv") }
                                catch (_: android.content.ActivityNotFoundException) { exporting = null; fail() }
                            }) { Text("خروجی کامل") }
                        }
                        Text("فایل UTF-8 با ستون‌های word، headword و meaning جداشده با Tab؛ ابتدا از خروجی یا نمونهٔ راهنما استفاده کنید.")
                        if (busy) Text("در حال انجام…")
                        message?.let { Text(it) }
                        Text(if (entries == null) "در حال خواندن…" else "${visible.size} نتیجه از ${entries.orEmpty().size}")
                    }
                }
                items(visible, key = { it.wordKey }) { entry ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(entry.wordKey + " → " + entry.headword,
                                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                            Text(entry.meaning, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                            TextButton(enabled = !busy, onClick = { selected = entry }) { Text("ویرایش / برداشتن معنی") }
                        }
                    }
                }
            }
        }
    }
}
