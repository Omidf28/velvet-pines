package com.subtitleplayer.prototype

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
internal fun VocabularyStatsDialog(words: List<SavedWord>, now: Long, onDismiss: () -> Unit) {
    val unique = remember(words) { words.map { normalizeDictionaryTerm(it.wordKey) ?: normalizeWord(it.wordKey) }.toSet().size }
    val answers = remember(words) { words.sumOf { it.reviewCount.coerceAtLeast(0).toLong() } }
    val newCount = remember(words) { words.count { it.reviewCount == 0 } }
    val due = remember(words, now) { words.count { it.reviewDueAt <= now } }
    val next = remember(words, now) { words.filter { it.reviewDueAt > now }.minOfOrNull { it.reviewDueAt } }
    val levels = remember(words) { words.filter { it.reviewCount > 0 }.groupingBy { it.reviewLevel }.eachCount() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("آمار دفترچه") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsRow("کارت‌های ذخیره‌شده", words.size.toString())
                StatsRow("واژه / عبارت متفاوت", unique.toString())
                StatsRow("کارت‌های جدید", newCount.toString())
                StatsRow("کارت‌های مرورشده", (words.size - newCount).toString())
                StatsRow("آمادهٔ مرور، شامل جدیدها", due.toString())
                StatsRow("منتظر موعد بعدی", (words.size - due).toString())
                StatsRow("پاسخ‌های مرور کارت‌های فعلی", answers.toString())
                next?.let {
                    Text(
                        "نزدیک‌ترین موعد آینده: " +
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text("سطح کارت‌های مرورشده", style = MaterialTheme.typography.titleSmall)
                listOf("شروع دوباره · ۱۰ دقیقه", "سطح ۱ · ۱ روز", "سطح ۲ · ۳ روز",
                    "سطح ۳ · ۷ روز", "سطح ۴ · ۱۴ روز", "سطح ۵ · ۳۰ روز").forEachIndexed { level, label ->
                    StatsRow(label, (levels[level] ?: 0).toString())
                }
                Text(
                    "آمار مربوط به کل دفترچه است و از جست‌وجو و فیلتر پیروی نمی‌کند. یک واژه در دیالوگ‌های متفاوت می‌تواند چند کارت داشته باشد. سطح مرور نشان‌دهندهٔ فاصلهٔ مرور است.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
