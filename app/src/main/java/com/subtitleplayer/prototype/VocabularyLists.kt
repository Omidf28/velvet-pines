package com.subtitleplayer.prototype

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "vocabulary_lists")
internal data class VocabularyList(@PrimaryKey val nameKey: String, val name: String)

@Entity(
    tableName = "vocabulary_list_words",
    primaryKeys = ["listKey", "wordKey"],
    indices = [Index(value = ["wordKey"])],
    foreignKeys = [
        ForeignKey(entity = VocabularyList::class, parentColumns = ["nameKey"], childColumns = ["listKey"],
            onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = DictionaryBookmark::class, parentColumns = ["wordKey"], childColumns = ["wordKey"],
            onDelete = ForeignKey.CASCADE)
    ]
)
internal data class VocabularyListWord(val listKey: String, val wordKey: String)

internal data class VocabularyListSummary(val nameKey: String, val name: String, val wordCount: Int)
internal enum class VocabularyListWriteResult { Updated, NameTaken, Missing }

internal fun validVocabularyListName(input: String): String? {
    if (input.any { it.isISOControl() }) return null
    val name = input.trim().replace(Regex("[ \u00a0]+"), " ")
    return name.takeIf { it.length in 1..80 }
}

internal fun vocabularyListKey(name: String): String = name.lowercase(Locale.ROOT)
