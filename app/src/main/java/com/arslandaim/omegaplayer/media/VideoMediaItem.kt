package com.arslandaim.omegaplayer.media

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** Explicit metadata survives the MediaSession boundary, including numeric content URIs. */
internal fun videoMediaItem(uri: Uri, title: String, mimeType: String?): MediaItem = MediaItem.Builder()
    .setUri(uri)
    .setMediaId(uri.toString())
    .setMimeType(mimeType?.takeIf { it.startsWith("video/") })
    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
    .setMediaMetadata(MediaMetadata.Builder().setTitle(title)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MOVIE).build())
    .build()

internal fun MediaItem.playbackUri(): Uri? = localConfiguration?.uri ?: requestMetadata.mediaUri

internal fun MediaItem.isVideoItem(): Boolean {
    if (mediaMetadata.mediaType in listOf(MediaMetadata.MEDIA_TYPE_MOVIE, MediaMetadata.MEDIA_TYPE_VIDEO,
            MediaMetadata.MEDIA_TYPE_TV_SHOW)) return true
    if (localConfiguration?.mimeType?.startsWith("video/") == true) return true
    val uri = playbackUri()?.toString()?.lowercase().orEmpty()
    if (uri.contains("/video/media/")) return true
    return listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "flv", "ts")
        .any { uri.substringBefore('?').endsWith(".$it") }
}

internal fun prepareVideoItem(player: androidx.media3.common.Player, item: MediaItem) {
    player.setPlaybackSpeed(1f)
    player.volume = 1f
    player.setMediaItem(item)
    player.prepare()
}
