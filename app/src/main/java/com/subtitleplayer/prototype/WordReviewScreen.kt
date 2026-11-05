package com.subtitleplayer.prototype

import android.database.sqlite.SQLiteException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
internal fun WordReviewScreen(
    dao: SavedWordsDao, pronunciation: EnglishPronunciation, source: String? = null, onBack: () -> Unit
) {
    var cards by remember(dao, source) { mutableStateOf<List<SavedWord>?>(null) }
    var index by remember(dao, source) { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var loadFailed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    LaunchedEffect(dao, source, reload) {
        cards = null
        index = 0
        loadFailed = false
        saveFailed = false
        try {
            val now = System.currentTimeMillis()
            cards = if (source == null) dao.dueForReview(now) else dao.dueForSource(now, source)
        } catch (_: SQLiteException) {
            loadFailed = true
        }
    }

    val session = cards
    val entry = session?.getOrNull(index)
    DisposableEffect(entry?.id, pronunciation) { onDispose { pronunciation.stop() } }
    var answerVisible by remember(entry?.id) { mutableStateOf(false) }

    fun rate(remembered: Boolean) {
        val card = entry ?: return
        if (saving || !answerVisible) return
        saving = true
        saveFailed = false
        scope.launch {
            try {
                val plan = planReview(card.reviewLevel, remembered, System.currentTimeMillis())
                val changed = dao.recordReview(card.id, card.reviewCount, plan.level, plan.dueAt)
                if (changed == 1) index++ else reload++
            } catch (_: SQLiteException) {
                saveFailed = true
            } finally {
                saving = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (source == null) "مرور واژه‌ها" else "مرور واژه‌های این منبع", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                loadFailed -> {
                    Text("خواندن واژه‌ها ممکن نبود.")
                    Button(onClick = { reload++ }) { Text("تلاش دوباره") }
                }
                session == null -> Text("در حال آماده‌کردن مرور…")
                entry == null -> {
                    Text(if (index == 0) "فعلاً واژه‌ای برای مرور نرسیده است." else "این نوبت مرور تمام شد.")
                    if (index > 0) Text("$index پاسخ ذخیره شد.")
                    Text("هر نوبت حداکثر ۵۰ واژه دارد. برای بررسی واژه‌های باقی‌مانده یا مرورهای تازه، دوباره بررسی کنید.")
                    Button(onClick = { reload++ }) { Text("بررسی دوباره") }
                }
                else -> {
                    Text("کارت ${index + 1} از ${session.size}")
                    Text("معنی را به یاد بیاورید، سپس پاسخ را ببینید.")
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                if (entry.word.equals(entry.headword, ignoreCase = true)) entry.word
                                else "${entry.word} → ${entry.headword}",
                                style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr)
                            )
                            Text(entry.dialogue, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr))
                            Text("${entry.sourceName} · ${formatTime(entry.positionMs)}", style = MaterialTheme.typography.bodySmall)
                            if (answerVisible) {
                                Text(
                                    entry.meaning,
                                    style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Rtl)
                                )
                            }
                        }
                    }
                    PronunciationControls(pronunciation, entry.word, entry.dialogue)
                    if (!answerVisible) {
                        Button(onClick = { answerVisible = true }) { Text("نمایش معنی") }
                    } else {
                        Text("یادم نبود: مرور دوباره در ۱۰ دقیقه")
                        Text("بلد بودم: مرور بعدی در ${planReview(entry.reviewLevel, true, 0).intervalMs / 86_400_000L} روز")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(enabled = !saving, onClick = { rate(false) }, modifier = Modifier.weight(1f)) {
                                Text("یادم نبود")
                            }
                            Button(enabled = !saving, onClick = { rate(true) }, modifier = Modifier.weight(1f)) {
                                Text("بلد بودم")
                            }
                        }
                    }
                    if (saving) Text("در حال ذخیرهٔ پاسخ…")
                    if (saveFailed) Text("پاسخ ذخیره نشد. دوباره یکی از گزینه‌ها را بزنید.")
                }
            }
        }
    }
}
