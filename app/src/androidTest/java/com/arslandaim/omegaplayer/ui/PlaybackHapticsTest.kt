package com.arslandaim.omegaplayer.ui

import android.view.KeyEvent
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.ui.common.Pulse
import com.arslandaim.omegaplayer.ui.feature.player.*
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackHapticsTest {
    @Test fun discreteActionsRequestBriefPulsesAndNewVideosEnableSbs() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val player=ExoPlayer.Builder(instrumentation.targetContext).build()
            val state=Vr180State()
            val pulses=mutableListOf<Pulse>()
            var closed=false
            val actions=PlayerControlActions(player,DeviceMediaVolume(instrumentation.targetContext),state,
                feedback={ pulses.add(it) },onClosed={ closed=true })
            try {
                player.setMediaItem(MediaItem.fromUri("file:///haptic-test.mp4"))
                state.enabled=false; state.motion=true; state.rightEye=true; state.zoomBy(1f)
                state.startVideo()
                assertFalse(state.enabled); assertFalse(state.motion); assertFalse(state.rightEye)
                assertEquals(1f,state.zoom,.001f)
                actions.perform(KeyEvent.KEYCODE_BUTTON_Y)
                actions.perform(KeyEvent.KEYCODE_BUTTON_B)
                actions.perform(KeyEvent.KEYCODE_BUTTON_X)
                actions.perform(KeyEvent.KEYCODE_BUTTON_X)
                assertTrue(closed)
                assertEquals(listOf(Pulse.RECENTER,Pulse.TAP,Pulse.TAP),pulses)
            } finally { player.release() }
        }
    }
}
