package com.arslandaim.omegaplayer.data.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DocumentLibraryScanTest {
    private fun video(id: String) = LibraryDocument(id, "content://test/$id", "$id.mp4", isVideo = true, size = 42)
    private fun folder(id: String) = LibraryDocument(id, "content://test/$id", id, isDirectory = true)

    @Test fun selectedFolderAndNestedVideosReachLibraryWithoutAudioOrDuplicates() = runBlocking {
        val result = scanVideoDocuments("root", "SD card · Movies") { directory ->
            when (directory?.id) {
                null -> listOf(video("one"), folder("nested"), LibraryDocument("audio", "content://test/audio", "audio.mp3"))
                "nested" -> listOf(video("two"), video("one"), folder("nested"))
                else -> emptyList()
            }
        }
        assertEquals(listOf("one", "two"), result.map { it.first.id })
        assertEquals("SD card · Movies/nested", result.last().second)
        assertEquals(42L, result.first().first.size)
    }

    @Test fun inaccessibleChildDoesNotHideOtherVideos() = runBlocking {
        val result = scanVideoDocuments("root", "Movies") { directory ->
            if (directory == null) listOf(folder("denied"), video("one"))
            else throw SecurityException("revoked child")
        }
        assertEquals(listOf("one"), result.map { it.first.id })
    }

    @Test fun revokedRootFailsInsteadOfReturningCachedVideos() = runBlocking {
        try {
            scanVideoDocuments("root", "Movies") { throw SecurityException("card removed") }
            fail("Root failure must request recovery")
        } catch (_: SecurityException) { }
    }

    @Test fun cancellationStopsScanning() = runBlocking {
        try {
            scanVideoDocuments("root", "Movies") { throw CancellationException("new folder") }
            fail("Cancelled scans must not publish stale results")
        } catch (_: CancellationException) { }
    }
}
