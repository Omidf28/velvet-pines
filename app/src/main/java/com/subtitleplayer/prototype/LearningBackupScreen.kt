package com.subtitleplayer.prototype

import android.database.sqlite.SQLiteException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler { if (!busy) onBack() }
    fun fail() { message = "عملیات کامل نشد. فایل و دسترسی ذخیره‌سازی را بررسی کنید." }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    val snapshot = dao.snapshot()
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Unavailable output")
                        stream.use { writeLearningBackup(snapshot, it) }
                    }
                    message = "پشتیبان ${snapshot.words.size} کارت و ${snapshot.dictionary.size} معنی شخصی ذخیره شد."
                } catch (_: IOException) { fail() }
                catch (_: SecurityException) { fail() }
                catch (_: SQLiteException) { fail() }
                finally { busy = false }
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                try {
                    pending = withContext(Dispatchers.IO) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use {
                            readSubtitleBytes(it, learningBackupLimit)
                        } ?: throw IOException("Unavailable input")
                        parseLearningBackup(bytes)
                    }
                } catch (_: IOException) { fail() }
                catch (_: SecurityException) { fail() }
                finally { busy = false }
            }
        }
    }
    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!busy) pending = null },
            title = { Text("بازیابی داده‌های یادگیری") },
            text = { Text("${backup.words.size} کارت و ${backup.dictionary.size} معنی شخصی در فایل است. موارد تازه اضافه می‌شوند؛ در تکراری‌ها اطلاعات فعلی دستگاه حفظ می‌شود. هیچ مدخلی حذف نمی‌شود.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val count = dao.restore(backup)
                            pending = null
                            message = "$count مورد تازه بازیابی شد؛ موارد تکراری حفظ شدند."
                        } catch (_: SQLiteException) { fail() }
                        finally { busy = false }
                    }
                }) { Text("بازیابی") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { pending = null }) { Text("لغو") } }
        )
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("پشتیبان داده‌های یادگیری", style = MaterialTheme.typography.titleLarge)
            Text("دفترچه، معنی شخصی، دیالوگ، نام منبع و پیشرفت مرور در یک فایل نگه‌داری می‌شوند. فایل شامل متن داده‌هاست و رمزگذاری نمی‌شود.")
            Text("فایل ویدیو، صدا، مجوز دسترسی فایل‌ها، صف و تنظیمات پلیر در پشتیبان نیستند. نشانی آنلاین و رمز شبکه در دادهٔ عادی برنامه ذخیره نمی‌شوند.")
            Button(enabled = !busy && pending == null, onClick = {
                try { export.launch("velvet-pines-learning.spbackup") }
                catch (_: android.content.ActivityNotFoundException) { fail() }
            }) { Text("ساخت پشتیبان") }
            Button(enabled = !busy && pending == null, onClick = {
                try { import.launch(arrayOf("*/*")) }
                catch (_: android.content.ActivityNotFoundException) { fail() }
            }) { Text("انتخاب فایل برای بازیابی") }
            Text("حداکثر فایل ۳۲ MiB و هر بخش ۲۰ هزار مدخل است. فایل خراب یا نسخهٔ ناشناخته پیش از تغییر دیتابیس رد می‌شود. بازیابی یک تراکنش است.")
            if (busy) Text("در حال انجام…")
            message?.let { Text(it) }
            TextButton(enabled = !busy, onClick = onBack) { Text("بازگشت") }
        }
    }
}
