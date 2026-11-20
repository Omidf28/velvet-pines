package com.subtitleplayer.prototype

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import org.json.JSONArray

internal data class DictionaryTopic(val id: String, val title: String, val wordCount: Int)


/** Separate read-only lexicon, never part of the user's Room database or backups. Call on IO. */
internal class PackagedDictionary(context: Context) {
    private val context = context.applicationContext

    fun resolve(term: String, personal: Map<String, WordMeaning>): DictionaryResult {
        val key = normalizeDictionaryTerm(term) ?: return DictionaryResult(null, "This term cannot be looked up")
        immediateDictionaryMeaning(key, personal)?.let { return it }
        val file = prepare()
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            return indexedDictionaryMeaning(key) { db.findMeaning(it) }
        }
    }

    /** Blank input browses by keyset; prefix searches use the same primary-key index. Call on IO. */
    fun search(input: String, personal: Map<String, WordMeaning>, after: String = "", level: DictionaryLevel? = null,
        topic: String? = null): DictionarySearchResult {
        persianSearchTokens(input)?.let { return searchPersian(it, personal, level, topic) }
        val prefix = if (input.isBlank()) "" else
            normalizeDictionaryPrefix(input) ?: return DictionarySearchResult(emptyList(), false)
        val boundary = if (prefix.isEmpty()) after else ""
        val filter = searchFilter(level, topic)
        val arguments = if (prefix.isEmpty()) mutableListOf(boundary)
            else mutableListOf(prefix, prefix + '\u007f')
        level?.let { arguments.add(it.name) }
        topic?.let { arguments.add(it) }
        arguments.add((dictionarySearchLimit + 1).toString())
        return openSearch(topic != null).use { db ->
            val indexed = db.rawQuery(
                if (prefix.isEmpty())
                    "SELECT term, definition FROM lexicon WHERE term > ?$filter ORDER BY term LIMIT ?"
                else "SELECT term, definition FROM lexicon WHERE term >= ? AND term < ?$filter ORDER BY term LIMIT ?",
                arguments.toTypedArray()
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val term = cursor.getString(0)
                        add(term to WordMeaning(term, cursor.getString(1)))
                    }
                }
            }
            mergeDictionarySearch(prefix, indexed, personal, after = boundary,
                allowed = db.allowedTerms(level, topic), findIndexed = { db.findMeaning(it) })
        }
    }

    private fun searchPersian(tokens: List<String>, personal: Map<String, WordMeaning>, level: DictionaryLevel?, topic: String?): DictionarySearchResult {
        val match = tokens.joinToString(" INTERSECT ") {
            "SELECT term FROM persian_terms WHERE token >= ? AND token < ?"
        }
        // Over-fetch enough rows to allow filtering every overridden definition.
        val limit = dictionarySearchLimit + 1 + dictionaryOverrideKeys(personal).size
        val filter = searchFilter(level, topic)
        val arguments = tokens.flatMap { listOf(it, it + '\uffff') } +
            listOfNotNull(level?.name, topic) + listOf(tokens.joinToString(" "), limit.toString())
        return openSearch(topic != null).use { db ->
            val indexed = db.rawQuery(
                "SELECT term, definition FROM lexicon WHERE term IN ($match)$filter " +
                    "ORDER BY NOT EXISTS (SELECT 1 FROM persian_terms AS exact " +
                    "WHERE exact.token = ? AND exact.term = lexicon.term AND exact.exact_meaning = 1), " +
                    "instr(term, ' ') > 0, length(term), term LIMIT ?",
                arguments.toTypedArray()
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val term = cursor.getString(0)
                        add(term to WordMeaning(term, cursor.getString(1)))
                    }
                }
            }
            mergePersianDictionarySearch(tokens, indexed, personal, db.allowedTerms(level, topic))
        }
    }

    private fun searchFilter(level: DictionaryLevel?, topic: String?): String =
        (if (level == null) "" else " AND term IN (SELECT term FROM learner_profiles WHERE level = ?)") +
            (if (topic == null) "" else " AND term IN (SELECT term FROM english_topics.topics WHERE topic = ?)")

    private fun openSearch(withTopics: Boolean): SQLiteDatabase {
        val englishFile = if (withTopics) prepareEnglishLexicon(context) else null
        val db = SQLiteDatabase.openDatabase(prepare().path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            if (englishFile != null) db.execSQL("ATTACH DATABASE ? AS english_topics", arrayOf(englishFile.path))
            return db
        } catch (error: Exception) { db.close(); throw error }
    }

    private fun SQLiteDatabase.allowedTerms(level: DictionaryLevel?, topic: String?): Set<String>? {
        if (topic == null) return level?.let { levelTerms(it) }
        val levelClause = if (level == null) "" else " AND term IN (SELECT term FROM learner_profiles WHERE level = ?)"
        return rawQuery("SELECT term FROM english_topics.topics WHERE topic = ?$levelClause",
            listOfNotNull(topic, level?.name).toTypedArray()).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
    }

    /** Counts describe the searchable English–Persian entries, rather than the entire source archive. */
    fun topics(): List<DictionaryTopic> {
        val config = context.assets.open("english-lexicon/topics.json").bufferedReader().use { JSONArray(it.readText()) }
        val counts = openSearch(true).use { db ->
            db.rawQuery("SELECT topic, count(*) FROM english_topics.topics JOIN lexicon ON topics.term = lexicon.term GROUP BY topic", null).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getInt(1)) }
            }
        }
        return List(config.length()) { index -> config.getJSONObject(index).let {
            DictionaryTopic(it.getString("id"), it.getString("title"), counts[it.getString("id")] ?: 0)
        } }
    }

    fun levelTerms(level: DictionaryLevel): Set<String> =
        SQLiteDatabase.openDatabase(prepare().path, null, SQLiteDatabase.OPEN_READONLY).use { it.levelTerms(level) }

    private fun SQLiteDatabase.levelTerms(level: DictionaryLevel): Set<String> = rawQuery(
        "SELECT DISTINCT term FROM learner_profiles WHERE level = ?", arrayOf(level.name)
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun profiles(term: String): List<LearnerProfile> {
        val key = normalizeDictionaryTerm(term) ?: return emptyList()
        return SQLiteDatabase.openDatabase(prepare().path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT level, pos, source FROM learner_profiles WHERE term = ? ORDER BY level, pos, source",
                arrayOf(key)).use { cursor ->
                buildList { while (cursor.moveToNext()) add(LearnerProfile(cursor.getString(0), cursor.getString(1), cursor.getString(2))) }
            }
        }
    }

    private fun SQLiteDatabase.findMeaning(term: String): WordMeaning? = rawQuery(
        "SELECT definition FROM lexicon WHERE term = ? LIMIT 1", arrayOf(term)
    ).use { cursor -> if (cursor.moveToFirst()) WordMeaning(term, cursor.getString(0)) else null }

    /** Check the bounded candidate set with one primary-key query. Call on IO. */
    fun matchingPhrases(keys: Set<String>): Set<String> {
        if (keys.isEmpty()) return emptySet()
        require(keys.size <= 35)
        val placeholders = keys.joinToString(",") { "?" }
        return SQLiteDatabase.openDatabase(prepare().path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT term FROM lexicon WHERE term IN ($placeholders)", keys.toTypedArray()).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
        }
    }
    private fun prepare(): File = preparePackagedLexicon(
        context, "lexicon", "lexicon", "dictionary-en-fa.sqlite.bin", 64L * 1024 * 1024
    )
}
