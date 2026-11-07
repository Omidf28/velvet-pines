package com.subtitleplayer.prototype

import java.util.Locale

/** Numeric runs compare without integer conversion, including Persian/Arabic digits. */
internal fun compareNaturalNames(left: String, right: String): Int {
    val a = left.lowercase(Locale.ROOT)
    val b = right.lowercase(Locale.ROOT)
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val da = Character.digit(a[i], 10)
        val db = Character.digit(b[j], 10)
        if (da >= 0 && db >= 0) {
            val beginA = i
            val beginB = j
            while (i < a.length && Character.digit(a[i], 10) >= 0) i++
            while (j < b.length && Character.digit(b[j], 10) >= 0) j++
            var significantA = beginA
            var significantB = beginB
            while (significantA < i && Character.digit(a[significantA], 10) == 0) significantA++
            while (significantB < j && Character.digit(b[significantB], 10) == 0) significantB++
            val length = (i - significantA).compareTo(j - significantB)
            if (length != 0) return length
            for (k in 0 until i - significantA) {
                val digit = Character.digit(a[significantA + k], 10).compareTo(Character.digit(b[significantB + k], 10))
                if (digit != 0) return digit
            }
            val zeros = (i - beginA).compareTo(j - beginB)
            if (zeros != 0) return zeros
        } else {
            val charA = if (da >= 0) '0' + da else a[i]
            val charB = if (db >= 0) '0' + db else b[j]
            val comparison = charA.compareTo(charB)
            if (comparison != 0) return comparison
            i++; j++
        }
    }
    val remainder = (a.length - i).compareTo(b.length - j)
    return if (remainder != 0) remainder else left.compareTo(right)
}

internal enum class LibraryDisplayOrder(val label: String) {
    Default("Default"), NameAscending("Name A–Z"), NameDescending("Name Z–A")
}

internal enum class WordDisplayOrder(val label: String) {
    Newest("Newest first"), Oldest("Oldest first"),
    Alphabetical("Word A–Z"), Due("Review due first")
}

internal fun orderedSavedWords(words: List<SavedWord>, order: WordDisplayOrder): List<SavedWord> = when (order) {
    WordDisplayOrder.Newest -> words.sortedByDescending { it.id }
    WordDisplayOrder.Oldest -> words.sortedBy { it.id }
    WordDisplayOrder.Alphabetical -> words.sortedWith { a, b ->
        val name = compareNaturalNames(a.word, b.word)
        if (name != 0) name else b.id.compareTo(a.id)
    }
    WordDisplayOrder.Due -> words.sortedWith(compareBy<SavedWord> { it.reviewDueAt }.thenBy { it.id })
}
