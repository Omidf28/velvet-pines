package com.subtitleplayer.prototype

import android.text.Html
import android.text.style.URLSpan
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal sealed interface StreamIndexEntry {
    val title: String
    val fileName: String
    val url: String

    data class Video(
        override val title: String,
        override val fileName: String,
        val link: OnlineVideoLink
    ) : StreamIndexEntry {
        override val url get() = link.url
    }

    data class Page(
        override val title: String,
        override val fileName: String,
        val address: URI
    ) : StreamIndexEntry {
        override val url get() = address.toString()
    }
}

internal class StreamIndexHttpException(val status: Int) : IOException("Index server returned HTTP $status")

internal fun streamEpisodeTitle(episode: StreamIndexEntry.Video, location: String?): String =
    listOfNotNull(episode.title, location).joinToString(" · ")

private val streamSearchSeparators = Regex("[^\\p{L}\\p{N}]+")
private val compactEpisode = Regex("(?i)\\bS([0-9]+) *E([0-9]+)\\b")
private val numberedLabel = Regex("(?i)\\b(season|episode|فصل|قسمت) *([0-9]+)\\b")
private val streamSearchGroups = Regex("\\b(?:season|episode) [0-9]+\\b|[\\p{L}\\p{N}]+")

private fun String.withoutLeadingZeros(): String = trimStart('0').ifEmpty { "0" }

private fun streamSearchText(value: String): String {
    val digits = buildString(value.length) {
        for (char in value) {
            val digit = Character.digit(char, 10)
            append(if (digit >= 0) '0' + digit else char)
        }
    }
    val words = digits.replace(streamSearchSeparators, " ").trim()
    val expanded = compactEpisode.replace(words) { match ->
        val (season, episode) = match.destructured
        "season ${season.withoutLeadingZeros()} episode ${episode.withoutLeadingZeros()}"
    }
    return numberedLabel.replace(expanded) { match ->
        val (label, number) = match.destructured
        val kind = if (label.equals("season", ignoreCase = true) || label == "فصل") "season" else "episode"
        "$kind ${number.withoutLeadingZeros()}"
    }
}

internal fun streamSearchTerms(query: String): List<String> =
    streamSearchGroups.findAll(streamSearchText(query)).map { it.value }.toList()

internal fun matchesStreamSearch(terms: List<String>, vararg fields: String?): Boolean {
    if (terms.isEmpty()) return true
    val searchable = fields.filterNotNull().map { " " + streamSearchText(it) + " " }
    return terms.all { term ->
        val needle = if (' ' in term) " $term " else term
        searchable.any { it.contains(needle, ignoreCase = true) }
    }
}

internal fun parseStreamIndexUrl(input: String): URI? =
    parseOnlineVideoLink(input)?.url?.let(URI::create)?.takeIf { it.scheme == "https" }

internal fun resolveStreamIndexEntry(base: URI, href: String, title: String): StreamIndexEntry? {
    val reference = href.trim().replace(" ", "%20")
    if (reference.isEmpty() || reference.startsWith('#') || reference in setOf(".", "./", "..", "../")) return null
    val resolved = try {
        val target = if (reference.startsWith('?')) {
            URI.create(base.toString().substringBefore('#').substringBefore('?') + reference)
        } else {
            base.resolve(reference)
        }
        target.normalize()
    } catch (_: IllegalArgumentException) {
        return null
    }
    val url = parseStreamIndexUrl(resolved.toString()) ?: return null
    if (url == base) return null
    val path = url.path.orEmpty()
    val link = parseOnlineVideoLink(url.toString()) ?: return null
    val fileName = path.trimEnd('/').substringAfterLast('/').ifBlank { url.host }
    val displayTitle = title.trim().takeUnless { "://" in it || it.startsWith("//") }
        ?.take(180).orEmpty().ifBlank { fileName }
    return when {
        link.format != OnlineVideoFormat.Auto || isVideo(path, null) -> StreamIndexEntry.Video(displayTitle, fileName, link)
        path.endsWith(".html", true) || path.endsWith(".htm", true) || path.endsWith(".xhtml", true) ||
            (path.length > 1 && path.endsWith('/')) -> StreamIndexEntry.Page(displayTitle, fileName, url)
        else -> null
    }
}

internal fun extractStreamIndexEntries(base: URI, html: String): List<StreamIndexEntry> {
    val text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    return text.getSpans(0, text.length, URLSpan::class.java).asSequence()
        .mapNotNull { span ->
            val title = text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
            resolveStreamIndexEntry(base, span.url, title)
        }
        .distinctBy { it.url }.take(300).toList()
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
