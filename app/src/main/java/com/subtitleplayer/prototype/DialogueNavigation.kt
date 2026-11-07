package com.subtitleplayer.prototype

/** First start after the supplied position in a list sorted by start time. */
internal fun dialogueUpperBound(dialogues: List<DialogueRange>, positionMs: Long): Int {
    var low = 0
    var high = dialogues.size
    while (low < high) {
        val middle = low + (high - low) / 2
        if (dialogues[middle].startMs <= positionMs) low = middle + 1 else high = middle
    }
    return low
}

internal fun adjacentDialogue(
    dialogues: List<DialogueRange>, positionMs: Long, forward: Boolean
): DialogueRange? {
    val index = if (forward) dialogueUpperBound(dialogues, positionMs)
    // Previous uses a strict 250 ms cutoff, so an exact boundary is excluded.
    else dialogueUpperBound(dialogues, positionMs.coerceAtLeast(0) - 250 - 1) - 1
    return dialogues.getOrNull(index)
}
