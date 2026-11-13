package com.subtitleplayer.prototype

import android.text.Html
import android.text.style.URLSpan
import java.io.IOException
import java.io.InputStream
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
        val address: URI,
        val directory: Boolean = false
    ) : StreamIndexEntry {
        override val url get() = address.toString()
    }

    data class CatalogPage(
        override val title: String,
        override val fileName: String,
        override val url: String,
        val entries: List<StreamIndexEntry>,
        val releaseYear: String? = null
    ) : StreamIndexEntry {
        val searchTitles = listOf(title.removeSuffix(" ${releaseYear.orEmpty()}"), fileName)
            .map(::streamSearchText).filter(String::isNotEmpty)
            .flatMap { name -> listOfNotNull(name, releaseYear?.let { "$name $it" }) }
            .distinct()
        val searchText = streamSearchText("$title $fileName")
        val compactSearchTitles = searchTitles.map { it.replace(" ", "") }
    }
}

internal data class StreamSearchResult(val entry: StreamIndexEntry, val location: String? = null)
internal class StreamIndexHttpException(val status: Int) : IOException("Index server returned HTTP $status")

internal fun streamEpisodeTitle(episode: StreamIndexEntry.Video, location: String?): String =
    listOfNotNull(episode.title, location).joinToString(" · ")

private val streamSearchSeparators = Regex("[^\\p{L}\\p{N}]+")
private val compactEpisode = Regex("(?i)\\bS([0-9]+) *E([0-9]+)\\b")
private val numberedLabel = Regex("(?i)\\b(season|episode|فصل|قسمت) *([0-9]+)\\b")
private val streamSearchGroups = Regex("\\b(?:season|episode) [0-9]+\\b|[\\p{L}\\p{N}]+")
private val episodeFileCode = Regex(
    "(?i)(?<![\\p{L}\\p{N}])(?:S([0-9]{1,3})[ ._-]*E([0-9]{1,4})|([0-9]{1,3})x([0-9]{1,4}))(?![\\p{L}\\p{N}])"
)
private val episodeFileLabel = Regex("(?i)(?<![\\p{L}\\p{N}])(?:episode|ep|e)[ ._-]*([0-9]{1,4})(?![\\p{L}\\p{N}])")

private fun String.withoutLeadingZeros(): String = trimStart('0').ifEmpty { "0" }

internal fun streamSearchText(value: String): String {
    val digits = buildString(value.length) {
        for (char in value) {
            val digit = Character.digit(char, 10)
            append(when {
                digit >= 0 -> '0' + digit
                char == 'ي' || char == 'ى' -> 'ی'
                char == 'ك' -> 'ک'
                else -> char
            })
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

internal const val archiveSearchLimit = 150

internal fun searchArchiveTitles(
    entries: List<StreamIndexEntry>,
    query: String
): List<StreamIndexEntry.CatalogPage> {
    val phrase = streamSearchText(query)
    val titles = entries.asSequence().filterIsInstance<StreamIndexEntry.CatalogPage>()
    if (phrase.isEmpty()) return titles.take(archiveSearchLimit).toList()

    val terms = streamSearchTerms(phrase)
    val persianPhrase = phrase.takeIf { it.any { char -> char in '\u0600'..'\u06ff' } }
        ?.replace(" ", "")
    val ranked = List(5) { mutableListOf<StreamIndexEntry.CatalogPage>() }
    for (title in titles) {
        val rank = when {
            title.searchTitles.any { it.equals(phrase, ignoreCase = true) } -> 0
            title.searchTitles.any { it.startsWith(phrase, ignoreCase = true) } -> 1
            title.searchTitles.any { it.contains(phrase, ignoreCase = true) } -> 2
            terms.all { title.searchText.contains(it, ignoreCase = true) } -> 3
            persianPhrase != null && title.compactSearchTitles.any {
                it.contains(persianPhrase, ignoreCase = true)
            } -> 4
            else -> continue
        }
        if (ranked[rank].size < archiveSearchLimit) ranked[rank].add(title)
    }
    return ranked.asSequence().flatten().take(archiveSearchLimit).toList()
}

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

internal fun archiveEpisodeEntry(entry: StreamIndexEntry): StreamIndexEntry {
    if (entry !is StreamIndexEntry.Video) return entry
    val code = episodeFileCode.find(entry.fileName)
    if (code != null) {
        val season = code.groupValues[1].ifEmpty { code.groupValues[3] }.withoutLeadingZeros()
        val episode = code.groupValues[2].ifEmpty { code.groupValues[4] }.withoutLeadingZeros()
        return entry.copy(title = "Season $season · Episode $episode")
    }
    val episode = episodeFileLabel.find(entry.fileName)?.groupValues?.get(1) ?: return entry
    return entry.copy(title = "Episode ${episode.withoutLeadingZeros()}")
}

internal fun extractStreamIndexEntries(base: URI, html: String, directory: Boolean = false): List<StreamIndexEntry> {
    val text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    return text.getSpans(0, text.length, URLSpan::class.java).asSequence()
        .mapNotNull { span ->
            val title = text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString()
            resolveStreamIndexEntry(base, span.url, title)
        }
        .distinctBy { it.url }.take(300)
        .map { if (directory) archiveEpisodeEntry(it) else it }
        .toList()
}

internal suspend fun readStreamIndex(start: URI, scanOrigin: URI? = null, directory: Boolean = false): List<StreamIndexEntry> {
    val context = currentCoroutineContext()
    return readStreamResponse(start, "text/html, application/xhtml+xml", scanOrigin) { url, contentType, input ->
        val mime = contentType.substringBefore(';').trim()
        if (!mime.equals("text/html", true) && !mime.equals("application/xhtml+xml", true)) {
            throw IOException("Unavailable HTML index")
        }
        val charset = contentType.split(';').drop(1).firstOrNull {
            it.trim().startsWith("charset=", ignoreCase = true)
        }?.substringAfter('=')?.trim()?.trim('"')?.let {
            runCatching { Charset.forName(it) }.getOrNull()
        } ?: Charsets.UTF_8
        val bytes = readSubtitleBytes(input, 512 * 1024) { context.ensureActive() }
        context.ensureActive()
        val base = if (directory && !url.rawPath.endsWith('/')) {
            URI.create(url.toString().substringBefore('?') + "/")
        } else url
        extractStreamIndexEntries(base, String(bytes, charset), directory)
    }
}

internal suspend fun <T> readStreamResponse(
    start: URI,
    accept: String,
    redirectOrigin: URI? = null,
    read: (URI, String, InputStream) -> T
): T {
    val context = currentCoroutineContext()
    var url = start
    repeat(5) {
        context.ensureActive()
        val connection = url.toURL().openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.setRequestProperty("Accept", accept)
            val status = connection.responseCode
            if (status in setOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location") ?: throw IOException("Missing redirect")
                url = parseStreamIndexUrl(url.resolve(location).toString()) ?: throw IOException("Invalid redirect")
                if (redirectOrigin != null && !sameStreamOrigin(redirectOrigin, url)) {
                    throw IOException("Catalog scan cannot follow a redirect to another origin")
                }
            } else {
                if (status != 200) throw StreamIndexHttpException(status)
                return connection.inputStream.use { read(url, connection.contentType.orEmpty(), it) }
            }
        } catch (invalid: IllegalArgumentException) {
            throw IOException("Invalid index URL", invalid)
        } finally {
            connection.disconnect()
        }
    }
    throw IOException("Too many redirects")
}
