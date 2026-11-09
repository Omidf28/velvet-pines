package com.subtitleplayer.prototype

import java.io.IOException
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StreamCatalogScanTest {
    private val root = URI("https://media.example/archive.html")
    private fun page(address: String, title: String) =
        StreamIndexEntry.Page(title, title, root.resolve(address))

    @Test fun scansLinkedSeasonsOnceAndKeepsTheirSearchPath() = runBlocking {
        val series = page("series.html", "Example Series")
        val season = page("season.html", "Season 2")
        val broken = page("broken.html", "Unavailable")
        val loop = page("archive.html", "Archive")
        val external = page("https://elsewhere.example/catalog.html", "External")
        val alternatePort = page("https://media.example:444/catalog.html", "Other port")
        val video = resolveStreamIndexEntry(season.address, "S02E05.mp4", "Episode 5") as StreamIndexEntry.Video
        val visited = mutableListOf<URI>()
        val result = scanStreamCatalog(root) { address ->
            visited += address
            when (address) {
                root -> listOf(series, broken, external, alternatePort)
                series.address -> listOf(season, loop)
                season.address -> listOf(video)
                broken.address -> throw IOException("Unavailable")
                else -> error("Unexpected request to $address")
            }
        }
        assertEquals(listOf(root, series.address, broken.address, season.address), visited)
        assertEquals(listOf(StreamSearchResult(video, "Example Series / Season 2")), result.episodes)
        assertEquals(3, result.pagesRead)
        assertEquals(1, result.failedPages)
    }

    @Test fun scanLimitsBothPageCountAndDepth() = runBlocking {
        val many = (1..20).map { page("page-$it.html", "Page $it") }
        val wide = scanStreamCatalog(root) { if (it == root) many else emptyList() }
        assertEquals(12, wide.pagesRead)
        val chain = listOf(root) + many.map { it.address }
        val deep = scanStreamCatalog(root) { listOf(many[chain.indexOf(it)]) }
        assertEquals(4, deep.pagesRead)
    }

    @Test fun onlyPublicHttpsIndexesWithoutQueriesAreSaved() {
        assertEquals(root, savedStreamSource(" https://media.example/./archive.html "))
        assertTrue(sameStreamOrigin(root, URI("https://MEDIA.example:443/season.html")))
        assertNull(savedStreamSource("https://media.example/archive.html?token=secret"))
        assertNull(savedStreamSource("https://user:password@media.example/archive.html"))
        assertNull(savedStreamSource("http://media.example/archive.html"))
    }
}
