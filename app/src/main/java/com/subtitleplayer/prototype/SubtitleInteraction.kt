package com.subtitleplayer.prototype

internal enum class SubtitleLookupMode(val label: String) {
    Words("Words"), Phrases("Known phrases")
}

internal fun subtitleLookupModeFrom(value: String?): SubtitleLookupMode =
    SubtitleLookupMode.entries.firstOrNull { it.name == value } ?: SubtitleLookupMode.Words

/** A selection belongs to one displayed cue, not every occurrence of the same word. */
internal data class SubtitleSelection(
    val subtitle: ActiveSubtitle,
    val start: Int,
    val endExclusive: Int
) {
    fun highlightFor(current: ActiveSubtitle): IntRange? {
        if (current != subtitle || start < 0 || endExclusive <= start || endExclusive > current.text.length) return null
        return start until endExclusive
    }
}

/** Every adjacent phrase containing the full selection, without crossing cue line or punctuation boundaries. */
internal fun subtitlePhraseCandidates(selection: SubtitleSelection): List<SubtitleTerm> {
    val text = selection.subtitle.text
    val words = subtitleWords.findAll(text).toList()
    val selectedStart = words.indexOfFirst { it.range.first == selection.start }
    val selectedEnd = words.indexOfFirst { it.range.last + 1 == selection.endExclusive }
    if (selectedStart < 0 || selectedEnd < selectedStart || selectedEnd - selectedStart >= 8) return emptyList()

    val normalized = words.map { normalizeWord(it.value) }
    return buildList {
        for (size in 8 downTo maxOf(2, selectedEnd - selectedStart + 1)) {
            val firstStart = maxOf(0, selectedEnd - size + 1)
            val lastStart = minOf(selectedStart, words.size - size)
            for (start in firstStart..lastStart) {
                val from = words[start].range.first
                val to = words[start + size - 1].range.last + 1
                if (from == selection.start && to == selection.endExclusive) continue
                val phrase = text.substring(from, to)
                if (normalizeDictionaryTerm(phrase) != null) {
                    val label = words.subList(start, start + size).joinToString(" ") { it.value }
                    val lookupText = if (size in 3..6 && normalizeDictionaryTerm(label) !in dialogueDictionary) {
                        splitPhrasalVerb(normalized[start], normalized[start + size - 1],
                            normalized.subList(start + 1, start + size - 1))
                    } else null
                    add(SubtitleTerm(from, to, label, null, lookupText ?: label))
                }
            }
        }
    }
}

private val objectIntroducers = setOf("the", "a", "an", "this", "that", "these", "those", "my", "your", "his", "her", "our", "their")
private val objectPronouns = setOf("it", "them", "me", "you", "him", "her", "us")
private val clauseWords = setOf("and", "or", "but", "if", "because", "while", "when", "then", "to")

private fun splitPhrasalVerb(verb: String, particle: String, middle: List<String>): String? {
    val objectPhrase = if (middle.size == 1) middle.first() in objectPronouns
        else middle.first() in objectIntroducers && middle.none { it in clauseWords }
    if (!objectPhrase) return null
    return when (particle) {
        "up" -> when (verb) {
            "bring", "brings", "brought", "bringing" -> "bring up"
            "pick", "picks", "picked", "picking" -> "pick up"
            else -> null
        }
        "off" -> "put off".takeIf { verb in setOf("put", "puts", "putting") }
        "down" -> "turn down".takeIf { verb in setOf("turn", "turns", "turned", "turning") }
        else -> null
    }
}
