package com.subtitleplayer.prototype

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import java.io.File

internal fun prepareEnglishLexicon(context: Context): File = preparePackagedLexicon(
    context, "english-lexicon", "english-lexicon", "dictionary-en.sqlite.bin", 128L * 1024 * 1024
)

internal data class EnglishPronunciationEntry(val ipa: String, val variety: String)

internal data class EnglishSense(
    val id: String,
    val definitions: List<String>,
    val examples: List<String>,
    val synonyms: List<String>,
    val antonyms: List<String>,
    val related: List<String>
)

internal data class EnglishLexicalEntry(
    val id: String,
    val lemma: String,
    val partOfSpeech: String,
    val pronunciations: List<EnglishPronunciationEntry>,
    val forms: List<String>,
    val senses: List<EnglishSense>
)

private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

internal class PackagedEnglishDictionary(context: Context) {
    private val context = context.applicationContext

    /** Include an exact English-only entry while preserving existing personalized results. Call on IO. */
    fun completeExactSearch(result: DictionarySearchResult, input: String, allowed: Set<String>?): DictionarySearchResult {
        val key = normalizeDictionaryTerm(input) ?: return result
        if (result.entries.any { it.term == key } || (allowed != null && key !in allowed)) return result
        val definition = lookup(key).asSequence().flatMap { it.senses.asSequence() }
            .flatMap { it.definitions.asSequence() }.firstOrNull() ?: return result
        val entries = (result.entries + DictionarySearchEntry(key, WordMeaning(key, definition),
            "Open English WordNet 2025")).sortedBy { it.term }
        return DictionarySearchResult(entries.take(dictionarySearchLimit), result.hasMore || entries.size > dictionarySearchLimit)
    }

    /** Exact terms take priority over source-provided form aliases. Call on IO. */
    fun lookup(term: String): List<EnglishLexicalEntry> {
        val key = normalizeDictionaryTerm(term) ?: return emptyList()
        val file = prepareEnglishLexicon(context)
        var matchingIds: Set<String>? = null
        val payloads = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val exact = db.rawQuery("SELECT payload FROM entries WHERE term = ? LIMIT 1", arrayOf(key)).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (exact != null) return@use listOf(exact)
            val forms = db.rawQuery("SELECT headword, entry_id FROM forms WHERE term = ?", arrayOf(key)).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
            }
            if (forms.isEmpty()) return emptyList()
            matchingIds = forms.map { it.second }.toSet()
            val headwords = forms.map { it.first }.distinct()
            val placeholders = headwords.joinToString(",") { "?" }
            db.rawQuery("SELECT payload FROM entries WHERE term IN ($placeholders) ORDER BY term", headwords.toTypedArray()).use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        }
        val parsed = payloads.flatMap(::parseEntries)
        val allowed = matchingIds
        return parsed.filter { allowed == null || it.id in allowed }
    }

    private fun parseEntries(payload: String): List<EnglishLexicalEntry> {
        val entries = JSONArray(payload)
        return List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            val pronunciations = entry.getJSONArray("pronunciations")
            val senses = entry.getJSONArray("senses")
            EnglishLexicalEntry(
                entry.getString("id"), entry.getString("lemma"), entry.getString("partOfSpeech"),
                List(pronunciations.length()) {
                    val pronunciation = pronunciations.getJSONObject(it)
                    EnglishPronunciationEntry(pronunciation.getString("ipa"), pronunciation.getString("variety"))
                },
                entry.getJSONArray("forms").strings(),
                List(senses.length()) {
                    val sense = senses.getJSONObject(it)
                    EnglishSense(sense.getString("id"), sense.getJSONArray("definitions").strings(),
                        sense.getJSONArray("examples").strings(), sense.getJSONArray("synonyms").strings(),
                        sense.getJSONArray("antonyms").strings(), sense.getJSONArray("related").strings())
                }
            )
        }
    }
}
