package com.subtitleplayer.prototype

private val dictionaryTermSpace = Regex("[ \\t\\u00A0]+")
private val dictionaryTermPattern = Regex("""[A-Za-z]+(?:['’\-][A-Za-z]+)*(?:[ \t\u00A0]+[A-Za-z]+(?:['’\-][A-Za-z]+)*)*""")

/** An exact surface-form key: no automatic stemming or guessed translations. */
internal fun normalizeDictionaryTerm(input: String): String? {
    val value = input.trim()
    if (value.length !in 1..80 || !dictionaryTermPattern.matches(value)) return null
    val words = value.split(dictionaryTermSpace)
    if (words.size > 8) return null
    return words.joinToString(" ", transform = ::normalizeWord)
}

internal fun validPersonalMeaning(input: String): String? {
    val text = input.trim()
    if (text.length !in 1..1_000 || text.any { it.isISOControl() && it != '\n' && it != '\t' }) return null
    return text
}
