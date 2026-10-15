package com.arslandaim.omegaplayer.data.storage

import java.util.Locale

/** MIME is authoritative; extension fallback is only for providers without a useful MIME. */
internal enum class DocumentMediaType { AUDIO, VIDEO;
    companion object {
        fun from(mimeType: String?, name: String): DocumentMediaType? {
            val mime = mimeType?.lowercase(Locale.ROOT)
            if (mime?.startsWith("audio/") == true) return AUDIO
            if (mime?.startsWith("video/") == true) return VIDEO
            if (!mime.isNullOrBlank() && mime != "application/octet-stream") return null
            return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "aiff" -> AUDIO
                "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "ts", "mpeg", "mpg" -> VIDEO
                else -> null
            }
        }
    }
}
