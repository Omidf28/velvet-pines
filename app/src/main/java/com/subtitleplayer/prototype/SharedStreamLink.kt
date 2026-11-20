package com.subtitleplayer.prototype

internal const val sharedStreamTextLimit = 16 * 1024
private const val sharedStreamUrlLimit = 8192
private val sharedUrlToken = Regex("[a-z][a-z0-9+.-]*://[^\\s<>\"“”]+", RegexOption.IGNORE_CASE)

internal sealed interface SharedStreamInput {
    data class Ready(val url: String) : SharedStreamInput
    data class Invalid(val message: String) : SharedStreamInput
}

/** Only prepares a URL; receiving text never initiates a network request. */
internal fun parseSharedStreamText(text: String?): SharedStreamInput {
    fun invalid(message: String) = SharedStreamInput.Invalid(message)
    if (text == null) return invalid("No link was shared. Paste an HTTPS or RTSP URL.")
    if (text.length > sharedStreamTextLimit) return invalid("The shared text is too long. Share just one URL.")
    if (text.isBlank()) return invalid("No link was shared. Paste an HTTPS or RTSP URL.")
    fun supported(value: String): String? = value.takeIf { it.length <= sharedStreamUrlLimit }
        ?.let { parseOnlineVideoLink(it) }?.takeUnless { it.format == OnlineVideoFormat.Smb }?.url
    val trimmed = text.trim()
    supported(trimmed)?.let { return SharedStreamInput.Ready(it) }
    val tokens = sharedUrlToken.findAll(trimmed).map { match ->
        var url = match.value
        val preceding = trimmed.getOrNull(match.range.first - 1)
        if ((preceding == '\'' && url.endsWith('\'')) || (preceding == '‘' && url.endsWith('’')))
            url = url.dropLast(1)
        // Discard surrounding prose delimiters while retaining balanced characters inside a URL.
        for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
            while (url.endsWith(close) && url.count { it == close } > url.count { it == open }) url = url.dropLast(1)
        }
        if ('?' !in url) url = url.trimEnd('.', ',', ';', '!', '،', '؛')
        url
    }.distinct().toList()
    if (tokens.isEmpty()) return invalid("No supported link found. Share an HTTPS or RTSP URL.")
    if (tokens.size > 1) return invalid("Several links were shared. Share one URL at a time.")
    val url = supported(tokens.single())
        ?: return invalid("This link is not supported. Use HTTPS or RTSP without embedded passwords.")
    return SharedStreamInput.Ready(url)
}
