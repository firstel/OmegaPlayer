package com.arslandaim.omegaplayer.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.arslandaim.omegaplayer.data.VideoModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Adds only the granted tree, never scans other locations or converts URIs into paths. */
internal class DocumentVideoLibrary(private val context: Context) {
    private val store = DocumentFolderStore(context)

    suspend fun scan(): List<VideoModel> {
        val tree = store.savedTree() ?: return emptyList()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootName = rootId.substringAfterLast('/').substringAfter(':').ifBlank { "Selected folder" }
        return scanVideoDocuments(rootId, "SD card · $rootName") { directory ->
            store.list(tree, directory?.uri?.let(Uri::parse)).map { entry ->
                LibraryDocument(DocumentsContract.getDocumentId(entry.uri), entry.uri.toString(),
                    entry.name, entry.isDirectory, entry.mediaType == DocumentMediaType.VIDEO, entry.size)
            }
        }.map { (entry, folder) ->
            val digest = MessageDigest.getInstance("SHA-256").digest(entry.uri.toByteArray(Charsets.UTF_8))
            val stableId = ByteBuffer.wrap(digest).long or Long.MIN_VALUE
            VideoModel(stableId, Uri.parse(entry.uri), entry.name, 0L, entry.size,
                entry.uri, documentFolder = folder)
        }
    }

    /** A document can already exist in MediaStore. Prefer its existing library item. */
    fun merge(media: List<VideoModel>, documents: List<VideoModel>): List<VideoModel> {
        val knownDocuments = media.mapNotNull { video ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    MediaStore.getDocumentUri(context, video.uri)?.let(DocumentsContract::getDocumentId)
                } else null
            } catch (_: Exception) { null }
        }.toSet()
        return media + documents.filter { DocumentsContract.getDocumentId(it.uri) !in knownDocuments }
    }
}
