package com.subtitleplayer.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch

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
    val filtered = remember(dialogues, query) {
        val key = dialogueSearchKey(query)
        if (key.isEmpty()) dialogues else dialogues.filter { key in dialogueSearchKey(it.text) }
    }
    val currentIndex = dialogues.indexOfLast { it.startMs <= positionMs }
    val current = dialogues.getOrNull(currentIndex)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(query, dialogues) {
        listState.scrollToItem(if (query.isBlank()) currentIndex.coerceAtLeast(0) else 0)
    }
    fun close() { keyboard?.hide(); onDismiss() }

    AlertDialog(
        onDismissRequest = { close() },
        title = { Text("دیالوگ‌ها") },
        text = {
            Column(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("جست‌وجوی جمله یا کلمه") },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("پاک") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr)
                )
                Text("نمایش ${filtered.size} از ${dialogues.size} دیالوگ", style = MaterialTheme.typography.bodySmall)
                if (filtered.isEmpty()) {
                    Text(if (dialogues.isEmpty()) "دیالوگی در بازهٔ این ویدیو وجود ندارد."
                        else "دیالوگی مطابق جست‌وجو پیدا نشد.")
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered) { dialogue ->
                            val colors = MaterialTheme.colorScheme
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (dialogue == current) colors.secondaryContainer
                                    else colors.surfaceVariant
                                )
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(
                                        Modifier.weight(1f).clickable(onClickLabel = "رفتن به دیالوگ") {
                                            keyboard?.hide()
                                            onSeek(dialogue)
                                        }.padding(10.dp)
                                    ) {
                                        Text(formatTime(dialogue.startMs), style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            dialogue.text,
                                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr)
                                        )
                                    }
                                    TextButton(onClick = { keyboard?.hide(); onReplay(dialogue) }) { Text("تکرار") }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(enabled = current != null, onClick = {
                keyboard?.hide()
                if (query.isNotEmpty()) query = ""
                else scope.launch { listState.animateScrollToItem(currentIndex.coerceAtLeast(0)) }
            }) { Text("دیالوگ فعلی") }
        },
        confirmButton = { TextButton(onClick = { close() }) { Text("بستن") } }
    )
}
