package com.arslandaim.omegaplayer.ui

import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import com.arslandaim.omegaplayer.ui.feature.player.*
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackInputRoutingTest {
    @Test fun dpadIsHandledBeforeTheInterfaceCanConsumeFocusKeys() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario -> scenario.onActivity { activity ->
            val player=ExoPlayer.Builder(activity).build()
            val state=Vr180State()
            var reachedInterface=false
            val original=activity.window.callback
            activity.window.callback=object: Window.Callback by original {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean { reachedInterface=true; return true }
            }
            val pad=GamepadPlaybackControls(player,state,action={ if(it==KeyEvent.KEYCODE_DPAD_LEFT) state.look(-15f,0f) }) { false }
            try {
                player.setMediaItem(MediaItem.fromUri("file:///routing.mp4"))
                installPlaybackInputRouting(activity.window) { pad }
                assertTrue(activity.window.callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT)))
                assertEquals(-15f,state.yaw,.001f); assertFalse(reachedInterface)
                activity.window.callback.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP))
                assertTrue(reachedInterface)
            } finally { activity.window.callback=original; pad.reset(); player.release() }
        } }
    }
}
