package com.subtitleplayer.prototype

import android.text.Html
import android.text.style.URLSpan
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class StreamIndexEntry(val title: String, val fileName: String, val link: OnlineVideoLink)
internal class StreamIndexHttpException(val status: Int) : IOException("Index server returned HTTP $status")

private val streamSearchSeparators = Regex("[^\\p{L}\\p{N}]+")

internal fun streamSearchTerms(query: String): List<String> =
    query.split(streamSearchSeparators).filter(String::isNotBlank)

internal fun parseStreamIndexUrl(input: String): URI? =
    parseOnlineVideoLink(input)?.url?.let(URI::create)?.takeIf { it.scheme == "https" }

internal fun resolveStreamIndexEntry(base: URI, href: String, title: String): StreamIndexEntry? {
    val resolved = try {
        base.resolve(href.trim().replace(" ", "%20")).normalize()
    } catch (_: IllegalArgumentException) {
        return null
    }
    val url = parseStreamIndexUrl(resolved.toString()) ?: return null
    val path = url.path.orEmpty()
    val link = parseOnlineVideoLink(url.toString()) ?: return null
    if (link.format == OnlineVideoFormat.Auto && !isVideo(path, null)) return null
    val fileName = path.substringAfterLast('/')
    return StreamIndexEntry(title.trim().take(180).ifBlank { fileName }, fileName, link)
}

internal fun extractStreamIndexEntries(base: URI, html: String): List<StreamIndexEntry> {
    val text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    return text.getSpans(0, text.length, URLSpan::class.java).asSequence()
        .mapNotNull { span ->
            val title = text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
            resolveStreamIndexEntry(base, span.url, title)
        }
        .distinctBy { it.link.url }.take(300).toList()
}

internal suspend fun readStreamIndex(start: URI): List<StreamIndexEntry> {
    val context = currentCoroutineContext()
    var url = start
    repeat(5) {
        context.ensureActive()
        val connection = url.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.setRequestProperty("Accept", "text/html, application/xhtml+xml")
            val status = connection.responseCode
            if (status in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                url = parseStreamIndexUrl(url.resolve(location).toString()) ?: throw IOException("Invalid redirect")
            } else {
                if (status != 200) throw StreamIndexHttpException(status)
                val contentType = connection.contentType.orEmpty()
                val mime = contentType.substringBefore(';').trim()
                if (!mime.equals("text/html", true) && !mime.equals("application/xhtml+xml", true)) {
                    throw IOException("Unavailable HTML index")
                }
                val charset = contentType.split(';').drop(1).firstOrNull {
                    it.trim().startsWith("charset=", ignoreCase = true)
                }?.substringAfter('=')?.trim()?.trim('"')?.let {
                    runCatching { Charset.forName(it) }.getOrNull()
                } ?: Charsets.UTF_8
                val bytes = connection.inputStream.use { readSubtitleBytes(it, 512 * 1024) }
                context.ensureActive()
                return extractStreamIndexEntries(url, String(bytes, charset))
            }
        } catch (invalid: IllegalArgumentException) {
            throw IOException("Invalid index URL", invalid)
        } finally {
            connection.disconnect()
        }
    }
    throw IOException("Too many redirects")
}
