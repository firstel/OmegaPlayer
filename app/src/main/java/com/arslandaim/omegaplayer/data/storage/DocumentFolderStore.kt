package com.arslandaim.omegaplayer.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class FolderEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val mediaType: DocumentMediaType?,
    val size: Long = 0L
)

/** Never resolves content URIs to filesystem paths or relies on MediaStore indexing. */
internal class DocumentFolderStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val preferences = context.applicationContext.getSharedPreferences("document_folders", Context.MODE_PRIVATE)

    fun savedTree(): Uri? = preferences.getString("tree_uri", null)?.let(Uri::parse)

    fun rememberTree(uri: Uri) {
        // Save only after persistence succeeds. Cancelling/rejecting a picker keeps the old folder.
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        preferences.edit().putString("tree_uri", uri.toString()).apply()
        // Retain older grants: previously opened media/history may still need them.
    }

    suspend fun list(tree: Uri, directory: Uri?): List<FolderEntry> = withContext(Dispatchers.IO) {
        check(resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }) {
            "Folder access has expired. Select the folder again."
        }
        val documentId = directory?.let(DocumentsContract::getDocumentId)
            ?: DocumentsContract.getTreeDocumentId(tree)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val entries = mutableListOf<FolderEntry>()
        val cursor = resolver.query(children, columns, null, null, null)
            ?: error("Folder is unavailable. Reinsert the SD card or select the folder again.")
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0) ?: continue
                val name = it.getString(1) ?: "Unnamed"
                val mime = it.getString(2)
                val flags = it.getInt(3)
                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val type = DocumentMediaType.from(mime, name)
                // Virtual documents cannot be opened directly by the playback service.
                if (flags and DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT != 0) continue
                if (isDirectory || type != null) {
                    entries += FolderEntry(DocumentsContract.buildDocumentUriUsingTree(tree, id), name, isDirectory, type, if (it.isNull(4)) 0L else it.getLong(4))
                }
            }
        }
        entries.sortedWith(compareBy<FolderEntry> { !it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
