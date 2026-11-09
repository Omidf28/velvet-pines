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
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaybackSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

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
