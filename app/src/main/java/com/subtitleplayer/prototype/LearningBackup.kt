package com.subtitleplayer.prototype

import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.DigestOutputStream

internal data class LearningBackup(
    val words: List<SavedWord>,
    val dictionary: List<PersonalDictionaryEntry>,
    val bookmarks: List<DictionaryBookmark> = emptyList(),
    val lists: List<VocabularyList> = emptyList(),
    val listWords: List<VocabularyListWord> = emptyList()
)
internal const val learningBackupLimit = 32 * 1024 * 1024
private const val backupMagic = 0x53504c42
private const val backupVersion = 3

private fun validateBackupLists(backup: LearningBackup) {
    val keys = backup.lists.map { it.nameKey }.toSet()
    val words = backup.bookmarks.map { it.wordKey }.toSet()
    if (keys.size != backup.lists.size || backup.lists.any {
        validVocabularyListName(it.name) != it.name || vocabularyListKey(it.name) != it.nameKey
    } || backup.listWords.toSet().size != backup.listWords.size || backup.listWords.any {
        it.listKey !in keys || it.wordKey !in words
    }) throw IOException("Invalid vocabulary lists")
}

private class LimitedBackupOutput(private val target: OutputStream) : OutputStream() {
    private var remaining = learningBackupLimit
    override fun write(value: Int) {
        if (remaining < 1) throw IOException("Backup too large")
        target.write(value); remaining--
    }
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > remaining) throw IOException("Backup too large")
        target.write(bytes, offset, length); remaining -= length
    }
}

private fun DataOutputStream.text(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    if (bytes.size > maxSubtitleBytes) throw IOException("Field too large")
    writeInt(bytes.size); write(bytes)
}
private fun DataInputStream.text(): String {
    val length = readInt()
    if (length < 0 || length > maxSubtitleBytes || length > available()) throw IOException("Invalid field size")
    val bytes = ByteArray(length)
    readFully(bytes)
    return Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
}
private fun DataInputStream.count(): Int = readInt().also {
    if (it !in 0..20000) throw IOException("Invalid record count")
}

internal fun writeLearningBackup(backup: LearningBackup, output: OutputStream) {
    if (listOf(backup.words.size, backup.dictionary.size, backup.bookmarks.size,
            backup.lists.size, backup.listWords.size).any { it > 20000 })
        throw IOException("Too many records")
    validateBackupLists(backup)
    val limited = LimitedBackupOutput(output)
    val digest = MessageDigest.getInstance("SHA-256")
    val data = DataOutputStream(DigestOutputStream(limited, digest))
    data.writeInt(backupMagic); data.writeInt(backupVersion)
    data.writeInt(backup.words.size)
    backup.words.forEach { w ->
        listOf(w.word, w.wordKey, w.headword, w.meaning, w.dialogue, w.sourceUri, w.sourceName).forEach { data.text(it) }
        data.writeLong(w.positionMs); data.writeLong(w.subtitleStartMs)
        data.writeInt(w.reviewLevel); data.writeLong(w.reviewDueAt); data.writeInt(w.reviewCount)
    }
    data.writeInt(backup.dictionary.size)
    backup.dictionary.forEach { data.text(it.wordKey); data.text(it.headword); data.text(it.meaning) }
    data.writeInt(backup.bookmarks.size)
    backup.bookmarks.forEach { data.text(it.wordKey); data.text(it.headword); data.text(it.meaning) }
    data.writeInt(backup.lists.size)
    backup.lists.forEach { data.text(it.nameKey); data.text(it.name) }
    data.writeInt(backup.listWords.size)
    backup.listWords.forEach { data.text(it.listKey); data.text(it.wordKey) }
    data.flush()
    limited.write(digest.digest())
}

internal fun parseLearningBackup(bytes: ByteArray): LearningBackup {
    if (bytes.size > learningBackupLimit) throw IOException("Backup too large")
    if (bytes.size < 32) throw IOException("Truncated backup")
    val bodySize = bytes.size - 32
    val digest = MessageDigest.getInstance("SHA-256").apply { update(bytes, 0, bodySize) }.digest()
    if (!MessageDigest.isEqual(digest, bytes.copyOfRange(bodySize, bytes.size))) throw IOException("Backup checksum mismatch")
    val data = DataInputStream(ByteArrayInputStream(bytes, 0, bodySize))
    if (data.readInt() != backupMagic) throw IOException("Unsupported backup")
    val version = data.readInt()
    if (version !in 1..backupVersion) throw IOException("Unsupported backup")
    val words = List(data.count()) {
        val word = data.text(); val key = data.text(); val headword = data.text(); val meaning = data.text()
        val dialogue = data.text(); val source = data.text(); val name = data.text()
        val position = data.readLong(); val start = data.readLong(); val level = data.readInt()
        val due = data.readLong(); val reviews = data.readInt()
        if (word.isBlank() || key != normalizeWord(word) || headword.isBlank() || meaning.isBlank() || source.isBlank() ||
            position < 0 || start < 0 || level !in 0..5 || due < 0 || reviews < 0) throw IOException("Invalid word")
        SavedWord(word, key, headword, meaning, dialogue, position, start, source, name,
            reviewLevel = level, reviewDueAt = due, reviewCount = reviews)
    }
    val dictionary = List(data.count()) {
        val key = data.text(); val headword = data.text(); val meaning = data.text()
        if (normalizeDictionaryTerm(key) != key || normalizeDictionaryTerm(headword) == null ||
            validPersonalMeaning(meaning) != meaning) throw IOException("Invalid dictionary entry")
        PersonalDictionaryEntry(key, headword, meaning)
    }
    val bookmarks = if (version >= 2) List(data.count()) {
        val key = data.text(); val headword = data.text(); val meaning = data.text()
        if (normalizeDictionaryTerm(key) != key || normalizeDictionaryTerm(headword) == null ||
            validPersonalMeaning(meaning) != meaning) throw IOException("Invalid dictionary bookmark")
        DictionaryBookmark(key, headword, meaning)
    } else emptyList()
    val lists = if (version >= 3) List(data.count()) { VocabularyList(data.text(), data.text()) } else emptyList()
    val listWords = if (version >= 3) List(data.count()) { VocabularyListWord(data.text(), data.text()) } else emptyList()
    if (data.available() != 0) throw IOException("Unexpected trailing data")
    return LearningBackup(words, dictionary, bookmarks, lists, listWords).also(::validateBackupLists)
}
