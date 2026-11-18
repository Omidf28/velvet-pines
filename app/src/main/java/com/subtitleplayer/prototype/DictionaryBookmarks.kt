package com.subtitleplayer.prototype

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
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
}

internal fun searchDictionaryBookmarks(
    entries: List<DictionaryBookmark>,
    input: String,
    after: String,
    personal: Map<String, WordMeaning>
): DictionarySearchResult {
    val prefix = if (input.isBlank()) "" else
        normalizeDictionaryPrefix(input) ?: return DictionarySearchResult(emptyList(), false)
    val matches = entries.asSequence()
        .filter { it.wordKey.startsWith(prefix) && it.wordKey > after }
        .sortedBy { it.wordKey }
        .take(dictionarySearchLimit + 1)
        .map { entry ->
            val result = immediateDictionaryMeaning(entry.wordKey, personal)
            DictionarySearchEntry(entry.wordKey,
                result?.meaning ?: WordMeaning(entry.headword, entry.meaning),
                result?.source ?: "Saved dictionary entry")
        }.toList()
    return DictionarySearchResult(matches.take(dictionarySearchLimit), matches.size > dictionarySearchLimit)
}
