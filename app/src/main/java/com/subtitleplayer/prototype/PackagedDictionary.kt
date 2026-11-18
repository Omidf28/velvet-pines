package com.subtitleplayer.prototype

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream


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
    fun search(input: String, personal: Map<String, WordMeaning>, after: String = ""): DictionarySearchResult {
        persianSearchTokens(input)?.let { return searchPersian(it, personal) }
        val prefix = if (input.isBlank()) "" else
            normalizeDictionaryPrefix(input) ?: return DictionarySearchResult(emptyList(), false)
        val boundary = if (prefix.isEmpty()) after else ""
        return SQLiteDatabase.openDatabase(prepare().path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val indexed = db.rawQuery(
                if (prefix.isEmpty())
                    "SELECT term, definition FROM lexicon WHERE term > ? ORDER BY term LIMIT ?"
                else "SELECT term, definition FROM lexicon WHERE term >= ? AND term < ? ORDER BY term LIMIT ?",
                if (prefix.isEmpty()) arrayOf(boundary, (dictionarySearchLimit + 1).toString())
                else arrayOf(prefix, prefix + '\u007f', (dictionarySearchLimit + 1).toString())
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val term = cursor.getString(0)
                        add(term to WordMeaning(term, cursor.getString(1)))
                    }
                }
            }
            mergeDictionarySearch(prefix, indexed, personal, after = boundary, findIndexed = { db.findMeaning(it) })
        }
    }

    private fun searchPersian(tokens: List<String>, personal: Map<String, WordMeaning>): DictionarySearchResult {
        val match = tokens.joinToString(" INTERSECT ") {
            "SELECT term FROM persian_terms WHERE token >= ? AND token < ?"
        }
        // Over-fetch enough rows to allow filtering every overridden definition.
        val limit = dictionarySearchLimit + 1 + dictionaryOverrideKeys(personal).size
        val arguments = tokens.flatMap { listOf(it, it + '\uffff') } + listOf(tokens.joinToString(" "), limit.toString())
        return SQLiteDatabase.openDatabase(prepare().path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val indexed = db.rawQuery(
                "SELECT term, definition FROM lexicon WHERE term IN ($match) " +
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
            mergePersianDictionarySearch(tokens, indexed, personal)
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
    private fun prepare(): File = synchronized(installLock) {
        val manifest = context.assets.open("lexicon/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val hash = manifest.getString("databaseSha256")
        require(hash.matches(Regex("[a-f0-9]{64}")))
        val expectedBytes = manifest.getLong("databaseBytes")
        require(expectedBytes in 1..64L * 1024 * 1024)
        val file = File(context.noBackupFilesDir, "lexicon-$hash.sqlite")
        if (verifiedPath == file.path && file.length() == expectedBytes) return@synchronized file
        fun digest(target: File): String {
            val sha = MessageDigest.getInstance("SHA-256")
            target.inputStream().use { input ->
                val buffer = ByteArray(32768)
                while (true) { val n = input.read(buffer); if (n < 0) break; sha.update(buffer, 0, n) }
            }
            return sha.digest().joinToString("") { "%02x".format(it) }
        }
        if (file.length() != expectedBytes || digest(file) != hash) {
            val temp = File(context.noBackupFilesDir, "lexicon-install.tmp")
            try {
                GZIPInputStream(context.assets.open("lexicon/dictionary-en-fa.sqlite.bin")).use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(32768)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            check(total <= expectedBytes) { "Lexicon too large" }
                            output.write(buffer, 0, n)
                        }
                        check(total == expectedBytes) { "Incomplete lexicon" }
                    }
                }
                check(digest(temp) == hash) { "Lexicon checksum mismatch" }
                check(temp.renameTo(file)) { "Lexicon installation failed" }
            } finally { temp.delete() }
        }
        verifiedPath = file.path
        // Reclaim older packaged indexes after the replacement has been verified.
        context.noBackupFilesDir.listFiles()?.filter {
            it.name != file.name && it.name.matches(Regex("lexicon-[a-f0-9]{64}\\.sqlite"))
        }?.forEach { it.delete() }
        file
    }

    companion object {
        private val installLock = Any()
        private var verifiedPath: String? = null
    }
}
