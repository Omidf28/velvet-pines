package com.subtitleplayer.prototype

import java.io.IOException
import java.io.Writer

private fun escapeDictionaryField(text: String): String = text.replace("\\", "\\\\")
    .replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

private fun unescapeDictionaryField(text: String): String = buildString {
    var i = 0
    while (i < text.length) {
        val c = text[i++]
        if (c != '\\') append(c) else {
            if (i == text.length) throw IOException("Incomplete escape")
            append(when (text[i++]) {
                '\\' -> '\\'; 't' -> '\t'; 'n' -> '\n'; 'r' -> '\r'
                else -> throw IOException("Unknown escape")
            })
        }
    }
}

internal fun writeDictionary(entries: List<PersonalDictionaryEntry>, writer: Writer) {
    if (entries.size > 20000) throw IOException("Maximum 20000 rows")
    val header = "word\theadword\tmeaning\n"
    var bytes = header.toByteArray(Charsets.UTF_8).size.toLong()
    entries.forEach { entry ->
        if (normalizeDictionaryTerm(entry.wordKey) != entry.wordKey ||
            normalizeDictionaryTerm(entry.headword) == null || validPersonalMeaning(entry.meaning) != entry.meaning
        ) throw IOException("Invalid dictionary entry")
        bytes += listOf(entry.wordKey, entry.headword, entry.meaning)
            .joinToString("\t", transform = ::escapeDictionaryField).toByteArray(Charsets.UTF_8).size + 1
        if (bytes > maxSubtitleBytes) throw IOException("Dictionary exceeds size limit")
    }
    writer.write(header)
    entries.forEach { entry ->
        writer.write(listOf(entry.wordKey, entry.headword, entry.meaning).joinToString("\t", transform = ::escapeDictionaryField))
        writer.write("\n")
    }
}

internal fun parseDictionary(text: String): List<PersonalDictionaryEntry> {
    val lines = text.removePrefix("\uFEFF").replace("\r\n", "\n").lineSequence().iterator()
    if (!lines.hasNext() || lines.next() != "word\theadword\tmeaning") throw IOException("Expected word / headword / meaning header")
    val entries = linkedMapOf<String, PersonalDictionaryEntry>()
    var row = 1
    while (lines.hasNext()) {
        val line = lines.next()
        row++
        if (line.isBlank()) continue
        if (row > 20_001) throw IOException("Maximum 20000 rows")
        try {
            val fields = line.split('\t').map(::unescapeDictionaryField)
            if (fields.size != 3) throw IOException("Expected 3 fields")
            val key = normalizeDictionaryTerm(fields[0]) ?: throw IOException("Invalid word")
            if (normalizeDictionaryTerm(fields[1]) == null) throw IOException("Invalid headword")
            val meaning = validPersonalMeaning(fields[2]) ?: throw IOException("Invalid meaning")
            entries[key] = PersonalDictionaryEntry(key, fields[1].trim(), meaning)
        } catch (e: IOException) { throw IOException("Line $row: ${e.message}", e) }
    }
    if (entries.isEmpty()) throw IOException("Empty dictionary")
    return entries.values.toList()
}
