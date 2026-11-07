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
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val dialogueSearchSpaces = Regex("[\\s\\u00A0]+")
private fun dialogueSearchKey(text: String): String =
    text.lowercase(Locale.ROOT).replace('’', '\'').trim().replace(dialogueSearchSpaces, " ")

@Composable
internal fun DialogueList(
    dialogues: List<DialogueRange>,
    positionMs: Long,
    onSeek: (DialogueRange) -> Unit,
    onReplay: (DialogueRange) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val key = remember(query) { dialogueSearchKey(query) }
    var searchable by remember(dialogues) { mutableStateOf<List<String>?>(null) }
    var filtered by remember(dialogues, key) {
        mutableStateOf<List<DialogueRange>?>(if (key.isEmpty()) dialogues else null)
    }
    LaunchedEffect(dialogues, key) {
        if (key.isEmpty()) return@LaunchedEffect
        delay(180)
        val keys = searchable ?: withContext(Dispatchers.Default) {
            dialogues.map { dialogue ->
                ensureActive()
                dialogueSearchKey(dialogue.text)
            }
        }.also { searchable = it }
        filtered = withContext(Dispatchers.Default) {
            dialogues.filterIndexed { index, _ ->
                ensureActive()
                key in keys[index]
            }
        }
    }

    val currentIndex = dialogueUpperBound(dialogues, positionMs) - 1
    val current = dialogues.getOrNull(currentIndex)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(filtered) {
        if (!filtered.isNullOrEmpty()) {
            listState.scrollToItem(if (key.isEmpty()) currentIndex + 1 else 0)
        }
    }

    PlayerOverlayPanel("دیالوگ‌ها", onDismiss = { keyboard?.hide(); onDismiss() }) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("جست‌وجوی جمله یا کلمه") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            trailingIcon = {
                if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("پاک") }
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
        )
        val results = filtered
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(when {
                    results == null -> "در حال جست‌وجو…"
                    dialogues.isEmpty() -> "دیالوگی در بازهٔ این ویدیو وجود ندارد."
                    results.isEmpty() -> "دیالوگی مطابق جست‌وجو پیدا نشد."
                    else -> "نمایش ${results.size} از ${dialogues.size} دیالوگ"
                }, style = MaterialTheme.typography.bodySmall)
            }
            items(results.orEmpty()) { dialogue ->
                val colors = MaterialTheme.colorScheme
                Card(colors = CardDefaults.cardColors(
                    containerColor = if (dialogue == current) colors.secondaryContainer else colors.surfaceVariant
                )) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable(onClickLabel = "رفتن به دیالوگ") {
                            keyboard?.hide()
                            onSeek(dialogue)
                        }.padding(10.dp)) {
                            Text(formatTime(dialogue.startMs), style = MaterialTheme.typography.labelMedium)
                            Text(dialogue.text,
                                style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
                        }
                        TextButton(onClick = { keyboard?.hide(); onReplay(dialogue) }) { Text("بازپخش") }
                    }
                }
            }
        }
        TextButton(enabled = current != null, onClick = {
            keyboard?.hide()
            if (key.isNotEmpty()) query = ""
            else scope.launch { listState.animateScrollToItem(currentIndex + 1) }
        }) { Text("دیالوگ فعلی") }
    }
}
