package com.subtitleplayer.prototype

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "dictionary_bookmarks")
internal data class DictionaryBookmark(
    @PrimaryKey val wordKey: String,
    val headword: String,
    val meaning: String
)

@Dao
internal interface DictionaryBookmarksDao {
    @Query("SELECT * FROM dictionary_bookmarks ORDER BY wordKey")
    fun observeAll(): Flow<List<DictionaryBookmark>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun save(entry: DictionaryBookmark)

    @Query("DELETE FROM dictionary_bookmarks WHERE wordKey = :key")
    suspend fun remove(key: String)

    @Query("SELECT vocabulary_lists.nameKey, vocabulary_lists.name, count(vocabulary_list_words.wordKey) AS wordCount FROM vocabulary_lists LEFT JOIN vocabulary_list_words ON vocabulary_lists.nameKey = vocabulary_list_words.listKey GROUP BY vocabulary_lists.nameKey, vocabulary_lists.name ORDER BY vocabulary_lists.nameKey")
    fun observeLists(): Flow<List<VocabularyListSummary>>

    @Query("SELECT wordKey FROM vocabulary_list_words WHERE listKey = :key ORDER BY wordKey")
    fun observeListWords(key: String): Flow<List<String>>

    @Query("SELECT listKey FROM vocabulary_list_words WHERE wordKey = :key ORDER BY listKey")
    fun observeListsForWord(key: String): Flow<List<String>>

    @Query("SELECT * FROM vocabulary_lists WHERE nameKey = :key LIMIT 1")
    suspend fun findList(key: String): VocabularyList?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertList(entry: VocabularyList): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertListWord(entry: VocabularyListWord)

    @Query("DELETE FROM vocabulary_list_words WHERE listKey = :listKey AND wordKey = :wordKey")
    suspend fun removeListWord(listKey: String, wordKey: String)

    @Query("UPDATE vocabulary_lists SET nameKey = :newKey, name = :name WHERE nameKey = :oldKey")
    suspend fun updateListName(oldKey: String, newKey: String, name: String): Int

    @Query("DELETE FROM vocabulary_lists WHERE nameKey = :key")
    suspend fun deleteList(key: String): Int

    @Transaction
    suspend fun createList(input: String, word: DictionaryBookmark? = null): VocabularyListWriteResult {
        val name = requireNotNull(validVocabularyListName(input))
        val key = vocabularyListKey(name)
        if (insertList(VocabularyList(key, name)) == -1L) return VocabularyListWriteResult.NameTaken
        if (word != null) {
            save(word)
            insertListWord(VocabularyListWord(key, word.wordKey))
        }
        return VocabularyListWriteResult.Updated
    }

    @Transaction
    suspend fun renameList(oldKey: String, input: String): VocabularyListWriteResult {
        val name = requireNotNull(validVocabularyListName(input))
        val key = vocabularyListKey(name)
        if (findList(oldKey) == null) return VocabularyListWriteResult.Missing
        if (key != oldKey && findList(key) != null) return VocabularyListWriteResult.NameTaken
        updateListName(oldKey, key, name)
        return VocabularyListWriteResult.Updated
    }

    @Transaction
    suspend fun setListWord(word: DictionaryBookmark, listKey: String, included: Boolean): Boolean {
        if (findList(listKey) == null) return false
        if (included) {
            save(word)
            insertListWord(VocabularyListWord(listKey, word.wordKey))
        } else removeListWord(listKey, word.wordKey)
        return true
    }
}

internal fun searchDictionaryBookmarks(
    entries: List<DictionaryBookmark>,
    input: String,
    after: String,
    personal: Map<String, WordMeaning>,
    allowed: Set<String>? = null
): DictionarySearchResult {
    val persian = persianSearchTokens(input)
    val prefix = if (input.isBlank() || persian != null) "" else
        normalizeDictionaryPrefix(input) ?: return DictionarySearchResult(emptyList(), false)
    val matches = entries.asSequence()
        .filter {
            it.wordKey.startsWith(prefix) && it.wordKey > after && (allowed == null || it.wordKey in allowed)
        }
        .sortedBy { it.wordKey }
        .map { entry ->
            val result = immediateDictionaryMeaning(entry.wordKey, personal)
            DictionarySearchEntry(entry.wordKey,
                result?.meaning ?: WordMeaning(entry.headword, entry.meaning),
                result?.source ?: "Saved dictionary entry")
        }
        .filter { persian == null || matchesPersianMeaning(it.meaning.persian, persian) }
        .sortedWith(if (persian == null) compareBy { it.term } else persianResultOrder(persian))
        .take(dictionarySearchLimit + 1)
        .toList()
    return DictionarySearchResult(matches.take(dictionarySearchLimit), matches.size > dictionarySearchLimit)
}
