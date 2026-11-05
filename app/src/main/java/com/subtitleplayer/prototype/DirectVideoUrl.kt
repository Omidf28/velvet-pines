package com.subtitleplayer.prototype

import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

internal enum class OnlineVideoFormat { Auto, Hls, Dash, Rtsp, Smb }

internal data class OnlineVideoLink(val url: String, val format: OnlineVideoFormat)

internal fun parseOnlineVideoLink(
    input: String,
    format: OnlineVideoFormat = OnlineVideoFormat.Auto
): OnlineVideoLink? {
    val uri = try {
        URI(input.trim())
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    if ((scheme != "https" && scheme != "rtsp" && scheme != "smb") ||
        uri.host.isNullOrBlank() || uri.rawUserInfo != null
    ) return null
    if (uri.port != -1 && uri.port !in 1..65535) return null

    val path = uri.path.orEmpty()
    val resolvedFormat = when {
        scheme == "smb" && (format != OnlineVideoFormat.Auto && format != OnlineVideoFormat.Smb) -> return null
        scheme == "smb" -> if (parseSmbFileLocation(uri) != null) OnlineVideoFormat.Smb else return null
        format == OnlineVideoFormat.Smb -> return null
        scheme == "rtsp" && format != OnlineVideoFormat.Auto && format != OnlineVideoFormat.Rtsp -> return null
        scheme == "rtsp" -> OnlineVideoFormat.Rtsp
        format == OnlineVideoFormat.Rtsp -> return null
        format != OnlineVideoFormat.Auto -> format
        path.endsWith(".m3u8", ignoreCase = true) -> OnlineVideoFormat.Hls
        path.endsWith(".mpd", ignoreCase = true) -> OnlineVideoFormat.Dash
        else -> OnlineVideoFormat.Auto
    }
    val url = "$scheme:" + uri.toASCIIString().substringAfter(':').substringBefore('#')
    return OnlineVideoLink(url, resolvedFormat)
}

internal data class SmbFileLocation(val host: String, val port: Int, val share: String, val path: String) {
    fun child(name: String): SmbFileLocation {
        require(isSmbEntryName(name))
        return copy(path = if (path.isEmpty()) name else "$path\\$name")
    }

    fun parent(): SmbFileLocation = copy(path = path.substringBeforeLast('\\', ""))

    fun toUrl(): String = URI(
        "smb", null, host, if (port == 445) -1 else port,
        "/$share" + if (path.isEmpty()) "/" else "/" + path.replace('\\', '/'),
        null, null
    ).toASCIIString()
}

internal fun parseSmbFileLocation(uri: URI): SmbFileLocation? = parseSmbLocation(uri, directory = false)

internal fun parseSmbDirectory(input: String): SmbFileLocation? {
    val uri = try {
        URI(input.trim())
    } catch (_: URISyntaxException) {
        return null
    }
    return parseSmbLocation(uri, directory = true)
}

internal fun isSmbEntryName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." &&
        name.none { it.isISOControl() || it in "/\\:*?\"<>|" }

private fun parseSmbLocation(uri: URI, directory: Boolean): SmbFileLocation? {
    val host = uri.host?.removeSurrounding("[", "]") ?: return null
    if (!uri.scheme.equals("smb", ignoreCase = true) || uri.rawUserInfo != null ||
        uri.rawQuery != null || (uri.port != -1 && uri.port !in 1..65535)
    ) return null

    val rawPath = uri.path.orEmpty().removePrefix("/")
    val parts = (if (directory) rawPath.removeSuffix("/") else rawPath).split('/')
    if (parts.size < (if (directory) 1 else 2) || parts.any { !isSmbEntryName(it) }) return null

    return SmbFileLocation(host, if (uri.port == -1) 445 else uri.port, parts.first(), parts.drop(1).joinToString("\\"))
}

internal fun remoteVideoId(url: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
    return "remote:" + Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
}
