package com.arslandaim.omegaplayer.ui

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.activity.ComponentActivity
import com.arslandaim.omegaplayer.media.prepareVideoItem
import com.arslandaim.omegaplayer.media.videoMediaItem
import com.arslandaim.omegaplayer.service.createPlaybackPlayer
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioPlaybackTest {
    @Test fun videoWithAacAudioSelectsAndRendersAudioUsingServiceConfiguration() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "audio-playback-check.mp4")
        instrumentation.context.assets.open("audio-test.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var player: ExoPlayer
                scenario.onActivity {
                    player = createPlaybackPlayer(it)
                    player.volume = 0f
                    prepareVideoItem(player, videoMediaItem(Uri.fromFile(file), "Audio check", "video/mp4"))
                    player.play()
                    assertEquals(1f, player.volume, 0.001f)
                }
                try {
                    var rendered = 0
                    var selected = false
                    var error: String? = null
                    val deadline = System.currentTimeMillis() + 15_000
                    while (rendered == 0 && error == null && System.currentTimeMillis() < deadline) {
                        scenario.onActivity {
                            player.audioDecoderCounters?.ensureUpdated()
                            rendered = player.audioDecoderCounters?.renderedOutputBufferCount ?: 0
                            selected = player.currentTracks.groups.any { group -> group.type == C.TRACK_TYPE_AUDIO && group.isSelected }
                            error = player.playerError?.toString()
                        }
                        if (rendered == 0) Thread.sleep(50)
                    }
                    assertNull(error)
                    assertTrue("AAC audio track must be selected", selected)
                    assertTrue("AAC decoder must render audio buffers", rendered > 0)
                } finally { scenario.onActivity { player.release() } }
            }
        } finally { file.delete() }
    }
}
