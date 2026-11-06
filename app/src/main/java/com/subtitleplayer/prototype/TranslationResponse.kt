package com.subtitleplayer.prototype

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal const val maxTranslationResponseBytes = 65536

/** Check cancellation before each read and reject oversized bodies without loading them fully. */
internal fun readTranslationResponse(input: InputStream, checkCancelled: () -> Unit): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (output.size() <= maxTranslationResponseBytes) {
        checkCancelled()
        val n = input.read(buffer, 0, minOf(buffer.size, maxTranslationResponseBytes + 1 - output.size()))
        if (n < 0) break
        output.write(buffer, 0, n)
    }
    checkCancelled()
    if (output.size() > maxTranslationResponseBytes) throw OnlineLookupException(LookupFailure.InvalidResponse)
    return output.toByteArray()
}

internal fun translationStatusFailure(status: Int, quotaFinished: Boolean = false): LookupFailure? = when {
    quotaFinished || status == 429 -> LookupFailure.Quota
    status != 200 -> LookupFailure.Service
    else -> null
}
