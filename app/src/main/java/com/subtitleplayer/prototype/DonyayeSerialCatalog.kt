package com.subtitleplayer.prototype

import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.IOException
import java.io.Reader
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val archivePath = "/DonyayeSerial/10_thous.html"
private const val archiveHost = "dls6.aparatchi-dlcenter.top"
private const val catalogByteLimit = 24 * 1024 * 1024

internal fun isDonyayeSerialArchive(url: URI): Boolean =
    url.scheme == "https" && url.host.equals(archiveHost, ignoreCase = true) &&
        url.port in listOf(-1, 443) && url.path == archivePath && url.rawQuery == null

internal suspend fun readDonyayeSerialCatalog(
    archive: URI,
    cacheDirectory: File,
    refresh: Boolean = false
): StreamIndexEntry.CatalogPage {
    val cache = File(cacheDirectory, "donyaye-serial.js")
    val context = currentCoroutineContext()
    fun readCatalog(file: File): StreamIndexEntry.CatalogPage {
        if (file.length() > catalogByteLimit) throw IOException("Catalog exceeds size limit")
        val entries = file.reader(Charsets.UTF_8).use {
            parseDonyayeSerialCatalog(archive, it) { context.ensureActive() }
        }
        if (entries.isEmpty()) throw IOException("Catalog has no playable entries")
        return StreamIndexEntry.CatalogPage("Donyaye Serial", "", archive.toString(), entries)
    }

    if (!refresh && cache.isFile) {
        try {
            return readCatalog(cache)
        } catch (_: IOException) {
            context.ensureActive()
        }
    }

    val pending = File.createTempFile("donyaye-serial-", ".tmp", cacheDirectory)
    try {
        val script = archive.resolve("10_thous.js")
        readStreamResponse(script, "text/javascript, application/javascript, */*", archive) { _, _, input ->
            pending.outputStream().buffered().use { output ->
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    context.ensureActive()
                    val size = input.read(buffer)
                    if (size < 0) break
                    total += size
                    if (total > catalogByteLimit) throw IOException("Catalog exceeds size limit")
                    output.write(buffer, 0, size)
                }
            }
        }
        val catalog = readCatalog(pending)
        context.ensureActive()
        Files.move(pending.toPath(), cache.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        return catalog
    } finally {
        pending.delete()
    }
}

internal fun parseDonyayeSerialCatalog(
    archive: URI,
    source: Reader,
    checkActive: () -> Unit = {}
): List<StreamIndexEntry.CatalogPage> {
    val input = source.buffered()
    val prefix = StringBuilder()
    while (prefix.length < 64) {
        val char = input.read()
        if (char < 0 || char == '='.code) break
        prefix.append(char.toChar())
    }
    if (!Regex("(?:let|const|var)\\s+data").matches(prefix.toString().removePrefix("\uFEFF").trim())) {
        throw IOException("Unrecognized archive catalog")
    }

    // The archive uses single-quoted strings. Read its array as data and leave the script body unread.
    val titles = LinkedHashMap<String, StreamIndexEntry.CatalogPage>()
    try {
        val json = JsonReader(input).apply { isLenient = true }
        json.beginArray()
        while (json.hasNext()) {
            checkActive()
            json.beginArray()
            val id = json.nextString()
            val type = json.nextString()
            val english = json.nextString().trim()
            val persian = json.nextString().trim()
            val year = json.nextString()
            repeat(2) { json.skipValue() }
            val options = when (type) {
                "movie" -> json.readMovieOptions(archive)
                "series" -> json.readSeriesOptions(archive)
                else -> emptyList<StreamIndexEntry>().also { json.skipValue() }
            }
            while (json.hasNext()) json.skipValue()
            json.endArray()
            if (options.isNotEmpty() && imdbId.matches(id) && (english.isNotEmpty() || persian.isNotEmpty())) {
                val title = listOf(english.ifEmpty { persian }, year).filter(String::isNotBlank).joinToString(" ")
                titles.putIfAbsent(id, StreamIndexEntry.CatalogPage(title, persian, "$archive#$id", options, year))
            }
        }
        json.endArray()
    } catch (error: IllegalStateException) {
        throw IOException("Invalid archive catalog structure", error)
    }
    return titles.values.toList()
}

private val imdbId = Regex("tt[0-9]{5,12}")

private fun JsonReader.readMovieOptions(base: URI): List<StreamIndexEntry.Video> = buildList {
    beginObject()
    while (hasNext()) {
        nextName()
        when (peek()) {
            JsonToken.BEGIN_ARRAY -> addAll(readOptions(base, season = null).filterIsInstance<StreamIndexEntry.Video>())
            JsonToken.BEGIN_OBJECT -> addAll(readSeasonGroups(base, movie = true).filterIsInstance<StreamIndexEntry.Video>())
            else -> skipValue()
        }
    }
    endObject()
}.distinctBy { it.url }

private fun JsonReader.readSeriesOptions(base: URI): List<StreamIndexEntry> = buildList {
    beginObject()
    while (hasNext()) {
        nextName()
        when (peek()) {
            JsonToken.BEGIN_ARRAY -> addAll(readOptions(base, season = null))
            JsonToken.BEGIN_OBJECT -> addAll(readSeasonGroups(base, movie = false))
            else -> skipValue()
        }
    }
    endObject()
}.distinctBy { it.url }

private fun JsonReader.readSeasonGroups(base: URI, movie: Boolean): List<StreamIndexEntry> = buildList {
    beginObject()
    while (hasNext()) {
        val season = nextName()
        if (peek() == JsonToken.BEGIN_ARRAY) {
            addAll(readOptions(base, season.takeUnless { movie }))
        } else skipValue()
    }
    endObject()
}

private fun JsonReader.readOptions(base: URI, season: String?): List<StreamIndexEntry> = buildList {
    beginArray()
    while (hasNext()) {
        var url = ""
        var quality = ""
        var version = ""
        var size = ""
        var episodeCount = ""
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "file" -> if (season == null) url = readLabel() else skipValue()
                "folder" -> if (season != null) url = readLabel() else skipValue()
                "quality" -> quality = readLabel()
                "version" -> version = readLabel()
                "size" -> size = readLabel()
                "number_of_episodes" -> episodeCount = readLabel()
                else -> skipValue()
            }
        }
        endObject()
        val address = parseStreamIndexUrl(url.replace(" ", "%20")) ?: continue
        val details = listOf(quality, version, size).filter(String::isNotBlank)
        if (season == null) {
            val entry = resolveStreamIndexEntry(base, address.toString(), details.joinToString(" · "))
            if (entry is StreamIndexEntry.Video) add(entry)
        } else {
            val label = (listOf("Season $season") + details +
                listOfNotNull(episodeCount.toIntOrNull()?.let { "$it episodes" })).joinToString(" · ")
            add(StreamIndexEntry.Page(label, "", address, directory = true))
        }
    }
    endArray()
}

private fun JsonReader.readLabel(): String = when (peek()) {
    JsonToken.STRING, JsonToken.NUMBER -> nextString()
    else -> "".also { skipValue() }
}
