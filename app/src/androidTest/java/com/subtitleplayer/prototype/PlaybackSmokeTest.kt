package com.subtitleplayer.prototype

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import org.junit.Rule
import org.junit.Test

class PlaybackSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensFilesAndSavesASubtitleWordToFlashcards() {
        val cue = "I can't afford to lose this job."
        val word = "afford"
        val meaning = "از عهدهٔ چیزی برآمدن"
        compose.onNodeWithContentDescription("Files menu").assertIsDisplayed()
        compose.onNodeWithText("Play sample").performClick()

        waitForText(cue)
        compose.onNodeWithText(cue).performFirstLinkClick { it.start == cue.indexOf(word) }
        waitForText(meaning)
        compose.onNodeWithContentDescription("Word options").performClick()
        compose.onNodeWithText("Save").performClick()
        waitForText("Saved")

        repeat(2) {
            compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
        }
        compose.onNodeWithContentDescription("Files menu").assertIsDisplayed()
        compose.onNodeWithText("Flashcards").performClick()
        waitForText(word)
        compose.onNodeWithText(word).performClick()
        compose.onNodeWithText(meaning).assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
