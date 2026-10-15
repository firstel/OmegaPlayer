package com.arslandaim.omegaplayer.ui

import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.view.Surface
import android.widget.ImageButton
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
    @Test fun secondDisplayControlsSharePlayerAndDetachOnDismiss() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val texture = SurfaceTexture(0).apply { setDefaultBufferSize(800, 600) }
                val surface = Surface(texture)
                val manager = activity.getSystemService(DisplayManager::class.java)
                val display = manager.createVirtualDisplay("Omega controls test", 800, 600, 160,
                    surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
                val player = ExoPlayer.Builder(activity).build()
                var panel: PlaybackControlsPresentation? = null
                try {
                    assertNotEquals(activity.window.decorView.display.displayId, display.display.displayId)
                    panel = PlaybackControlsPresentation(activity, display.display, player)
                    panel.show()
                    // PlayerControlView is itself the content controller; locate the built-in play control.
                    val play = panel.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_play_pause)
                    assertNotNull(play)
                    player.playWhenReady = false
                    play.performClick()
                    assertTrue(player.playWhenReady)
                    play.performClick()
                    assertFalse(player.playWhenReady)
                    panel.dismiss()
                    assertFalse(panel.isShowing)
                } finally {
                    panel?.dismiss()
                    player.release()
                    display.release()
                    surface.release()
                    texture.release()
                }
            }
        }
    }
}
