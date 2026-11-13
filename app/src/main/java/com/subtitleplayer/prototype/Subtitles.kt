package com.subtitleplayer.prototype

import java.util.Locale

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

private val separator = Regex("\\n(?:[ \\t]*\\n)+")
private val timing = Regex("""(\d{1,3}:[0-5]\d:[0-5]\d[,.]\d{3})\s*-->\s*(\d{1,3}:[0-5]\d:[0-5]\d[,.]\d{3})(?:\s+.*)?""")
private val formatting = Regex("""</?(?:b|i|u|s|font)\b[^>]*>""", RegexOption.IGNORE_CASE)
internal val subtitleWords = Regex("""[A-Za-z]+(?:['’\-][A-Za-z]+)*""")

private val vttHeader = Regex("""WEBVTT(?:[ \t].*)?""")
private const val vttTimestamp = """(?:\d{2,}:)?[0-5]\d:[0-5]\d\.\d{3}"""
private val vttTiming = Regex("""($vttTimestamp)[ \t]+-->[ \t]+($vttTimestamp)(?:[ \t]+.*)?""")
private val vttTag = Regex("""</?(?:c|v|lang|ruby|rt|b|i|u)(?=[.\s>])[^>]*>|<$vttTimestamp>""")
private val vttEntity = Regex("&(?:amp|lt|gt|lrm|rlm|nbsp);")
private val vttEntities = mapOf(
    "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
    "&lrm;" to "\u200E", "&rlm;" to "\u200F", "&nbsp;" to "\u00A0"
)

internal fun parseSubtitleFile(source: String): List<SubtitleCue> {
    val normalized = normalizeSubtitleFile(source)
    return if (vttHeader.matches(normalized.lineSequence().first())) {
        parseWebVtt(normalized)
    } else parseSrt(normalized)
}

private fun parseWebVtt(text: String): List<SubtitleCue> =
    text.split(separator).drop(1).mapNotNull { block ->
        val lines = block.lines()
        val first = lines.first().trim()
        if (first == "STYLE" || first == "REGION" ||
            first == "NOTE" || first.startsWith("NOTE ") || first.startsWith("NOTE\t")
        ) return@mapNotNull null

        val timingIndex = if ("-->" in first) 0 else 1
        val match = lines.getOrNull(timingIndex)?.trim()?.let(vttTiming::matchEntire)
            ?: return@mapNotNull null
        val start = webVttTimestampMs(match.groupValues[1]) ?: return@mapNotNull null
        val end = webVttTimestampMs(match.groupValues[2]) ?: return@mapNotNull null
        val content = lines.drop(timingIndex + 1).joinToString("\n").replace(vttTag, "")
        val subtitle = vttEntity.replace(content) { vttEntities.getValue(it.value) }.trim()
        if (end <= start || subtitle.isBlank()) null else SubtitleCue(start, end, subtitle)
    }.sortedBy(SubtitleCue::startMs)

private fun webVttTimestampMs(time: String): Long? {
    if (time.count { it == ':' } == 1) return timestampMs("0:$time")
    val hours = time.substringBefore(':').toLongOrNull() ?: return null
    if (hours > (Long.MAX_VALUE - 4_000_000) / 3_600_000) return null
    return timestampMs(time)
}

private fun normalizeSubtitleFile(source: String): String =
    source.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

fun parseSrt(source: String): List<SubtitleCue> {
    val text = normalizeSubtitleFile(source).trim()
    return text.split(separator).mapNotNull { block ->
        val lines = block.lines()
        val timingLine = if (lines.first().trim().all(Char::isDigit)) 1 else 0
        val match = lines.getOrNull(timingLine)?.trim()?.let(timing::matchEntire)
            ?: return@mapNotNull null
        val start = timestampMs(match.groupValues[1])
        val end = timestampMs(match.groupValues[2])
        val subtitle = lines.drop(timingLine + 1).joinToString("\n").replace(formatting, "").trim()
        if (end <= start || subtitle.isBlank()) null else SubtitleCue(start, end, subtitle)
    }.sortedBy(SubtitleCue::startMs)
}

internal data class ActiveSubtitle(val text: String, val startMs: Long)

internal fun activeSubtitle(cues: List<SubtitleCue>, positionMs: Long): ActiveSubtitle? {
    val visible = cues.asSequence()
        .takeWhile { it.startMs <= positionMs }
        .filter { positionMs < it.endMs }
        .toList()
    val first = visible.firstOrNull() ?: return null
    return ActiveSubtitle(visible.joinToString("\n", transform = SubtitleCue::text), first.startMs)
}

internal data class DialogueRange(val text: String, val startMs: Long, val endMs: Long)

internal fun SubtitleCue.toDialogueRange(delayMs: Long, durationMs: Long): DialogueRange? {
    if (durationMs <= 0) return null
    val start = (startMs + delayMs).coerceIn(0L, durationMs)
    val end = (endMs + delayMs).coerceIn(0L, durationMs)
    return if (start < end) DialogueRange(text, start, end) else null
}

private fun timestampMs(time: String): Long {
    val (hours, minutes, seconds, millis) = time.split(':', ',', '.').map(String::toLong)
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}

data class WordMeaning(val headword: String, val persian: String)

private val starterDictionary = mapOf(
    "afford" to WordMeaning("afford", "از عهدهٔ چیزی برآمدن"),
    "arrived" to WordMeaning("arrive", "رسیدن"),
    "before" to WordMeaning("before", "قبل از"),
    "breath" to WordMeaning("breath", "نَفَس"),
    "can't" to WordMeaning("can", "نمی‌توانم / نمی‌تواند"),
    "decide" to WordMeaning("decide", "تصمیم گرفتن"),
    "job" to WordMeaning("job", "شغل"),
    "looking" to WordMeaning("look", "نگاه کردن / دنبال چیزی گشتن"),
    "lose" to WordMeaning("lose", "از دست دادن"),
    "shelter" to WordMeaning("shelter", "پناهگاه"),
    "still" to WordMeaning("still", "هنوز"),
    "storm" to WordMeaning("storm", "طوفان"),
    "take" to WordMeaning("take", "گرفتن"),
    "time" to WordMeaning("time", "زمان / فرصت"),
    "went" to WordMeaning("go", "رفتن"),
    "afraid" to WordMeaning("afraid", "ترسیده"),
    "again" to WordMeaning("again", "دوباره"),
    "already" to WordMeaning("already", "قبلاً / تا حالا"),
    "ask" to WordMeaning("ask", "پرسیدن / درخواست کردن"),
    "asked" to WordMeaning("ask", "پرسیدن / درخواست کردن"),
    "believe" to WordMeaning("believe", "باور کردن"),
    "bring" to WordMeaning("bring", "آوردن"),
    "brought" to WordMeaning("bring", "آوردن"),
    "came" to WordMeaning("come", "آمدن"),
    "caught" to WordMeaning("catch", "گرفتن / گیر انداختن"),
    "choice" to WordMeaning("choice", "انتخاب"),
    "come" to WordMeaning("come", "آمدن"),
    "could" to WordMeaning("can", "می‌توانست / می‌توانم"),
    "did" to WordMeaning("do", "انجام دادن"),
    "enough" to WordMeaning("enough", "کافی"),
    "felt" to WordMeaning("feel", "احساس کردن"),
    "find" to WordMeaning("find", "پیدا کردن"),
    "forgot" to WordMeaning("forget", "فراموش کردن"),
    "found" to WordMeaning("find", "پیدا کردن"),
    "gave" to WordMeaning("give", "دادن"),
    "get" to WordMeaning("get", "گرفتن / به دست آوردن"),
    "give" to WordMeaning("give", "دادن"),
    "go" to WordMeaning("go", "رفتن"),
    "got" to WordMeaning("get", "گرفتن / به دست آوردن"),
    "guess" to WordMeaning("guess", "حدس زدن"),
    "happen" to WordMeaning("happen", "اتفاق افتادن"),
    "heard" to WordMeaning("hear", "شنیدن"),
    "help" to WordMeaning("help", "کمک کردن"),
    "hope" to WordMeaning("hope", "امید داشتن"),
    "just" to WordMeaning("just", "فقط / همین الآن"),
    "keep" to WordMeaning("keep", "نگه داشتن / ادامه دادن"),
    "knew" to WordMeaning("know", "دانستن / شناختن"),
    "leave" to WordMeaning("leave", "ترک کردن / رفتن"),
    "left" to WordMeaning("leave", "ترک کرد / رفت"),
    "lost" to WordMeaning("lose", "از دست دادن / گم کردن"),
    "maybe" to WordMeaning("maybe", "شاید"),
    "mean" to WordMeaning("mean", "منظور داشتن / معنی دادن"),
    "mind" to WordMeaning("mind", "ذهن / اهمیت دادن"),
    "miss" to WordMeaning("miss", "دلتنگ شدن / از دست دادن"),
    "need" to WordMeaning("need", "نیاز داشتن"),
    "promise" to WordMeaning("promise", "قول / قول دادن"),
    "ready" to WordMeaning("ready", "آماده"),
    "remember" to WordMeaning("remember", "به یاد آوردن"),
    "right" to WordMeaning("right", "درست / راست"),
    "said" to WordMeaning("say", "گفتن"),
    "saw" to WordMeaning("see", "دیدن"),
    "seem" to WordMeaning("seem", "به نظر رسیدن"),
    "should" to WordMeaning("should", "باید / بهتر است"),
    "sure" to WordMeaning("sure", "مطمئن / البته"),
    "tell" to WordMeaning("tell", "گفتن / خبر دادن"),
    "there's" to WordMeaning("there is", "وجود دارد / هست"),
    "thought" to WordMeaning("think", "فکر کرد / فکر"),
    "told" to WordMeaning("tell", "گفتن / خبر دادن"),
    "took" to WordMeaning("take", "گرفتن / بردن"),
    "trouble" to WordMeaning("trouble", "دردسر"),
    "understand" to WordMeaning("understand", "فهمیدن"),
    "wait" to WordMeaning("wait", "صبر کردن"),
    "wrong" to WordMeaning("wrong", "اشتباه")
)

internal fun normalizeWord(word: String): String = word.lowercase(Locale.ROOT).replace('’', '\'')

fun lookupWord(word: String): WordMeaning? = starterDictionary[normalizeWord(word)]

internal data class SubtitleTerm(
    val start: Int,
    val endExclusive: Int,
    val text: String,
    val meaning: WordMeaning?
)

private val phraseDictionary = buildMap<String, WordMeaning> {
    fun phrase(headword: String, persian: String, vararg forms: String) {
        val meaning = WordMeaning(headword, persian)
        put(headword, meaning)
        forms.forEach { put(it, meaning) }
    }

    phrase("give up", "تسلیم شدن / دست کشیدن", "gave up", "given up", "gives up", "giving up")
    phrase("look for", "دنبال چیزی گشتن", "looked for", "looks for", "looking for")
    phrase("look after", "مراقبت کردن", "looked after", "looks after", "looking after")
    phrase("look forward to", "مشتاقانه منتظر بودن", "looks forward to", "looking forward to", "looked forward to")
    phrase("find out", "فهمیدن / پی بردن", "found out", "finds out", "finding out")
    phrase("figure out", "فهمیدن / راه‌حل پیدا کردن", "figured out", "figures out", "figuring out")
    phrase("run out", "تمام شدن / با شتاب بیرون رفتن", "ran out", "runs out", "running out")
    phrase("run out of", "چیزی را تمام کردن / دیگر چیزی نداشتن", "ran out of", "runs out of", "running out of")
    phrase("hang on", "یک لحظه صبر کردن", "hung on", "hanging on")
    phrase("hold on", "صبر کردن / محکم نگه داشتن", "held on", "holding on")
    phrase("calm down", "آرام شدن / آرام کردن", "calmed down", "calms down", "calming down")
    phrase("come on", "بیا / زود باش / بی‌خیال")
    phrase("pick up", "برداشتن / دنبال کسی رفتن", "picked up", "picks up", "picking up")
    phrase("show up", "از راه رسیدن / پیدا شدن", "showed up", "shows up", "showing up")
    phrase("turn down", "رد کردن / کم کردن صدا", "turned down", "turns down", "turning down")
    phrase("take a breath", "نفس کشیدن", "took a breath", "takes a breath", "taking a breath")
    phrase("take care of", "مراقبت کردن / رسیدگی کردن", "took care of", "takes care of", "taking care of")
    phrase("by the way", "راستی / ضمناً")
    phrase("never mind", "بی‌خیال / مهم نیست")
    phrase("no way", "امکان ندارد / عمراً")
}
private val maxPhraseWords = (phraseDictionary.keys + dialogueDictionary.keys).maxOf { key ->
    key.count { it == ' ' } + 1
}

internal fun defaultTermMeaning(term: String): WordMeaning? {
    val key = normalizeDictionaryTerm(term) ?: return null
    return phraseDictionary[key] ?: lookupWord(key) ?: dialogueDictionary[key]
}

internal fun subtitleTerms(
    text: String,
    personalMeanings: Map<String, WordMeaning> = emptyMap(),
    mode: SubtitleLookupMode = SubtitleLookupMode.Words
): List<SubtitleTerm> {
    val tokens = subtitleWords.findAll(text).toList()
    val normalized = tokens.map { normalizeWord(it.value) }
    return buildList {
        var first = 0
        while (first < tokens.size) {
            var last = first
            var meaning = lookupWord(tokens[first].value) ?: dialogueDictionary[normalized[first]]
            val limit = if (mode == SubtitleLookupMode.Words) first + 1
                else minOf(first + (if (personalMeanings.isEmpty()) maxPhraseWords else 8), tokens.size)
            for (next in first + 1 until limit) {
                val gap = text.substring(tokens[next - 1].range.last + 1, tokens[next].range.first)
                if (gap.any { it != ' ' && it != '\t' && it != '\u00A0' }) break
                val key = normalized.subList(first, next + 1).joinToString(" ")
                val phrase = personalMeanings[key] ?: phraseDictionary[key] ?: dialogueDictionary[key] ?: continue
                last = next
                meaning = phrase
            }

            add(
                SubtitleTerm(
                    start = tokens[first].range.first,
                    endExclusive = tokens[last].range.last + 1,
                    text = tokens.subList(first, last + 1).joinToString(" ") { it.value },
                    meaning = personalMeanings[normalized.subList(first, last + 1).joinToString(" ")] ?: meaning
                )
            )
            first = last + 1
        }
    }
}
