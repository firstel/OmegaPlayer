package com.arslandaim.omegaplayer.data.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class LibraryDocument(
    val id: String,
    val uri: String,
    val name: String,
    val isDirectory: Boolean = false,
    val isVideo: Boolean = false,
    val size: Long = 0L
)

/** Iterative traversal avoids stack overflow and confines scans to the granted tree. */
internal suspend fun scanVideoDocuments(
    rootId: String,
    rootFolder: String,
    children: suspend (LibraryDocument?) -> List<LibraryDocument>
): List<Pair<LibraryDocument, String>> {
    val pending = ArrayDeque<Pair<LibraryDocument?, String>>()
    pending.add(null to rootFolder)
    val visited = mutableSetOf<String>()
    val result = mutableListOf<Pair<LibraryDocument, String>>()
    while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val (directory, folder) = pending.removeFirst()
        if (!visited.add(directory?.id ?: rootId)) continue
        val entries = try { children(directory) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            if (directory == null) throw error else continue
        }
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            if (entry.isDirectory) pending.add(entry to "$folder/${entry.name}")
            else if (entry.isVideo) result += entry to folder
        }
    }
    return result.distinctBy { it.first.id }
}
