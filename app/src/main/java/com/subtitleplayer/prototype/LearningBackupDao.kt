package com.subtitleplayer.prototype

import androidx.room.*

internal data class LearningRestoreResult(val cards: Int, val personalDefinitions: Int, val bookmarks: Int = 0,
    val lists: Int = 0, val listEntries: Int = 0)

@Dao
internal interface LearningBackupDao {
    @Query("SELECT * FROM saved_words ORDER BY id")
    suspend fun allWords(): List<SavedWord>
    @Query("SELECT * FROM personal_dictionary ORDER BY wordKey")
    suspend fun allDictionary(): List<PersonalDictionaryEntry>
    @Query("SELECT * FROM dictionary_bookmarks ORDER BY wordKey")
    suspend fun allBookmarks(): List<DictionaryBookmark>
    @Query("SELECT * FROM vocabulary_lists ORDER BY nameKey")
    suspend fun allLists(): List<VocabularyList>
    @Query("SELECT * FROM vocabulary_list_words ORDER BY listKey, wordKey")
    suspend fun allListWords(): List<VocabularyListWord>

    @Transaction
    suspend fun snapshot(): LearningBackup = LearningBackup(allWords(), allDictionary(), allBookmarks(), allLists(), allListWords())

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreWords(words: List<SavedWord>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreDictionary(entries: List<PersonalDictionaryEntry>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreBookmarks(entries: List<DictionaryBookmark>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreLists(entries: List<VocabularyList>): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreListWords(entries: List<VocabularyListWord>): List<Long>

    @Transaction
    suspend fun restore(backup: LearningBackup): LearningRestoreResult = LearningRestoreResult(
        cards = restoreWords(backup.words.map { it.copy(id = 0) }).count { it != -1L },
        personalDefinitions = restoreDictionary(backup.dictionary).count { it != -1L },
        bookmarks = restoreBookmarks(backup.bookmarks).count { it != -1L },
        lists = restoreLists(backup.lists).count { it != -1L },
        listEntries = restoreListWords(backup.listWords).count { it != -1L }
    )
}
