package com.subtitleplayer.prototype

import java.io.IOException
import java.net.URI
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun savedStreamSource(input: String): URI? =
    input.trim().takeIf { it.length <= 2_048 }?.let(::parseStreamIndexUrl)
        ?.normalize()?.takeIf { it.rawQuery == null }

internal fun sameStreamOrigin(first: URI, second: URI): Boolean =
    first.scheme == second.scheme && first.host.equals(second.host, ignoreCase = true) &&
        first.httpsPort == second.httpsPort

private val URI.httpsPort get() = if (port == -1) 443 else port

internal data class StreamCatalogScan(
    val rootEntries: List<StreamIndexEntry>,
    val episodes: List<StreamSearchResult>,
    val pagesRead: Int,
    val failedPages: Int
)

internal suspend fun scanStreamCatalog(
    root: URI,
    read: suspend (URI) -> List<StreamIndexEntry> = { readStreamIndex(it, scanOrigin = root) }
): StreamCatalogScan {
    val context = currentCoroutineContext()
    data class Pending(val address: URI, val location: String?, val depth: Int)
    val pending = ArrayDeque(listOf(Pending(root, null, 0)))
    val scheduled = mutableSetOf(root)
    val episodes = mutableListOf<StreamSearchResult>()
    var rootEntries = emptyList<StreamIndexEntry>()
    var pagesRead = 0
    var failedPages = 0
    while (pending.isNotEmpty()) {
        context.ensureActive()
        val page = pending.removeFirst()
        val entries = try {
            read(page.address)
        } catch (error: IOException) {
            if (page.depth == 0) throw error
            failedPages++
            continue
        }
        pagesRead++
        if (page.depth == 0) rootEntries = entries
        else entries.filterIsInstance<StreamIndexEntry.Video>().forEach {
            episodes += StreamSearchResult(it, page.location)
        }
        if (page.depth == 3) continue
        for (child in entries.filterIsInstance<StreamIndexEntry.Page>()) {
            if (scheduled.size == 12) break
            if (sameStreamOrigin(root, child.address) && scheduled.add(child.address)) {
                val location = listOfNotNull(page.location, child.title).joinToString(" / ")
                pending.addLast(Pending(child.address, location, page.depth + 1))
            }
        }
    }
    return StreamCatalogScan(rootEntries, episodes, pagesRead, failedPages)
}
