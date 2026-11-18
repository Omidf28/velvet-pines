package com.subtitleplayer.prototype

private val persianWord = Regex("[\u0621-\u063a\u0641-\u064a\u0671-\u06d3\u06fa-\u06fc]+")
private val persianMarks = Regex("[\u0640\u064b-\u065f\u0670\u200c]")
private val persianSpaces = Regex("[ \t\u00a0]+")

internal fun normalizePersianSearchText(text: String): String =
    text.replace(persianMarks, "").replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')

internal fun persianSearchTokens(input: String): List<String>? {
    if (input.trim().length !in 1..80) return null
    val tokens = normalizePersianSearchText(input.trim()).split(persianSpaces)
    return tokens.takeIf { it.size in 1..8 && it.all(persianWord::matches) }
}

internal fun matchesPersianMeaning(meaning: String, tokens: List<String>): Boolean {
    val words = persianWord.findAll(normalizePersianSearchText(meaning)).map { it.value }.toList()
    return tokens.all { token -> words.any { it.startsWith(token) } }
}

internal fun dictionaryOverrideKeys(personal: Map<String, WordMeaning>): Set<String> =
    commonWordMeanings.keys + defaultDictionaryKeys() + personal.keys

internal fun persianResultOrder(tokens: List<String>) = compareBy<DictionarySearchEntry>(
    { tokens.size != 1 || it.meaning.persian.lineSequence().none { line ->
        normalizePersianSearchText(line).trim() == tokens.single()
    } },
    { ' ' in it.term }, { it.term.length }, { it.term }
)

internal fun mergePersianDictionarySearch(
    tokens: List<String>,
    indexed: List<Pair<String, WordMeaning>>,
    personal: Map<String, WordMeaning>
): DictionarySearchResult {
    val entries = mutableMapOf<String, DictionarySearchEntry>()
    for ((term, meaning) in indexed) {
        val result = immediateDictionaryMeaning(term, personal) ?: DictionaryResult(meaning, "Offline dictionary")
        val effective = result.meaning ?: continue
        if (matchesPersianMeaning(effective.persian, tokens))
            entries[term] = DictionarySearchEntry(term, effective, result.source)
    }
    for (term in dictionaryOverrideKeys(personal)) {
        val result = immediateDictionaryMeaning(term, personal) ?: continue
        val meaning = result.meaning ?: continue
        if (matchesPersianMeaning(meaning.persian, tokens))
            entries[term] = DictionarySearchEntry(term, meaning, result.source)
    }
    val sorted = entries.values.sortedWith(persianResultOrder(tokens))
    return DictionarySearchResult(sorted.take(dictionarySearchLimit), sorted.size > dictionarySearchLimit)
}
