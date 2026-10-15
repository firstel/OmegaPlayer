package com.arslandaim.omegaplayer.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentMediaTypeTest {
    @Test fun providerMimeTypeTakesPrecedenceOverExtension() {
        assertEquals(DocumentMediaType.AUDIO, DocumentMediaType.from("audio/flac", "track.mp4"))
        assertEquals(DocumentMediaType.VIDEO, DocumentMediaType.from("video/mp4", "clip"))
        assertNull(DocumentMediaType.from("image/jpeg", "cover.mp4"))
    }

    @Test fun genericOrMissingMimeUsesCaseInsensitiveExtension() {
        assertEquals(DocumentMediaType.VIDEO, DocumentMediaType.from("application/octet-stream", "Movie.MKV"))
        assertEquals(DocumentMediaType.AUDIO, DocumentMediaType.from(null, "Track.MP3"))
        assertEquals(DocumentMediaType.AUDIO, DocumentMediaType.from("", "Track.opus"))
    }

    @Test fun directoriesAndNonMediaAreExcluded() {
        assertNull(DocumentMediaType.from("vnd.android.document/directory", "Music.mp3"))
        assertNull(DocumentMediaType.from(null, "notes.txt"))
        assertNull(DocumentMediaType.from(null, "mp4"))
    }
}
