package com.subtitleplayer.prototype

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

internal enum class SubtitleTextEncoding(val label: String, val charsetName: String?) {
    Auto("خودکار", null), Utf8("UTF-8", "UTF-8"),
    Windows1256("Windows-1256", "windows-1256"), Windows1252("Windows-1252", "windows-1252")
}

internal const val maxSubtitleBytes = 8 * 1024 * 1024

internal fun readSubtitleBytes(stream: InputStream, limit: Int = maxSubtitleBytes): ByteArray {
    require(limit > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        if (count > limit - output.size()) throw IOException("Subtitle exceeds size limit")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun decodeSubtitleBytes(
    bytes: ByteArray, persian: Boolean, encoding: SubtitleTextEncoding = SubtitleTextEncoding.Auto
): String {
    fun prefix(vararg values: Int) = bytes.size >= values.size &&
        values.indices.all { bytes[it].toInt() and 255 == values[it] }
    fun decode(charset: Charset, offset: Int = 0): String = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset)).toString()

    if (prefix(0xff, 0xfe, 0, 0) || prefix(0, 0, 0xfe, 0xff)) throw IOException("UTF-32 is unsupported")
    if (prefix(0xef, 0xbb, 0xbf)) return decode(Charsets.UTF_8, 3)
    if (prefix(0xff, 0xfe)) return decode(Charsets.UTF_16LE, 2)
    if (prefix(0xfe, 0xff)) return decode(Charsets.UTF_16BE, 2)
    encoding.charsetName?.let {
        if (!Charset.isSupported(it)) throw IOException("Selected encoding unavailable")
        return decode(Charset.forName(it))
    }
    return try {
        decode(Charsets.UTF_8)
    } catch (_: CharacterCodingException) {
        val fallback = if (persian) "windows-1256" else "windows-1252"
        if (!Charset.isSupported(fallback)) throw IOException("Legacy encoding unavailable")
        decode(Charset.forName(fallback))
    }
}
