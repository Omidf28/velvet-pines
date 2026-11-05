package com.subtitleplayer.prototype

import java.io.Writer

internal enum class VocabularyExportFormat { Csv, Anki }

private fun ankiHtml(text: String): String = text.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
    .replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<br>").replace("\t", " ")

internal fun writeAnkiVocabulary(rows: List<VocabularyExportRow>, writer: Writer) {
    writer.write("#separator:Tab\n#html:true\n#columns:Front\tBack\n")
    rows.forEach { row ->
        val word = ankiHtml(row.word)
        val lemma = if (row.word.equals(row.headword, true)) "" else " → " + ankiHtml(row.headword)
        val source = ankiHtml(row.sourceName) + " · " + formatTime(row.positionMs)
        val front = "<div dir=\"ltr\"><b>$word$lemma</b><br>" + ankiHtml(row.dialogue) + "</div><small>$source</small>"
        val back = "<div dir=\"rtl\">" + ankiHtml(row.meaning) + "</div><small>$source</small>"
        writer.write(front + "\t" + back + "\n")
    }
}
