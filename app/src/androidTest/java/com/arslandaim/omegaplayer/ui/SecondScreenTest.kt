package com.arslandaim.omegaplayer.ui

import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.view.Surface
import android.widget.Button
import com.arslandaim.omegaplayer.R
import androidx.media3.common.ForwardingPlayer
import com.arslandaim.omegaplayer.media.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.MainActivity
import com.arslandaim.omegaplayer.ui.feature.player.PlaybackControlsPresentation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SecondScreenTest {
    @Test fun numericVideoUriKeepsTitleTypeAndUriAcrossSessionSerialization() {
        val uri = android.net.Uri.parse("content://media/external/video/media/123")
        val item = videoMediaItem(uri, "Test movie.mp4", "video/mp4")
        val received = androidx.media3.common.MediaItem.fromBundle(item.toBundle())
        assertTrue(received.isVideoItem())
        assertEquals("Test movie.mp4", received.mediaMetadata.title.toString())
        assertEquals(uri, received.playbackUri())
        val audio = androidx.media3.common.MediaItem.Builder().setUri("content://media/external/audio/media/123")
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
                .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
        assertFalse(audio.isVideoItem())
    }

    @Test fun secondDisplayControlsSharePlayerAndDetachOnDismiss() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                org.junit.Assume.assumeFalse(activity.getSystemService(android.media.AudioManager::class.java).isVolumeFixed)
                val deviceVolume = com.arslandaim.omegaplayer.ui.feature.player.DeviceMediaVolume(activity)
                val originalVolume = deviceVolume.fraction
                val texture = SurfaceTexture(0).apply { setDefaultBufferSize(800, 600) }
                val surface = Surface(texture)
                val manager = activity.getSystemService(DisplayManager::class.java)
                val display = manager.createVirtualDisplay("Omega controls test", 800, 600, 160,
                    surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
                val engine = ExoPlayer.Builder(activity).build()
                var position = 30_000L
                val player = object : ForwardingPlayer(engine) {
                    override fun getDuration() = 60_000L
                    override fun getCurrentPosition() = position
                    override fun seekTo(positionMs: Long) { position = positionMs }
                    override fun isCommandAvailable(command: Int) = true
                }
                var panel: PlaybackControlsPresentation? = null
                try {
                    assertNotEquals(activity.window.decorView.display.displayId, display.display.displayId)
                    var closed = false
                    panel = PlaybackControlsPresentation(activity, display.display, player,
                        onVideoClosed = { closed = true })
                    deviceVolume.setFraction(0f)
                    panel.show()
                    assertEquals(0, deviceVolume.percent)
                    assertEquals("Media volume 0%", panel.findViewById<android.widget.TextView>(R.id.bottom_volume_level).text.toString())
                    deviceVolume.setFraction(1f)
                    val play = panel.findViewById<Button>(R.id.bottom_play_pause)
                    assertNotNull(play)
                    assertSame(player, panel.attachedPlayer)
                    player.playWhenReady = false
                    play.performClick()
                    assertTrue(player.playWhenReady)
                    val back = panel.findViewById<Button>(R.id.bottom_seek_back)
                    val forward = panel.findViewById<Button>(R.id.bottom_seek_forward)
                    back.performClick(); assertEquals(20_000L, position)
                    forward.performClick(); assertEquals(30_000L, position)
                    position = 4_000L
                    back.performClick(); assertEquals(0L, position)
                    position = 58_000L
                    forward.performClick(); assertEquals(60_000L, position)
                    panel.findViewById<Button>(R.id.bottom_mute).performClick()
                    assertEquals(0f, deviceVolume.fraction, 0.001f)
                    panel.findViewById<Button>(R.id.bottom_mute).performClick()
                    assertEquals(1f, deviceVolume.fraction, 0.001f)
                    panel.findViewById<Button>(R.id.bottom_volume_down).performClick()
                    assertEquals(deviceVolume.maximum - 1, deviceVolume.level)
                    panel.findViewById<Button>(R.id.bottom_volume_up).performClick()
                    assertEquals(1f, deviceVolume.fraction, 0.001f)
                    panel.findViewById<Button>(R.id.bottom_volume_up).performClick()
                    assertEquals(1f, deviceVolume.fraction, 0.001f)
                    panel.findViewById<Button>(R.id.bottom_speed).performClick()
                    assertEquals(1.25f, player.playbackParameters.speed, 0.001f)
                    panel.findViewById<Button>(R.id.bottom_stop).performClick()
                    assertEquals(0L, position)
                    assertFalse(player.playWhenReady)
                    assertEquals(androidx.media3.common.Player.STATE_IDLE, player.playbackState)
                    player.setPlaybackSpeed(2f)
                    player.volume = 0.2f
                    prepareVideoItem(player, videoMediaItem(android.net.Uri.parse("file:///test-next.mp4"),
                        "Next video", "video/mp4"))
                    assertEquals(1f, player.playbackParameters.speed, 0.001f)
                    assertEquals(1f, player.volume, 0.001f)
                    panel.findViewById<Button>(R.id.bottom_close).performClick()
                    assertTrue(closed)
                    assertEquals(0, player.mediaItemCount)
                    assertFalse(player.playWhenReady)
                    assertNull(panel.attachedPlayer)
                    assertFalse(panel.isShowing)
                } finally {
                    panel?.dismiss()
                    deviceVolume.setFraction(originalVolume)
                    player.release()
                    display.release()
                    surface.release()
                    texture.release()
                }
            }
        }
    }
}
