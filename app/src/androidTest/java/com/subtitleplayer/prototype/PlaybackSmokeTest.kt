package com.subtitleplayer.prototype

import android.content.ContentValues
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Rect
import android.provider.MediaStore
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.net.URI

class PlaybackSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun streamsTabChecksUrlsAndReturnsToFiles() {
        compose.onNodeWithText("Streams").performClick()
        compose.onNodeWithText("Video or index URL").performTextInput("http://media.example/episode.mp4")
        compose.onNodeWithText("Play URL").performClick()
        compose.onNodeWithText("Enter a direct HTTPS or RTSP video URL.").assertIsDisplayed()
        compose.onNodeWithText("Files").performClick()
        compose.onNodeWithContentDescription("Files menu").assertIsDisplayed()
    }

    @Test
    fun anUnreachableSavedIndexOffersRetryAndLocalFiles() {
        compose.onNodeWithText("Streams").performClick()
        compose.onNodeWithText("Video or index URL").performTextInput("https://media.invalid/index.html")
        compose.onNodeWithText("Save source").performClick()
        compose.onNodeWithText("Files").performClick()
        compose.onNodeWithText("Streams").performClick()
        compose.onNode(hasScrollToIndexAction() and hasAnyDescendant(hasText("Save source")))
            .performScrollToNode(hasText("media.invalid"))
        compose.onNodeWithText("media.invalid").performClick()
        waitForNode(hasText("Try again"))
        compose.onNodeWithText("Try again").performClick()
        waitForNode(hasText("Open Files"))
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithText("Open Files").performClick()
        compose.onNodeWithContentDescription("Files menu").assertIsDisplayed()
    }

    @Test
    fun readsPlayableEpisodesFromAnHtmlIndex() {
        val links = extractStreamIndexEntries(
            URI("https://media.example/series/index.html"),
            """
            <a href="season-1/episode-2.mp4">Episode <strong>2</strong></a>
            <a href="season-1/episode-2.mp4#preview">Duplicate</a>
            <a href="season-1/episode-2.srt">Subtitle</a>
            <a href="season-2.html">Season 2</a>
            <a href="https://cdn.example/season-1/episode-3.m3u8">Episode 3</a>
            """.trimIndent()
        )
        assertEquals(listOf("Episode 2", "Season 2", "Episode 3"), links.map { it.title })
        assertEquals("https://media.example/series/season-1/episode-2.mp4", links.first().url)
        assertEquals(URI("https://media.example/series/season-2.html"), (links[1] as StreamIndexEntry.Page).address)
        assertEquals(OnlineVideoFormat.Hls, (links.last() as StreamIndexEntry.Video).link.format)
    }

    @Test
    fun searchesDonyayeSerialAndOpensMovieAndSeasonOptions() {
        val address = URI("https://dls6.aparatchi-dlcenter.top/DonyayeSerial/10_thous.html")
        val script = """
            let data = [
                ['tt1000001', 'movie', 'Pine\'s Trail', 'مسیر کاج', 2024, 9, 100,
                    {'1': [
                        {'file': 'https://media.example/trail.mkv', 'quality': '1080p', 'version': 'SoftSub'},
                        {'file': 'http://media.example/trail.mp4', 'quality': '720p'}
                    ], '3': {'1': [{'file': 'https://media.example/trail-original.mkv'}]}}, []],
                ['tt1000002', 'series', 'Forest Tales', 'داستان‌های جنگل', 2025, 8, 200,
                    {'1': {'2': [
                        {'folder': 'https://media.example/forest/S02/1080p.Web-DL',
                         'quality': '1080p.Web-DL', 'number_of_episodes': 8}
                    ]}, '2': [{'file': 'https://media.example/forest-special.mkv'}]}, []]
            ]
            throw new Error('The script body must not run')
        """.trimIndent()
        val titles = parseDonyayeSerialCatalog(address, script.reader())
        assertEquals(listOf("Pine's Trail 2024", "Forest Tales 2025"), titles.map { it.title })
        val movie = titles.first().entries.first() as StreamIndexEntry.Video
        assertEquals(2, titles.first().entries.size)
        assertEquals("https://media.example/trail.mkv", movie.url)
        assertEquals("1080p · SoftSub", movie.title)
        val season = titles.last().entries.first() as StreamIndexEntry.Page
        assertEquals("https://media.example/forest-special.mkv", titles.last().entries.last().url)
        assertEquals("https://media.example/forest/S02/1080p.Web-DL", season.url)
        assertEquals(true, season.directory)
        assertEquals("Season 2 · 1080p.Web-DL · 8 episodes", season.title)
        val state = StreamsBrowseState()
        compose.runOnIdle {
            state.openArchive(StreamIndexEntry.CatalogPage("Donyaye Serial", "", address.toString(), titles))
            state.query = "مسير كاج ۲۰۲۴"
            assertEquals(true, state.archiveSearchPending)
        }
        runBlocking { withContext(Dispatchers.Main) { state.searchCurrentArchive() } }
        compose.runOnIdle {
            assertEquals(listOf(titles.first()), state.results.map { it.entry })
            state.query = "forest tales"
            assertEquals(emptyList<StreamSearchResult>(), state.results)
        }
        runBlocking { withContext(Dispatchers.Main) { state.searchCurrentArchive() } }
        compose.runOnIdle {
            assertEquals(listOf(titles.last()), state.results.map { it.entry })
            state.openPage(titles.last(), titles.last().entries)
            assertEquals(titles.last().entries.toSet(), state.results.map { it.entry }.toSet())
            val episodes = extractStreamIndexEntries(
                URI(season.url + "/"),
                """<a href="EP10.mkv">EP…</a><a href="EP2.mkv">EP…</a>""",
                directory = true
            )
            state.openPage(season, episodes)
            assertEquals(listOf("Episode 2", "Episode 10"), state.results.map { it.entry.title })
            state.query = "فصل ۲ قسمت ۱۰"
            assertEquals(listOf("Episode 10"), state.results.map { it.entry.title })
            state.back()
            state.back()
            assertEquals("forest tales", state.query)
            assertFalse(state.archiveSearchPending)
            assertEquals(listOf(titles.last()), state.results.map { it.entry })
        }
    }

    @Test(expected = IOException::class)
    fun rejectsAnArchiveWithAnUnexpectedDataFormat() {
        val address = URI("https://dls6.aparatchi-dlcenter.top/DonyayeSerial/10_thous.html")
        parseDonyayeSerialCatalog(address, "let data = {};".reader())
    }

    @Test
    fun returningFromASeasonRestoresTheParentSearch() {
        val archive = URI("https://media.example/archive.html")
        val rootEntries = extractStreamIndexEntries(archive, """
            <a href="season-1.html">Season 1</a>
            <a href="season-2.html">Season 2</a>
        """.trimIndent())
        val season = rootEntries.first() as StreamIndexEntry.Page
        val nextSeason = rootEntries.last() as StreamIndexEntry.Page
        val seasonEntries = extractStreamIndexEntries(season.address, """<a href="s01e02.mp4">Episode 2</a>""")
        val nextSeasonEntries = extractStreamIndexEntries(nextSeason.address, """<a href="s02e02.mp4">Episode 2</a>""")
        compose.runOnIdle {
            val state = StreamsBrowseState()
            state.addCatalog(archive.toString(), rootEntries)
            state.query = "Season 1"
            state.openPage(season, seasonEntries)
            assertEquals(season, state.currentPage)
            assertEquals(seasonEntries, state.entries)
            assertEquals("", state.query)
            assertFalse(state.canOpen(season))
            state.query = "Episode 2"
            state.back()
            assertNull(state.currentPage)
            assertEquals(rootEntries, state.entries)
            assertEquals("Season 1", state.query)
            state.openPage(nextSeason, nextSeasonEntries)
            state.query = "Episode 2"
            assertEquals(nextSeasonEntries, state.results.map { it.entry })
            state.back()
            state.query = "Episode 2"
            assertEquals(listOf(seasonEntries.single(), nextSeasonEntries.single()), state.results.map { it.entry })
            state.query = "Season 2 Episode 2"
            assertEquals(listOf(StreamSearchResult(nextSeasonEntries.single(), "Season 2")), state.results)
            state.query = ""
            assertEquals(rootEntries, state.results.map { it.entry })
        }
    }

    @Test
    fun opensFilesAndSavesASubtitleWordToFlashcards() {
        openSample()

        selectSampleWord()
        compose.onNodeWithContentDescription("Word options").performClick()
        compose.onNodeWithText("Save").performClick()
        waitForNode(hasText("Saved"))

        repeat(2) {
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
        }
        compose.onNodeWithContentDescription("Files menu").assertIsDisplayed()
        compose.onNodeWithText("Flashcards").performClick()
        waitForNode(hasText(sampleWord))
        compose.onNodeWithText(sampleWord).performClick()
        compose.onNodeWithText(sampleMeaning).assertIsDisplayed()
    }

    @Test
    fun fullscreenControlsAndTimedDefinitionKeepVideoBounds() {
        openSample()
        waitForNode(hasContentDescription("Fullscreen"))
        compose.onNodeWithContentDescription("Fullscreen").performClick()
        compose.waitUntil(15_000) {
            var landscape = false
            compose.activityRule.scenario.onActivity {
                landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            }
            landscape
        }
        waitForNode(hasText(sampleCue))
        if (!compose.onNodeWithContentDescription("Pause").isDisplayed()) tapVideo()
        waitForNode(hasContentDescription("Pause"))
        compose.onNodeWithContentDescription("Pause").performClick()

        val original = videoBounds()
        tapVideo()
        waitForNode(hasContentDescription("Exit fullscreen"), present = false)
        assertEquals(original, videoBounds())
        tapVideo()
        waitForNode(hasContentDescription("Exit fullscreen"))
        assertEquals(original, videoBounds())

        selectSampleWord()
        compose.onNodeWithText(sampleMeaning).assertIsDisplayed()
        assertEquals(original, videoBounds())
        waitForNode(hasText(sampleMeaning), present = false)
        assertEquals(original, videoBounds())
    }

    private fun openSample() {
        compose.onNodeWithContentDescription("Files menu").assertIsDisplayed()
        compose.onNodeWithText("Play sample").performClick()
    }

    private fun selectSampleWord() {
        waitForNode(hasText(sampleCue))
        compose.onNodeWithText(sampleCue).performFirstLinkClick { it.start == sampleCue.indexOf(sampleWord) }
        waitForNode(hasText(sampleMeaning))
    }

    private fun tapVideo() {
        val playbackArea = SemanticsMatcher("Playback gesture area") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == "Show or hide playback controls"
        }
        compose.onNode(playbackArea).performTouchInput {
            click(Offset(width * 0.15f, height * 0.45f))
        }
    }

    private fun videoBounds(): Rect {
        val bounds = Rect()
        compose.activityRule.scenario.onActivity {
            val video = it.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
            check(video.getGlobalVisibleRect(bounds)) { "The video frame is not visible" }
        }
        return bounds
    }

    private fun waitForNode(matcher: SemanticsMatcher, present: Boolean = true) {
        compose.waitUntil(15_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() == present
        }
    }
}

@SdkSuppress(minSdkVersion = 29)
class EmbeddedSubtitlePlaybackTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun opensLocalMkvAndLooksUpAnEmbeddedEnglishWord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val videos = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val mime = "video/x-matroska"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "velvet-pines-embedded.mkv")
            put(MediaStore.Video.Media.MIME_TYPE, mime)
        }
        val video = checkNotNull(resolver.insert(videos, values))
        try {
            instrumentation.context.assets.open("demo-embedded.mkv").use { fixture ->
                checkNotNull(resolver.openOutputStream(video)).use { fixture.copyTo(it) }
            }

            val openVideo = Intent(Intent.ACTION_VIEW)
                .setClass(context, MainActivity::class.java)
                .setDataAndType(video, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ActivityScenario.launch<MainActivity>(openVideo).use {
                compose.waitUntil(15_000) {
                    compose.onAllNodes(hasText(sampleCue)).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(sampleCue).performFirstLinkClick { it.start == sampleCue.indexOf(sampleWord) }
                compose.waitUntil(15_000) {
                    compose.onAllNodes(hasText(sampleMeaning)).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(sampleMeaning).assertIsDisplayed()
            }
        } finally {
            resolver.delete(video, null, null)
        }
    }
}

private const val sampleCue = "I can't afford to lose this job."
private const val sampleWord = "afford"
private const val sampleMeaning = "از عهدهٔ چیزی برآمدن"
