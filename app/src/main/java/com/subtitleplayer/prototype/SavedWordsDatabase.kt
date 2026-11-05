package com.subtitleplayer.prototype

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "saved_words",
    indices = [Index(value = ["sourceUri", "subtitleStartMs", "dialogue", "wordKey"], unique = true)]
)
internal data class SavedWord(
    val word: String,
    val wordKey: String,
    val headword: String,
    val meaning: String,
    val dialogue: String,
    val positionMs: Long,
    val subtitleStartMs: Long,
    val sourceUri: String,
    val sourceName: String,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "0") val reviewLevel: Int = 0,
    @ColumnInfo(defaultValue = "0") val reviewDueAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val reviewCount: Int = 0
)

@Dao
internal interface SavedWordsDao {
    @Query("SELECT * FROM saved_words ORDER BY id DESC")
    fun observeAll(): Flow<List<SavedWord>>

    @Query("DELETE FROM saved_words WHERE id = :id")
    suspend fun deleteWord(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWord(word: SavedWord): Long

    @Transaction
    suspend fun save(word: SavedWord) {
        if (insertWord(word) == -1L) updateDefinition(
            word.sourceUri, word.subtitleStartMs, word.dialogue, word.wordKey, word.headword, word.meaning
        )
    }

    @Query("UPDATE saved_words SET headword = :headword, meaning = :meaning WHERE sourceUri = :source AND subtitleStartMs = :start AND dialogue = :dialogue AND wordKey = :key")
    suspend fun updateDefinition(source: String, start: Long, dialogue: String, key: String, headword: String, meaning: String)

    @Query("SELECT * FROM saved_words WHERE reviewDueAt <= :now ORDER BY reviewDueAt, id LIMIT 50")
    suspend fun dueForReview(now: Long): List<SavedWord>

    @Query("SELECT * FROM saved_words WHERE sourceUri = :source AND reviewDueAt <= :now ORDER BY reviewDueAt, id LIMIT 50")
    suspend fun dueForSource(now: Long, source: String): List<SavedWord>

    @Query("UPDATE saved_words SET reviewLevel = :level, reviewDueAt = :dueAt, reviewCount = reviewCount + 1 WHERE id = :id AND reviewCount = :expectedCount")
    suspend fun recordReview(id: Long, expectedCount: Int, level: Int, dueAt: Long): Int
}

// Preserve existing vocabulary when upgrading the database.
internal val vocabularyMigration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE saved_words ADD COLUMN reviewLevel INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE saved_words ADD COLUMN reviewDueAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE saved_words ADD COLUMN reviewCount INTEGER NOT NULL DEFAULT 0")
    }
}

internal val vocabularyMigration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS personal_dictionary (wordKey TEXT NOT NULL, headword TEXT NOT NULL, meaning TEXT NOT NULL, PRIMARY KEY(wordKey))")
    }
}

@Database(entities = [SavedWord::class, PersonalDictionaryEntry::class], version = 3, exportSchema = false)
internal abstract class SavedWordsDatabase : RoomDatabase() {
    abstract fun words(): SavedWordsDao
    abstract fun dictionary(): PersonalDictionaryDao
    abstract fun backup(): LearningBackupDao

    companion object {
        @Volatile
        private var instance: SavedWordsDatabase? = null

        fun get(context: Context): SavedWordsDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SavedWordsDatabase::class.java,
                "saved-words.db"
            ).addMigrations(vocabularyMigration1To2, vocabularyMigration2To3).build().also { instance = it }
        }
    }
}
