package com.subtitleplayer.prototype

import android.content.res.Configuration
import android.graphics.Rect
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
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
    fun returningFromASeasonRestoresTheParentSearch() {
        val archive = URI("https://media.example/archive.html")
        val rootEntries = extractStreamIndexEntries(archive, """<a href="season-1.html">Season 1</a>""")
        val season = rootEntries.single() as StreamIndexEntry.Page
        val seasonEntries = extractStreamIndexEntries(season.address, """<a href="s01e02.mp4">Episode 2</a>""")
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

    private companion object {
        const val sampleCue = "I can't afford to lose this job."
        const val sampleWord = "afford"
        const val sampleMeaning = "از عهدهٔ چیزی برآمدن"
    }
}
