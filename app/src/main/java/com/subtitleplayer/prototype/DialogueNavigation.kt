package com.subtitleplayer.prototype

internal fun adjacentDialogue(
    dialogues: List<DialogueRange>, positionMs: Long, forward: Boolean
): DialogueRange? {
    return if (forward) dialogues.firstOrNull { it.startMs > positionMs }
    else dialogues.lastOrNull { it.startMs < positionMs.coerceAtLeast(0) - 250 }
}
