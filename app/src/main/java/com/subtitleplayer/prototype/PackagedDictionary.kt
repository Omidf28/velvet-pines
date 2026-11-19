package com.subtitleplayer.prototype

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File


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
    fun search(input: String, personal: Map<String, WordMeaning>, after: String = "", level: DictionaryLevel? = null): DictionarySearchResult {
        persianSearchTokens(input)?.let { return searchPersian(it, personal, level) }
        val prefix = if (input.isBlank()) "" else
            normalizeDictionaryPrefix(input) ?: return DictionarySearchResult(emptyList(), false)
        val boundary = if (prefix.isEmpty()) after else ""
        val filter = if (level == null) "" else " AND term IN (SELECT term FROM learner_profiles WHERE level = ?)"
        val arguments = if (prefix.isEmpty()) mutableListOf(boundary)
            else mutableListOf(prefix, prefix + '\u007f')
        level?.let { arguments.add(it.name) }
        arguments.add((dictionarySearchLimit + 1).toString())
        return SQLiteDatabase.openDatabase(prepare().path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
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
                allowed = level?.let { db.levelTerms(it) }, findIndexed = { db.findMeaning(it) })
        }
    }

    private fun searchPersian(tokens: List<String>, personal: Map<String, WordMeaning>, level: DictionaryLevel?): DictionarySearchResult {
        val match = tokens.joinToString(" INTERSECT ") {
            "SELECT term FROM persian_terms WHERE token >= ? AND token < ?"
        }
        // Over-fetch enough rows to allow filtering every overridden definition.
        val limit = dictionarySearchLimit + 1 + dictionaryOverrideKeys(personal).size
        val filter = if (level == null) "" else " AND term IN (SELECT term FROM learner_profiles WHERE level = ?)"
        val arguments = tokens.flatMap { listOf(it, it + '\uffff') } +
            listOfNotNull(level?.name) + listOf(tokens.joinToString(" "), limit.toString())
        return SQLiteDatabase.openDatabase(prepare().path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
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
            mergePersianDictionarySearch(tokens, indexed, personal, level?.let { db.levelTerms(it) })
        }
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
