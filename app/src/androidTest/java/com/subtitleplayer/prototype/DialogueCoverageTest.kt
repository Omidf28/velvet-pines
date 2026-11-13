package com.subtitleplayer.prototype

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueCoverageTest {
    @Test
    fun measuresOfflineCoverageOnFixedDialogue() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = instrumentation.context.assets.open("dialogue-coverage.en.srt")
            .bufferedReader().use { it.readText() }
        val cues = parseSrt(fixture)
        assertEquals(28, cues.size)

        val words = cues.flatMap { cue ->
            subtitleTerms(cue.text).map { normalizeWord(it.text) }
        }
        val frequency = words.groupingBy { it }.eachCount()
        val dictionary = PackagedDictionary(instrumentation.targetContext)
        val wordResults = frequency.keys.associateWith { dictionary.resolve(it, emptyMap()) }
        val missingWords = frequency.entries.filter { wordResults.getValue(it.key).meaning == null }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        val possibleBases = wordResults.count { (_, result) -> result.source == "Possible base form · Offline" }

        val phrases = listOf(
            "my bad", "call it a day", "no biggie", "long story short", "out of the loop",
            "cut to the chase", "walk out on", "caught off guard", "brought it up", "spoiler alert",
            "plot twist", "take a breath", "start over", "putting it off", "read the room", "spill the tea",
            "on the house", "take a rain check", "get the hang of it", "run it by me"
        )
        val dialogue = cues.joinToString("\n") { normalizeWord(it.text) }
        assertTrue(phrases.all { it in dialogue })
        val missingPhrases = phrases.filter { dictionary.resolve(it, emptyMap()).meaning == null }

        Log.i("DialogueCoverage", "Unique words: ${frequency.size - missingWords.size}/${frequency.size}, " +
            "word occurrences: ${words.size - missingWords.sumOf { it.value }}/${words.size}, " +
            "possible base forms: $possibleBases, phrases: ${phrases.size - missingPhrases.size}/${phrases.size}")
        Log.i("DialogueCoverage", "Missing words: $missingWords; missing phrases: $missingPhrases")

        assertTrue("Previously fixed dialogue words must stay available",
            listOf("overslept", "overheard", "withdrew", "hangry", "screenshot", "binge-watch",
                "y'all", "gonna", "gotta", "wanna", "kinda", "sorta", "lemme", "dunno",
                "could've", "would've", "might've", "must've", "couldn't've", "shouldn't've", "we'd've")
                .all { wordResults.getValue(it).meaning != null })
        assertTrue("Selected dialogue phrases without definitions: $missingPhrases", missingPhrases.isEmpty())
    }
}
