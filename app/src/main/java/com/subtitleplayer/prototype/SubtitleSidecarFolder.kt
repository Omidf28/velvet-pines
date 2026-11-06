package com.subtitleplayer.prototype

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException

/** Only inspect siblings in an already granted, known SAF parent; IDs remain opaque. */
internal fun discoverSubtitleSidecars(
    resolver: ContentResolver,
    video: Uri,
    parent: Uri,
    cancellation: CancellationSignal
): SidecarMatches {
    if (video.scheme != "content" || parent.authority != video.authority || !DocumentsContract.isTreeUri(parent))
        return SidecarMatches()
    val videoId = try { DocumentsContract.getDocumentId(video) } catch (_: IllegalArgumentException) {
        return SidecarMatches()
    }
    val parentId = try { DocumentsContract.getDocumentId(parent) } catch (_: IllegalArgumentException) {
        DocumentsContract.getTreeDocumentId(parent)
    }
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId)
    val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS)
    val files = mutableListOf<SidecarFile>()
    var videoName: String? = null
    val cursor = resolver.query(children, columns, null, null, null, cancellation)
        ?: throw IOException("Subtitle folder did not return documents")
    cursor.use {
        val idColumn = it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
        val nameColumn = it.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
        val mimeColumn = it.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
        val flagsColumn = it.getColumnIndex(Document.COLUMN_FLAGS)
        var count = 0
        while (it.moveToNext()) {
            cancellation.throwIfCanceled()
            if (++count > 10_000) throw IOException("Subtitle folder has too many entries")
            if (it.getString(mimeColumn) == Document.MIME_TYPE_DIR) continue
            val id = it.getString(idColumn) ?: continue
            val name = it.getString(nameColumn)?.takeIf { name -> name.isNotBlank() && name.length <= 1024 } ?: continue
            if (id == videoId) videoName = name
            val virtual = flagsColumn >= 0 && (it.getInt(flagsColumn) and Document.FLAG_VIRTUAL_DOCUMENT) != 0
            if (!virtual && name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in setOf("srt", "vtt"))
                files.add(SidecarFile(id, name))
        }
    }
    cancellation.throwIfCanceled()
    return videoName?.let { matchSubtitleSidecars(it, files) } ?: SidecarMatches()
}
