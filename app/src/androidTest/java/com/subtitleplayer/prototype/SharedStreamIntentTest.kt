package com.subtitleplayer.prototype

import android.content.Intent
import android.text.SpannableString
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.net.URI
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SharedStreamIntentTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun sharesheetLaunchPreparesEditableUrlAndRecreationDoesNotReplayTheOriginalShare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val url = "https://media.example/video.m3u8?signature=share-session-only"
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .putExtra(Intent.EXTRA_TEXT, SpannableString("Watch this episode\n$url"))
        assertTrue(context.packageManager.queryIntentActivities(intent, 0).any {
            it.activityInfo.name == MainActivity::class.java.name
        })
        val sourcePrefs = context.getSharedPreferences("stream_sources", android.content.Context.MODE_PRIVATE)
        val previousSources = sourcePrefs.getString("indexes", "")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            waitForText(url)
            compose.onNodeWithText(url).assertIsDisplayed()
            compose.onNodeWithText("Shared link ready. Choose Play URL for a video or Load index for an archive.").assertIsDisplayed()
            val edited = "https://media.example/edited.mp4?signature=edited-session-only"
            compose.onNode(hasSetTextAction()).performTextReplacement(edited)
            scenario.recreate()
            waitForText(edited)
            compose.onNodeWithText(edited).assertIsDisplayed()
            scenario.onActivity { activity ->
                activity.startActivity(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .setClass(activity, MainActivity::class.java).setPackage(context.packageName)
                    .addCategory(Intent.CATEGORY_DEFAULT).putExtra(Intent.EXTRA_TEXT, "No URL here"))
            }
            waitForText("No supported link found. Share an HTTPS or RTSP URL.")
            compose.onNodeWithText(edited).assertIsDisplayed()
            val archive = "https://media.example/season/index.html"
            scenario.onActivity { activity ->
                activity.startActivity(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .setClass(activity, MainActivity::class.java).setPackage(context.packageName)
                    .addCategory(Intent.CATEGORY_DEFAULT).putExtra(Intent.EXTRA_TEXT, archive))
            }
            waitForText(archive)
            compose.onNodeWithText(archive).assertIsDisplayed()
            assertEquals(previousSources, sourcePrefs.getString("indexes", ""))
        }
    }

    @Test fun sharedInputReturnsFromNestedIndexAndKeepsDraftOnInvalidInput() {
        val state = StreamsBrowseState()
        val index = URI("https://media.example/season/")
        state.openPage(StreamIndexEntry.Page("Season", "season", index, directory = true), emptyList())
        state.query = "Episode 2"
        state.reportUnavailable("Old error", StreamIndexRequest(index))
        state.acceptSharedInput(SharedStreamInput.Ready("rtsp://media.example/live"))
        assertNull(state.currentPage)
        assertNull(state.failedRequest)
        assertEquals("", state.query)
        assertEquals("rtsp://media.example/live", state.address)
        assertEquals(1, state.inputRevision)
        state.acceptSharedInput(SharedStreamInput.Invalid("Choose one link"))
        assertEquals("rtsp://media.example/live", state.address)
        assertEquals("Choose one link", state.message)
        assertEquals(2, state.inputRevision)
    }

    @Test fun intentAdapterRejectsUnrelatedActionsTypesAndOversizedStyledText() {
        val url = "https://media.example/video.mp4"
        assertNull(sharedStreamInput(Intent(Intent.ACTION_VIEW).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)))
        assertNull(sharedStreamInput(Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_TEXT, url)))
        assertNull(sharedStreamInput(Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)))
        assertTrue(sharedStreamInput(Intent(Intent.ACTION_SEND).setType("text/plain")) is SharedStreamInput.Invalid)
        assertTrue(sharedStreamInput(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,
            SpannableString("x".repeat(sharedStreamTextLimit + 1)))) is SharedStreamInput.Invalid)
    }

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }
}
