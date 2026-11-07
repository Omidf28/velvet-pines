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

internal data class LibraryVideo(val uri: Uri, val name: String, val parentPath: String? = null, val parentDirectory: Uri? = null, val bookmark: PlaybackBookmark? = null)

internal data class LibrarySubfolder(val documentId: String, val name: String)

internal data class LibraryFolderShortcut(val tree: Uri, val path: List<LibrarySubfolder> = emptyList()) {
    fun sameFolder(other: LibraryFolderShortcut): Boolean =
        tree == other.tree && path.lastOrNull()?.documentId == other.path.lastOrNull()?.documentId
}

internal data class LibraryFolderListing(val folders: List<LibrarySubfolder>, val videos: List<LibraryVideo>)
internal const val recentVideoLimit = 20
internal const val playbackQueueLimit = 100
internal const val favoriteFolderLimit = 20
private val videoExtensions = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp", "ts", "mpeg", "mpg")

internal fun libraryGrantUri(uri: Uri): Uri {
    if (uri.scheme != ContentResolver.SCHEME_CONTENT) return uri
    val authority = uri.authority ?: return uri
    return if (DocumentsContract.isTreeUri(uri)) {
        DocumentsContract.buildTreeDocumentUri(authority, DocumentsContract.getTreeDocumentId(uri))
    } else uri
}

internal fun Set<Uri>.coversReadAccess(uri: Uri): Boolean = uri in this || libraryGrantUri(uri) in this

internal fun parseLibraryFolderUri(value: String?): Uri? {
    val uri = value?.let(Uri::parse) ?: return null
    if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority.isNullOrBlank() ||
        !DocumentsContract.isTreeUri(uri)) return null
    return try {
        libraryGrantUri(uri)
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun readFavoriteFolderShortcuts(encoded: String): List<LibraryFolderShortcut> {
    val array = try {
        JSONArray(encoded)
    } catch (_: JSONException) {
        return emptyList()
    }
    val shortcuts = mutableListOf<LibraryFolderShortcut>()
    for (index in 0 until minOf(array.length(), favoriteFolderLimit)) {
        val item = array.optJSONObject(index) ?: continue
        val tree = parseLibraryFolderUri(item.optString("tree")) ?: continue
        val entries = item.optJSONArray("path") ?: continue
        val path = (0 until entries.length()).mapNotNull { step ->
            val child = entries.optJSONObject(step) ?: return@mapNotNull null
            val id = child.optString("id")
            val name = child.optString("name")
            if (id.isBlank() || name.isBlank()) null else LibrarySubfolder(id, name)
        }
        val ids = path.map { it.documentId }
        if (path.size != entries.length() || ids.toSet().size != ids.size ||
            DocumentsContract.getTreeDocumentId(tree) in ids) continue

        val shortcut = LibraryFolderShortcut(tree, path)
        if (shortcuts.none { it.sameFolder(shortcut) }) shortcuts.add(shortcut)
    }
    return shortcuts
}

internal fun writeFavoriteFolderShortcuts(shortcuts: List<LibraryFolderShortcut>): String =
    JSONArray().apply {
        for (shortcut in shortcuts) {
            val path = JSONArray()
            for (child in shortcut.path) {
                path.put(JSONObject().put("id", child.documentId).put("name", child.name))
            }
            put(JSONObject().put("tree", shortcut.tree.toString()).put("path", path))
        }
    }.toString()

internal fun fallbackFolderName(tree: Uri): String =
    DocumentsContract.getTreeDocumentId(tree).substringAfterLast('/').substringAfterLast(':')
        .ifBlank { "پوشهٔ ذخیره‌سازی" }

internal fun readFolderName(resolver: ContentResolver, tree: Uri): String? {
    val document = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    return resolver.query(document, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0)?.takeIf(String::isNotBlank) else null
    }
}
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
        if (uri.scheme == ContentResolver.SCHEME_CONTENT && name.isNotBlank()) {
            val parent = item.optString("parentDirectory").takeIf(String::isNotBlank)?.let(Uri::parse)?.takeIf {
                it.scheme == ContentResolver.SCHEME_CONTENT && it.authority == uri.authority && DocumentsContract.isTreeUri(it)
            }
            val bookmark = if (item.has("positionMs")) capturePlaybackBookmark(
                item.optLong("positionMs"), item.optLong("durationMs"), item.optBoolean("completed")
            ) else null
            LibraryVideo(uri, name, parentPath = item.optString("parentPath").takeIf(String::isNotBlank),
                parentDirectory = parent, bookmark = bookmark)
        } else null
    }.distinctBy(LibraryVideo::uri)
}

internal fun writeVideoEntries(videos: List<LibraryVideo>): String =
    JSONArray().apply {
        videos.forEach { video ->
            put(JSONObject().apply {
                put("uri", video.uri.toString())
                put("name", video.name)
                put("parentDirectory", video.parentDirectory?.toString().orEmpty())
                put("parentPath", video.parentPath.orEmpty())
                video.bookmark?.let {
                    put("positionMs", it.positionMs)
                    put("durationMs", it.durationMs)
                    put("completed", it.completed)
                }
            })
        }
    }.toString()

internal fun addRecentVideo(videos: List<LibraryVideo>, video: LibraryVideo): List<LibraryVideo> =
    (listOf(video) + videos.filterNot { videoSubtitleKey(it.uri) == videoSubtitleKey(video.uri) }).take(recentVideoLimit)

internal suspend fun scanVideoFolder(
    resolver: ContentResolver,
    tree: Uri,
    path: List<LibrarySubfolder>,
    includeSubfolders: Boolean
): LibraryFolderListing {
    val rootId = DocumentsContract.getTreeDocumentId(tree)
    val currentId = path.lastOrNull()?.documentId ?: rootId
    val currentPath = path.takeIf { it.isNotEmpty() }?.joinToString(" / ") { it.name }
    val queue = ArrayDeque<Pair<String, String?>>()
    queue.addLast(currentId to currentPath)
    val ancestors = (listOf(rootId) + path.dropLast(1).map { it.documentId }).toSet() - currentId
    val seen = ancestors.toMutableSet()
    val folders = mutableListOf<LibrarySubfolder>()
    val videos = mutableListOf<LibraryVideo>()
    val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
    val context = currentCoroutineContext()

    while (queue.isNotEmpty()) {
        context.ensureActive()
        val (folderId, relativePath) = queue.removeFirst()
        if (!seen.add(folderId)) continue
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, folderId)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId)
        val cursor = resolver.query(children, columns, null, null, null)
            ?: throw IOException("Folder provider did not return documents")
        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val nameColumn = it.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            while (it.moveToNext()) {
                context.ensureActive()
                val id = it.getString(idColumn)?.takeIf(String::isNotBlank) ?: continue
                val name = it.getString(nameColumn)?.takeIf(String::isNotBlank) ?: continue
                val mime = it.getString(mimeColumn)
                if (mime == Document.MIME_TYPE_DIR) {
                    if (id in seen) continue
                    if (folderId == currentId) folders.add(LibrarySubfolder(id, name))
                    if (includeSubfolders) {
                        val nestedPath = if (relativePath == null) name else "$relativePath / $name"
                        queue.addLast(id to nestedPath)
                    }
                } else if (isVideo(name, mime)) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    videos.add(LibraryVideo(uri, name, relativePath, parent))
                }
            }
        }
    }
    return LibraryFolderListing(
        folders = folders.distinctBy(LibrarySubfolder::documentId).sortedWith { a, b ->
            val name = compareNaturalNames(a.name, b.name)
            if (name != 0) name else a.documentId.compareTo(b.documentId)
        },
        videos = videos.distinctBy(LibraryVideo::uri).sortedWith { a, b ->
            val name = compareNaturalNames(a.name, b.name)
            if (name != 0) name else compareNaturalNames(a.parentPath.orEmpty(), b.parentPath.orEmpty())
        }
    )
}
internal fun isVideo(name: String, mime: String?): Boolean =
    mime?.startsWith("video/") == true ||
        ((mime.isNullOrBlank() || mime == "application/octet-stream" || mime == "application/x-matroska") &&
            name.substringAfterLast('.', "").lowercase(Locale.ROOT) in videoExtensions)
