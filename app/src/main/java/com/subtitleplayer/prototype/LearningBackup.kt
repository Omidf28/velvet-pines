package com.subtitleplayer.prototype

import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.DigestOutputStream

internal data class LearningBackup(val words: List<SavedWord>, val dictionary: List<PersonalDictionaryEntry>)
internal const val learningBackupLimit = 32 * 1024 * 1024
private const val backupMagic = 0x53504c42
private const val backupVersion = 1

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
    if (backup.words.size > 20000 || backup.dictionary.size > 20000) throw IOException("Too many records")
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
    if (data.readInt() != backupMagic || data.readInt() != backupVersion) throw IOException("Unsupported backup")
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
    if (data.available() != 0) throw IOException("Unexpected trailing data")
    return LearningBackup(words, dictionary)
}
