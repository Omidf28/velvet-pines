package com.subtitleplayer.prototype

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "personal_dictionary")
internal data class PersonalDictionaryEntry(
    @PrimaryKey val wordKey: String,
    val headword: String,
    val meaning: String
)

@Dao
internal interface PersonalDictionaryDao {
    @Query("SELECT * FROM personal_dictionary ORDER BY wordKey")
    fun observeAll(): Flow<List<PersonalDictionaryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: PersonalDictionaryEntry)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun merge(entries: List<PersonalDictionaryEntry>)

    @Query("DELETE FROM personal_dictionary WHERE wordKey = :key")
    suspend fun remove(key: String)
}
