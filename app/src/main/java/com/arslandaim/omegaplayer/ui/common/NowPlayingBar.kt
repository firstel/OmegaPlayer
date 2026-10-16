/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.common

import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import com.arslandaim.omegaplayer.media.isVideoItem
import com.arslandaim.omegaplayer.media.playbackUri
import com.arslandaim.omegaplayer.media.PlaybackConnection

@Composable
fun NowPlayingBar(
    playbackConnection: PlaybackConnection,
    onAudioClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMediaItem by playbackConnection.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying by playbackConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaController by playbackConnection.mediaController.collectAsStateWithLifecycle()

    val isVideo = remember(currentMediaItem) {
        currentMediaItem?.isVideoItem() == true
    }

    AnimatedVisibility(
        visible = currentMediaItem != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val item = currentMediaItem ?: return@AnimatedVisibility
        val uriString = item.playbackUri()?.toString() ?: ""

        if (isVideo) {
            VideoMiniPlayer(
                mediaItem = item,
                isPlaying = isPlaying,
                mediaController = mediaController,
                onExpandClick = {
                    if (uriString.isNotEmpty()) {
                        onVideoClick(uriString)
                    }
                },
                onCloseClick = {
                    mediaController?.stop()
                    mediaController?.clearMediaItems()
                }
            )
        } else {
            AudioMiniPlayer(
                mediaItem = item,
                isPlaying = isPlaying,
                mediaController = mediaController,
                onBarClick = {
                    if (uriString.isNotEmpty()) {
                        onAudioClick(uriString)
                    }
                },
                onCloseClick = {
                    mediaController?.stop()
                    mediaController?.clearMediaItems()
                }
            )
        }
    }
}
