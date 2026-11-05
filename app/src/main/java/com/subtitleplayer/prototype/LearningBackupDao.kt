package com.subtitleplayer.prototype

import androidx.room.*

@Dao
internal interface LearningBackupDao {
    @Query("SELECT * FROM saved_words ORDER BY id")
    suspend fun allWords(): List<SavedWord>
    @Query("SELECT * FROM personal_dictionary ORDER BY wordKey")
    suspend fun allDictionary(): List<PersonalDictionaryEntry>

    @Transaction
    suspend fun snapshot(): LearningBackup = LearningBackup(allWords(), allDictionary())

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreWords(words: List<SavedWord>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreDictionary(entries: List<PersonalDictionaryEntry>): List<Long>

    @Transaction
    suspend fun restore(backup: LearningBackup): Int =
        restoreWords(backup.words.map { it.copy(id = 0) }).count { it != -1L } +
            restoreDictionary(backup.dictionary).count { it != -1L }
}
