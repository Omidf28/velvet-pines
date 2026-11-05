package com.subtitleplayer.prototype

import java.io.Writer

internal data class VocabularyExportRow(
    val word: String,
    val headword: String,
    val meaning: String,
    val dialogue: String,
    val sourceName: String,
    val positionMs: Long,
    val reviewLevel: Int,
    val reviewDueAt: Long,
    val reviewCount: Int
)

private fun csvText(value: String): String {
    // Quoting alone does not stop spreadsheet software interpreting a cell as a formula.
    val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@') ||
        value.firstOrNull() in listOf('\t', '\r', '\n')) "'" + value else value
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

/** Streams UTF-8 CSV content to the caller's writer without retaining a second full copy. */
internal fun writeVocabularyCsv(rows: List<VocabularyExportRow>, writer: Writer) {
    writer.write("\uFEFF")
    writer.write("word,headword,meaning,dialogue,source_name,position_ms,review_level,review_due_at_epoch_ms,review_count\r\n")
    rows.forEach { row ->
        val cells = listOf(row.word, row.headword, row.meaning, row.dialogue, row.sourceName).map(::csvText) +
            listOf(row.positionMs, row.reviewLevel, row.reviewDueAt, row.reviewCount).map { it.toString() }
        writer.write(cells.joinToString(","))
        writer.write("\r\n")
    }
}
