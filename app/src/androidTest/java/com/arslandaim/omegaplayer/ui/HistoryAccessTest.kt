package com.arslandaim.omegaplayer.ui

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.data.RecentPlayback
import com.arslandaim.omegaplayer.ui.feature.library.HistoryAccessState
import com.arslandaim.omegaplayer.ui.feature.library.canReadHistoryMedia
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class HistoryAccessTest {
    @Test fun savedContentAndVaultPathsAreHiddenAfterDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val file = File(context.filesDir, "history-access-test.mp4").apply { writeBytes(byteArrayOf(1)) }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "history-access-test.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OmegaHistoryTest")
        })!!
        try {
            resolver.openOutputStream(uri)!!.use { it.write(1) }
            assertTrue(canReadHistoryMedia(context, uri.toString()))
            assertTrue(canReadHistoryMedia(context, file.toURI().toString()))
            assertTrue(canReadHistoryMedia(context, file.absolutePath))
            resolver.delete(uri, null, null)
            file.delete()
            assertFalse(canReadHistoryMedia(context, uri.toString()))
            assertFalse(canReadHistoryMedia(context, file.toURI().toString()))
            assertFalse(canReadHistoryMedia(context, "content://com.android.externalstorage.documents/document/FFFF-FFFF%3AMovies%2Fmissing.mp4"))
        } finally { resolver.delete(uri, null, null); file.delete() }
    }

    private fun item(uri: String) = RecentPlayback(uri = uri, position = 1000, duration = 10000,
        mediaType = "video", name = "History test")

    @Test fun inaccessibleEntriesNeverBecomeWatchButtonsAndTapRechecks() = runBlocking {
        val existing = item("existing")
        val missing = item("missing")
        val readable = mutableSetOf(existing.uri)
        val state = HistoryAccessState(this) { it in readable }
        state.refresh(listOf(existing, missing))
        assertTrue(state.items.isEmpty())
        yield()
        assertEquals(listOf(existing), state.items)
        var opened = false
        readable.clear() // Simulate unplugging the SD card after the buttons were shown.
        state.open(existing) { opened = true }
        yield()
        assertFalse(opened)
        assertTrue(state.items.isEmpty())
        readable.add(existing.uri)
        state.refresh(listOf(existing, missing)) // A returned card/permission makes history usable again.
        yield()
        state.open(existing) { opened = true }
        yield()
        assertTrue(opened)
    }
}
