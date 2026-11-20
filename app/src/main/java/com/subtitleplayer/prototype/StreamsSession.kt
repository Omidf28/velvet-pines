package com.subtitleplayer.prototype

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Session-only URLs survive Activity recreation without going into preferences or saved-state files. */
internal class StreamsSession : ViewModel() {
    val browse = StreamsBrowseState()
    var incoming by mutableStateOf<SharedStreamInput?>(null)
}

internal fun sharedStreamInput(intent: Intent): SharedStreamInput? {
    if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return null
    val text = try {
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.let {
            // Bound conversion even when the sender supplies a styled CharSequence.
            if (it.length > sharedStreamTextLimit) return SharedStreamInput.Invalid("The shared text is too long. Share just one URL.")
            it.toString()
        }
    } catch (_: RuntimeException) { return SharedStreamInput.Invalid("Could not read the shared text. Share one URL again.") }
    return parseSharedStreamText(text)
}
