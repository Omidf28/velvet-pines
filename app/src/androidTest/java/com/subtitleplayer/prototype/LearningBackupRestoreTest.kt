package com.subtitleplayer.prototype

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningBackupRestoreTest {
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

            val snapshot = source.backup().snapshot()
            file.outputStream().use { writeLearningBackup(snapshot, it) }
            val backup = file.inputStream().use { parseLearningBackup(readSubtitleBytes(it, learningBackupLimit)) }
            assertEquals(LearningRestoreResult(1, 1), destination.backup().restore(backup))
            val restored = destination.backup().snapshot()
            val restoredWord = restored.words.single()
            assertEquals(snapshot.words.single().copy(id = 0), restoredWord.copy(id = 0))
            assertEquals(snapshot.dictionary, restored.dictionary)

            destination.words().save(word.copy(meaning = "رهسپار شدن"))
            assertEquals(1, destination.words().recordReview(restoredWord.id, expectedCount = 1, level = 3, dueAt = 20_000))
            destination.dictionary().put(definition.copy(meaning = "درود"))
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
