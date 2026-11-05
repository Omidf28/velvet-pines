package com.subtitleplayer.prototype

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class LearningBackupTest {
    private val word = SavedWord("went", "went", "go", "رفتن", "We went home.", 2500, 2000,
        "asset:///demo.mp4", "نمونه", id = 99, reviewLevel = 4, reviewDueAt = 123456, reviewCount = 7)
    private val backup = LearningBackup(listOf(word), listOf(PersonalDictionaryEntry("hello", "hello", "سلام\nمثال")))
    private fun bytes(value: LearningBackup = backup) =
        ByteArrayOutputStream().also { writeLearningBackup(value, it) }.toByteArray()

    @Test fun roundTripPreservesContentAndReviewButDoesNotReuseLocalIds() {
        val result = parseLearningBackup(bytes())
        assertEquals(backup.words.map { it.copy(id = 0) }, result.words)
        assertEquals(backup.dictionary, result.dictionary)
    }
    @Test fun emptyBackupCanBeUsedOnANewInstallation() {
        val empty = LearningBackup(emptyList(), emptyList())
        assertEquals(empty, parseLearningBackup(bytes(empty)))
    }
    @Test fun rejectsCorruptionTruncationAndTrailingBytes() {
        assertThrows(IOException::class.java) { parseLearningBackup(bytes().copyOf(7)) }
        assertThrows(IOException::class.java) { parseLearningBackup(bytes() + byteArrayOf(1)) }
        assertThrows(IOException::class.java) { parseLearningBackup(bytes().also { it[0] = 0 }) }
        assertThrows(IOException::class.java) { parseLearningBackup(bytes().also { it[7] = 99 }) }
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
    }
}
