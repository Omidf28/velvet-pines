package com.subtitleplayer.prototype

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class LibraryVideo(val uri: Uri, val name: String, val parentPath: String? = null)

internal const val recentVideoLimit = 20
internal const val playbackQueueLimit = 100
private val videoExtensions = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts", "mpeg", "mpg")

internal fun libraryGrantUri(uri: Uri): Uri {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return uri
    val authority = uri.authority ?: return uri
    return if (DocumentsContract.isTreeUri(uri)) {
        DocumentsContract.buildTreeDocumentUri(authority, DocumentsContract.getTreeDocumentId(uri))
    } else uri
}

internal fun Set<Uri>.coversReadAccess(uri: Uri): Boolean = uri in this || libraryGrantUri(uri) in this

internal fun readVideoEntries(encoded: String?, limit: Int): List<LibraryVideo> {
    if (encoded == null) return emptyList()
    val array = try {
        JSONArray(encoded)
    } catch (_: JSONException) {
        return emptyList()
    }
    return (0 until minOf(array.length(), limit)).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val uri = Uri.parse(item.optString("uri"))
        val name = item.optString("name")
        if (uri.scheme == ContentResolver.SCHEME_CONTENT && name.isNotBlank()) LibraryVideo(uri, name)
        else null
    }.distinctBy(LibraryVideo::uri)
}

internal fun writeVideoEntries(videos: List<LibraryVideo>): String =
    JSONArray().apply {
        videos.forEach { video ->
            put(JSONObject().put("uri", video.uri.toString()).put("name", video.name))
        }
    }.toString()

internal fun addRecentVideo(videos: List<LibraryVideo>, video: LibraryVideo): List<LibraryVideo> =
    (listOf(video) + videos.filterNot { it.uri == video.uri }).take(recentVideoLimit)

internal suspend fun scanVideoFolder(resolver: ContentResolver, tree: Uri): List<LibraryVideo> {
    val queue = ArrayDeque<Pair<String, String?>>()
    queue.addLast(DocumentsContract.getTreeDocumentId(tree) to null)
    val seen = mutableSetOf<String>()
    val videos = mutableListOf<LibraryVideo>()
    val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
    val context = currentCoroutineContext()

    while (queue.isNotEmpty()) {
        context.ensureActive()
        val (folderId, path) = queue.removeFirst()
        if (!seen.add(folderId)) continue
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val cursor = resolver.query(children, columns, null, null, null)
            ?: throw IOException("Folder provider did not return documents")
        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameColumn = it.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            while (it.moveToNext()) {
                context.ensureActive()
                val id = it.getString(idColumn) ?: continue
                val name = it.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                val mime = it.getString(mimeColumn)
                if (mime == Document.MIME_TYPE_DIR) {
                    val nestedPath = if (path == null) name else "$path / $name"
                    queue.addLast(id to nestedPath)
                } else if (isVideo(name, mime)) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    videos.add(LibraryVideo(uri, name, path))
                }
            }
        }
    }
    return videos.distinctBy(LibraryVideo::uri).sortedWith(
        Comparator { a, b ->
            val name = compareNaturalNames(a.name, b.name)
            if (name != 0) name else compareNaturalNames(a.parentPath.orEmpty(), b.parentPath.orEmpty())
        }
    )
}

internal fun isVideo(name: String, mime: String?): Boolean =
    mime?.startsWith("video/") == true ||
        ((mime.isNullOrBlank() || mime == "application/octet-stream" || mime == "application/x-matroska") &&
            name.substringAfterLast('.', "").lowercase(Locale.ROOT) in videoExtensions)
