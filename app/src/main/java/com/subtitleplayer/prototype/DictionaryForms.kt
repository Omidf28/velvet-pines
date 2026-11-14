package com.subtitleplayer.prototype

private val irregularBaseForms = mapOf(
    "overheard" to "overhear", "misheard" to "mishear", "outgrew" to "outgrow", "outgrown" to "outgrow",
    "withheld" to "withhold", "overslept" to "oversleep", "rethought" to "rethink",
    "overpaid" to "overpay", "withdrew" to "withdraw", "withdrawn" to "withdraw", "leapt" to "leap",
    "went" to "go", "gone" to "go", "was" to "be", "were" to "be",
    "been" to "be", "am" to "be", "is" to "be", "are" to "be",
    "did" to "do", "done" to "do", "had" to "have", "has" to "have",
    "saw" to "see", "seen" to "see", "took" to "take", "taken" to "take",
    "came" to "come", "got" to "get", "gotten" to "get", "said" to "say",
    "thought" to "think", "bought" to "buy", "brought" to "bring",
    "knew" to "know", "known" to "know", "felt" to "feel", "found" to "find",
    "made" to "make", "told" to "tell", "left" to "leave", "gave" to "give",
    "given" to "give", "children" to "child", "men" to "man", "women" to "woman",
    "feet" to "foot", "teeth" to "tooth", "mice" to "mouse"
)

private val verbsWithInflectedCk = setOf("panic", "mimic", "picnic", "traffic", "frolic")
private val inflectionSuffixes = listOf("ing", "ed", "er", "est")

private val americanSpellingBases = setOf(
    "organize", "recognize", "realize", "normalize", "prioritize", "standardize",
    "customize", "personalize", "visualize", "specialize", "minimize", "maximize",
    "initialize", "legalize", "memorize", "randomize"
)
private val spellingVariants = buildMap {
    for (american in americanSpellingBases) {
        val british = american.removeSuffix("ize") + "ise"
        for (suffix in listOf("", "s", "d", "ing")) {
            val left = (if (suffix == "ing") british.dropLast(1) else british) + suffix
            val right = (if (suffix == "ing") american.dropLast(1) else american) + suffix
            put(left, right)
            put(right, left)
        }
    }
}

/** Conservative candidates; every proposed base still requires a dictionary hit. */
internal fun dictionaryBaseForms(term: String): List<String> {
    val key = normalizeDictionaryTerm(term) ?: return emptyList()
    if (' ' in key) return emptyList()
    irregularBaseForms[key]?.let { return listOf(it) }
    return buildList {
        if (key.endsWith("'s") && key.length > 3) add(key.dropLast(2))
        if (key.endsWith("ies") && key.length > 4) add(key.dropLast(3) + "y")
        if (key.endsWith("s") && !key.endsWith("ss") && key.length > 3) add(key.dropLast(1))
        if (key.endsWith("es") && key.length > 3) add(key.dropLast(2))
        for (suffix in inflectionSuffixes) {
            if (key.endsWith(suffix) && key.length > suffix.length + 2) {
                val stem = key.dropLast(suffix.length)
                if ((suffix == "ing" || suffix == "ed") && stem.endsWith("ck")) {
                    val base = stem.dropLast(1)
                    if (base in verbsWithInflectedCk) add(base)
                }
                if (suffix == "ing" && stem.endsWith("y")) add(stem.dropLast(1) + "ie")
                if (stem.endsWith("i")) add(stem.dropLast(1) + "y")
                add(stem)
                add(stem + "e")
                if (stem.length > 2 && stem.last() == stem[stem.lastIndex - 1] && stem.last() !in "aeiou") add(stem.dropLast(1))
            }
        }
    }.distinct()
}

internal val commonWordMeanings = mapOf(
    "a" to "یک؛ حرف تعریف نامعین", "an" to "یک؛ حرف تعریف نامعین",
    "the" to "حرف تعریف معین؛ اشاره به چیز مشخص", "i" to "من", "you" to "تو؛ شما",
    "he" to "او (مذکر)", "she" to "او (مؤنث)", "it" to "آن", "we" to "ما", "they" to "آن‌ها",
    "am" to "هستم", "is" to "است", "are" to "هستی؛ هستید؛ هستند", "was" to "بود؛ بودم", "were" to "بودی؛ بودید؛ بودند",
    "of" to "از؛ نشان‌دهندهٔ تعلق", "to" to "به؛ برای؛ نشانهٔ مصدر", "and" to "و", "or" to "یا",
    "not" to "نه؛ نشان‌دهندهٔ نفی", "don't" to "نمی‌کنم؛ نمی‌کنی؛ نکن (بسته به جمله)",
    "doesn't" to "نمی‌کند", "didn't" to "نکرد؛ نکردم", "can't" to "نمی‌توانم؛ نمی‌تواند",
    "won't" to "نخواهد؛ نخواهم", "isn't" to "نیست", "aren't" to "نیستی؛ نیستند", "it's" to "آن است؛ آن دارد (بسته به جمله)",
    "i'm" to "من هستم", "you're" to "تو هستی؛ شما هستید", "we're" to "ما هستیم", "they're" to "آن‌ها هستند"
)

internal data class DictionaryResult(val meaning: WordMeaning?, val source: String, val failure: LookupFailure? = null)

internal fun immediateDictionaryMeaning(key: String, personal: Map<String, WordMeaning>): DictionaryResult? {
    personal[key]?.let { return DictionaryResult(it, "Personal dictionary") }
    commonWordMeanings[key]?.let { return DictionaryResult(WordMeaning(key, it), "Offline dictionary") }
    defaultTermMeaning(key)?.let { return DictionaryResult(it, "Offline dictionary") }
    return null
}

internal fun indexedDictionaryMeaning(
    key: String,
    exactKnownMissing: Boolean = false,
    find: (String) -> WordMeaning?
): DictionaryResult {
    if (!exactKnownMissing) find(key)?.let { return DictionaryResult(it, "Offline dictionary") }
    val variant = spellingVariants[key]
    variant?.let(find)?.let { return DictionaryResult(it, "Spelling variant · Offline") }
    val candidates = dictionaryBaseForms(key).asSequence().mapNotNull { base ->
        dialogueDictionary[base] ?: find(base) ?: spellingVariants[base]?.let(find)
    }.distinctBy { it.headword }.take(3).toList()
    if (candidates.isNotEmpty()) {
        val definition = candidates.joinToString("\n") { "${it.headword}: ${it.persian}" }.take(1000)
        return DictionaryResult(WordMeaning(key, definition), "Possible base form · Offline")
    }
    return DictionaryResult(null, "No match in the offline dictionary")
}

internal fun validOnlineDefinition(value: String): Boolean =
    value.length in 1..1000 && value.any { it in '\u0600'..'\u06ff' } &&
        '<' !in value && value.none { it.isISOControl() && it != '\n' && it != '\t' }
