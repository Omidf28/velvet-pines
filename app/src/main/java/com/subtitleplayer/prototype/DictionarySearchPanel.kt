package com.subtitleplayer.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Independent offline lookup; opening the window does not alter playback or its bounds. */
@Composable
internal fun DictionarySearchPanel(
    dictionary: PackagedDictionary,
    personal: Map<String, WordMeaning>,
    personalReady: Boolean,
    dao: PersonalDictionaryDao,
    speech: EnglishPronunciation,
    initialTerm: String,
    beforeSpeak: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf(initialTerm) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val prefix = remember(query) { normalizeDictionaryPrefix(query) }
    val exactKey = remember(query) { normalizeDictionaryTerm(query) }
    var results by remember(query, personal, retry) { mutableStateOf(DictionarySearchResult(emptyList(), false)) }
    var searching by remember(query, personal, retry) { mutableStateOf(prefix != null) }
    var failed by remember(query, personal, retry) { mutableStateOf(false) }
    var detail by remember(selected, personal, retry) { mutableStateOf<DictionaryResult?>(null) }
    var detailFailed by remember(selected, personal, retry) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    LaunchedEffect(query, personal, retry) {
        if (prefix == null) return@LaunchedEffect
        delay(200)
        try {
            results = withContext(Dispatchers.IO) { dictionary.search(query, personal) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        searching = false
        listState.scrollToItem(0)
    }
    LaunchedEffect(selected, personal, retry) {
        val term = selected ?: return@LaunchedEffect
        try {
            detail = withContext(Dispatchers.IO) { dictionary.resolve(term, personal) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { detailFailed = true }
    }
    DisposableEffect(speech) { onDispose { speech.stop() } }
    fun close() { keyboard?.hide(); speech.stop(); onDismiss() }
    fun select(term: String) { keyboard?.hide(); speech.stop(); selected = term }
    PlayerOverlayPanel("واژه‌نامهٔ انگلیسی–فارسی", onDismiss = ::close) {
        val term = selected
        if (term == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(160) },
                label = { Text("کلمه یا عبارت انگلیسی") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                trailingIcon = if (query.isEmpty()) null else ({ TextButton(onClick = { query = "" }) { Text("پاک") } }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "status") {
                    Text(when {
                        query.isBlank() -> "واژه یا ابتدای یک عبارت را بنویس؛ جست‌وجو کاملاً آفلاین است."
                        prefix == null -> "با حروف انگلیسی جست‌وجو کن؛ حداکثر ۸ واژه و ۸۰ نویسه."
                        searching -> "در حال جست‌وجو…"
                        failed -> "واژه‌نامه خوانده نشد. فضای ذخیره را بررسی کن و دوباره تلاش کن."
                        results.entries.isEmpty() -> "مدخلی با این شروع پیدا نشد؛ می‌توانی معنی شخصی اضافه کنی."
                        results.hasMore -> "${results.entries.size} نتیجهٔ اول؛ برای نتایج دقیق‌تر بیشتر بنویس."
                        else -> "${results.entries.size} نتیجه"
                    }, color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall)
                    if (failed) TextButton(onClick = { retry++ }) { Text("تلاش دوباره") }
                    if (exactKey != null) TextButton(onClick = { select(exactKey) }) {
                        Text("نمایش معنی «$exactKey»", maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (!searching && !failed) items(results.entries, key = { it.term }) { entry ->
                    Card(colors = CardDefaults.cardColors(containerColor = PinesPalette.Card), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().clickable(onClickLabel = "نمایش معنی کامل") { select(entry.term) }
                            .padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.term, color = PinesPalette.Rose,
                                style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                            Text(entry.meaning.persian, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl))
                            Text(entry.source, style = MaterialTheme.typography.labelSmall, color = PinesPalette.Muted)
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(term, Modifier.weight(1f), color = PinesPalette.Rose,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr))
                TextButton(onClick = { speech.stop(); selected = null }) { Text("نتایج") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    val result = detail
                    Text(if (detailFailed) "خواندن معنی انجام نشد؛ دوباره تلاش کن."
                        else if (result == null) "در حال خواندن معنی…"
                        else result.meaning?.persian ?: "معنی آفلاین پیدا نشد؛ می‌توانی معنی شخصی اضافه کنی.",
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl))
                    result?.let { Text(it.source, color = PinesPalette.Muted, style = MaterialTheme.typography.labelSmall) }
                    if (detailFailed) TextButton(onClick = { retry++ }) { Text("تلاش دوباره") }
                }
                item {
                    PronunciationControls(speech, term, contentColor = PinesPalette.Rose, beforeSpeak = beforeSpeak)
                    TextButton(enabled = personalReady && (detail != null || detailFailed), onClick = {
                        speech.stop(); editing = true
                    }) { Text(if (term in personal) "ویرایش معنی شخصی" else "افزودن معنی شخصی") }
                    Text("معنی شخصی در همهٔ فیلم‌ها اولویت دارد. ذخیره در دفترچه با جمله و زمان، از کارت روی فیلم انجام می‌شود.",
                        color = PinesPalette.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (editing) PersonalMeaningDialog(
                dao, term, detail?.meaning, term in personal,
                onSaved = { retry++ }, onDismiss = { editing = false }
            )
        }
    }
}
