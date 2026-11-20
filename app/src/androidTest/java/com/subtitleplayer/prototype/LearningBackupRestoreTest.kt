package com.subtitleplayer.prototype

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningBackupRestoreTest {
    @Test
    fun listsSupportMultipleMembershipsRenameAndLosslessBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, SavedWordsDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, SavedWordsDatabase::class.java).build()
        try {
            val dao = source.bookmarks()
            val word = DictionaryBookmark("coffee", "coffee", "قهوه")
            assertFalse(dao.setListWord(word, "missing", true))
            assertTrue(source.backup().allBookmarks().isEmpty())
            assertEquals(VocabularyListWriteResult.Updated, dao.createList(" IELTS  Writing ", word))
            assertEquals(VocabularyListWriteResult.NameTaken, dao.createList("ielts writing"))
            assertEquals(VocabularyListWriteResult.Updated, dao.createList("Work"))
            assertTrue(dao.setListWord(word, "work", true))
            assertTrue(dao.setListWord(word, "work", true))
            assertEquals(2, source.backup().allListWords().size)
            assertEquals(VocabularyListWriteResult.NameTaken, dao.renameList("work", "IELTS Writing"))
            assertEquals(VocabularyListWriteResult.Updated, dao.renameList("ielts writing", "Exam"))
            assertEquals(setOf("exam", "work"), source.backup().allListWords().map { it.listKey }.toSet())
            val snapshot = source.backup().snapshot()
            val bytes = java.io.ByteArrayOutputStream().also { writeLearningBackup(snapshot, it) }.toByteArray()
            val backup = parseLearningBackup(bytes)
            assertEquals(LearningRestoreResult(0, 0, 1, 2, 2), destination.backup().restore(backup))
            assertEquals(snapshot, destination.backup().snapshot())
            assertEquals(LearningRestoreResult(0, 0), destination.backup().restore(backup))
            assertTrue(dao.setListWord(word, "work", false))
            assertEquals(1, source.backup().allListWords().size)
            assertEquals(listOf(word), source.backup().allBookmarks())
            dao.deleteList("exam")
            assertTrue(source.backup().allListWords().isEmpty())
            assertEquals(listOf(word), source.backup().allBookmarks())
            destination.bookmarks().remove("coffee")
            assertTrue(destination.backup().allListWords().isEmpty())
            assertEquals(2, destination.backup().allLists().size)
        } finally { destination.close(); source.close() }
    }

    @Test
    fun backupRestoresReviewProgressAndKeepsExistingEdits() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = Room.inMemoryDatabaseBuilder(context, SavedWordsDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, SavedWordsDatabase::class.java).build()
        val file = File.createTempFile("velvet-learning-", ".spbackup", context.cacheDir)
        try {
            val word = SavedWord(
                word = "went",
                wordKey = "went",
                headword = "go",
                meaning = "رفتن",
                dialogue = "We went home.",
                positionMs = 2_500,
                subtitleStartMs = 2_000,
                sourceUri = "asset:///demo.mp4",
                sourceName = "Sample video"
            )
            val definition = PersonalDictionaryEntry("hello", "hello", "سلام")
            val sourceId = source.words().insertWord(word)
            assertEquals(1, source.words().recordReview(sourceId, expectedCount = 0, level = 2, dueAt = 12_000))
            source.dictionary().put(definition)
            val bookmark = DictionaryBookmark("forest", "forest", "جنگل")
            source.bookmarks().save(bookmark)

            val snapshot = source.backup().snapshot()
            file.outputStream().use { writeLearningBackup(snapshot, it) }
            val backup = file.inputStream().use { parseLearningBackup(readSubtitleBytes(it, learningBackupLimit)) }
            assertEquals(LearningRestoreResult(1, 1, 1), destination.backup().restore(backup))
            val restored = destination.backup().snapshot()
            val restoredWord = restored.words.single()
            assertEquals(snapshot.words.single().copy(id = 0), restoredWord.copy(id = 0))
            assertEquals(snapshot.dictionary, restored.dictionary)
            assertEquals(snapshot.bookmarks, restored.bookmarks)

            destination.words().save(word.copy(meaning = "رهسپار شدن"))
            assertEquals(1, destination.words().recordReview(restoredWord.id, expectedCount = 1, level = 3, dueAt = 20_000))
            destination.dictionary().put(definition.copy(meaning = "درود"))
            destination.bookmarks().remove("forest")
            destination.bookmarks().save(bookmark.copy(meaning = "معنی تازه"))
            val edited = destination.backup().snapshot()

            assertEquals(LearningRestoreResult(0, 0), destination.backup().restore(backup))
            assertEquals(edited, destination.backup().snapshot())
        } finally {
            file.delete()
            destination.close()
            source.close()
        }
    }
}
