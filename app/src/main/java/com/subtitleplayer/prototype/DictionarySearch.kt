package com.subtitleplayer.prototype

internal data class DictionarySearchEntry(val term: String, val meaning: WordMeaning, val source: String)
internal data class DictionarySearchResult(val entries: List<DictionarySearchEntry>, val hasMore: Boolean)
internal const val dictionarySearchLimit = 40
private val dictionaryPrefixSpaces = Regex("[ \\t\\u00a0]+")
private val dictionaryPrefixPattern = Regex("""[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*)*['-]?""")

/** Accept unfinished final apostrophes/hyphens while typing; never accept SQL wildcards. */
internal fun normalizeDictionaryPrefix(input: String): String? {
    val key = normalizeWord(input.trim()).replace(dictionaryPrefixSpaces, " ")
    return key.takeIf { it.length in 1..80 && it.count { c -> c == ' ' } < 8 && dictionaryPrefixPattern.matches(it) }
}

internal fun mergeDictionarySearch(
    prefix: String,
    indexed: List<Pair<String, WordMeaning>>,
    personal: Map<String, WordMeaning>,
    limit: Int = dictionarySearchLimit,
    findAlternate: (String) -> WordMeaning? = { null }
): DictionarySearchResult {
    require(limit > 0)
    val merged = mutableMapOf<String, DictionarySearchEntry>()
    for ((term, meaning) in indexed) {
        if (!term.startsWith(prefix)) continue
        val resolved = immediateDictionaryMeaning(term, personal) ?: DictionaryResult(meaning, "Offline dictionary")
        merged[term] = DictionarySearchEntry(term, resolved.meaning!!, resolved.source)
    }
    for ((term, meaning) in commonWordMeanings) {
        if (term.startsWith(prefix)) merged[term] = DictionarySearchEntry(term,
            personal[term] ?: WordMeaning(term, meaning), if (term in personal) "Personal dictionary" else "Offline dictionary")
    }
    for ((term, meaning) in dialogueDictionary) {
        if (term.startsWith(prefix) && term !in merged) {
            merged[term] = DictionarySearchEntry(term, personal[term] ?: meaning,
                if (term in personal) "Personal dictionary" else "Offline dictionary")
        }
    }
    immediateDictionaryMeaning(prefix, personal)?.let {
        merged[prefix] = DictionarySearchEntry(prefix, it.meaning!!, it.source)
    }
    personal.entries.asSequence().filter { it.key.startsWith(prefix) }.sortedBy { it.key }.take(limit + 1).forEach {
        merged[it.key] = DictionarySearchEntry(it.key, it.value, "Personal dictionary")
    }
    if (prefix !in merged) {
        dictionarySpellingVariant(prefix)?.let(findAlternate)?.let { meaning ->
            merged[prefix] = DictionarySearchEntry(prefix, meaning, "Spelling variant · Offline")
        }
    }
    val sorted = merged.values.sortedBy { it.term }
    return DictionarySearchResult(sorted.take(limit), sorted.size > limit)
}
