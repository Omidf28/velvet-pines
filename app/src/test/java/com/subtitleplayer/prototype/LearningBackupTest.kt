package com.subtitleplayer.prototype

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class LearningBackupTest {
    private val word = SavedWord("went", "went", "go", "رفتن", "We went home.", 2500, 2000,
        "asset:///demo.mp4", "نمونه", id = 99, reviewLevel = 4, reviewDueAt = 123456, reviewCount = 7)
    private val backup = LearningBackup(listOf(word), listOf(PersonalDictionaryEntry("hello", "hello", "سلام\nمثال")),
        listOf(DictionaryBookmark("coffee", "coffee", "قهوه")),
        listOf(VocabularyList("ielts writing", "IELTS Writing")),
        listOf(VocabularyListWord("ielts writing", "coffee")))
    private fun bytes(value: LearningBackup = backup) =
        ByteArrayOutputStream().also { writeLearningBackup(value, it) }.toByteArray()

    @Test fun roundTripPreservesContentAndReviewButDoesNotReuseLocalIds() {
        val result = parseLearningBackup(bytes())
        assertEquals(backup.words.map { it.copy(id = 0) }, result.words)
        assertEquals(backup.dictionary, result.dictionary)
        assertEquals(backup.bookmarks, result.bookmarks)
        assertEquals(backup.lists, result.lists)
        assertEquals(backup.listWords, result.listWords)
    }
    @Test fun emptyBackupCanBeUsedOnANewInstallation() {
        val empty = LearningBackup(emptyList(), emptyList())
        assertEquals(empty, parseLearningBackup(bytes(empty)))
    }
    @Test fun rejectsCorruptionTruncationAndTrailingBytes() {
        assertThrows(IOException::class.java) { parseLearningBackup(bytes().copyOf(7)) }
        assertThrows(IOException::class.java) { parseLearningBackup(bytes() + byteArrayOf(1)) }
        assertThrows(IOException::class.java) { parseLearningBackup(bytes().also { it[0] = 0 }) }
    }
    @Test fun rejectsUnsupportedVersionEvenWithAValidChecksum() {
        val body = bytes().let { it.copyOf(it.size - 32) }.also { it[7] = 99 }
        val checksum = MessageDigest.getInstance("SHA-256").digest(body)
        val error = assertThrows(IOException::class.java) { parseLearningBackup(body + checksum) }
        assertEquals("Unsupported backup", error.message)
    }
    @Test fun rejectsInvalidProgressBeforeRestore() {
        val invalid = backup.copy(words = listOf(word.copy(reviewLevel = 9)))
        assertThrows(IOException::class.java) { parseLearningBackup(bytes(invalid)) }
    }
    @Test fun checksumDetectsPayloadChangesEvenIfTheTextRemainsValid() {
        val changed = bytes()
        changed[20] = (changed[20].toInt() xor 1).toByte()
        assertThrows(IOException::class.java) { parseLearningBackup(changed) }
    }
    @Test fun rejectsInconsistentWordKeyBeforeRestore() {
        val invalid = backup.copy(words = listOf(word.copy(wordKey = "different")))
        assertThrows(IOException::class.java) { parseLearningBackup(bytes(invalid)) }
    }
    @Test fun refusesExcessiveRecordCount() {
        assertThrows(IOException::class.java) {
            bytes(LearningBackup(List(20001) { word }, emptyList()))
        }
        assertThrows(IOException::class.java) {
            bytes(backup.copy(bookmarks = List(20001) { backup.bookmarks.single() }))
        }
    }
    @Test fun restoresVersionOneBackupsWithNoBookmarks() {
        val encoded = bytes(backup.copy(bookmarks = emptyList(), lists = emptyList(), listWords = emptyList()))
        // Version one ended immediately after personal definitions.
        val body = encoded.copyOf(encoded.size - 32 - 12).also { it[7] = 1 }
        val legacy = body + MessageDigest.getInstance("SHA-256").digest(body)
        val result = parseLearningBackup(legacy)
        assertEquals(backup.words.map { it.copy(id = 0) }, result.words)
        assertEquals(backup.dictionary, result.dictionary)
        assertTrue(result.bookmarks.isEmpty())
        assertTrue(result.lists.isEmpty())
    }
    @Test fun restoresVersionTwoBackupsWithNoLists() {
        val encoded = bytes(backup.copy(lists = emptyList(), listWords = emptyList()))
        val body = encoded.copyOf(encoded.size - 32 - 8).also { it[7] = 2 }
        val result = parseLearningBackup(body + MessageDigest.getInstance("SHA-256").digest(body))
        assertEquals(backup.bookmarks, result.bookmarks)
        assertTrue(result.lists.isEmpty())
        assertTrue(result.listWords.isEmpty())
    }
    @Test fun rejectsOrphanDuplicateAndMalformedLists() {
        for (invalid in listOf(
            backup.copy(lists = backup.lists + backup.lists),
            backup.copy(lists = listOf(VocabularyList("ielts writing", " IELTS Writing "))),
            backup.copy(lists = listOf(VocabularyList("wrong", "IELTS Writing"))),
            backup.copy(listWords = backup.listWords + backup.listWords),
            backup.copy(listWords = listOf(VocabularyListWord("missing", "coffee"))),
            backup.copy(listWords = listOf(VocabularyListWord("ielts writing", "missing")))
        )) assertThrows(IOException::class.java) { bytes(invalid) }
    }
    @Test fun rejectsInvalidBookmarkKeysAndDefinitionsBeforeRestore() {
        for (invalid in listOf(
            DictionaryBookmark("Coffee", "coffee", "قهوه"),
            DictionaryBookmark("coffee", "", "قهوه"),
            DictionaryBookmark("coffee", "coffee", "")
        )) {
            assertThrows(IOException::class.java) { parseLearningBackup(bytes(backup.copy(bookmarks = listOf(invalid), listWords = emptyList()))) }
        }
    }
}
