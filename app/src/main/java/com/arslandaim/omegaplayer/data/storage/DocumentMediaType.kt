package com.arslandaim.omegaplayer.data.storage

import java.util.Locale

/** Providers may label playable containers as application types instead of audio/video. */
internal enum class DocumentMediaType { AUDIO, VIDEO;
    companion object {
        fun from(mimeType: String?, name: String): DocumentMediaType? {
            val mime = mimeType?.trim()?.lowercase(Locale.ROOT)
            if (mime?.startsWith("audio/") == true) return AUDIO
            if (mime?.startsWith("video/") == true) return VIDEO
            if (!mime.isNullOrBlank() && !mime.startsWith("application/")) return null
            return when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "aiff", "oga", "aif", "alac", "amr", "ac3", "dts" -> AUDIO
                "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp", "ts", "mpeg", "mpg", "wmv", "flv", "vob", "m2ts", "mts", "3g2", "divx", "rm", "rmvb", "ogv", "m2v", "mpe" -> VIDEO
                else -> null
            }
        }
    }
}
