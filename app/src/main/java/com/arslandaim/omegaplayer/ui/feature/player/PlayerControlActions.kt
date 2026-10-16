package com.arslandaim.omegaplayer.ui.feature.player

import android.view.KeyEvent
import androidx.media3.common.Player

/** Touch and physical keys call the same actions, including progress-saving close. */
internal class PlayerControlActions(
    private val player: Player?, private val volume: DeviceMediaVolume,
    private val vr: Vr180State,
    private val feedback: (com.arslandaim.omegaplayer.ui.common.Pulse) -> Unit = {},
    private val onClosing: () -> Unit = {}, private val onClosed: () -> Unit = {}
) {
    private var closed = false
    fun perform(key: Int) {
        val p = player ?: return
        if (key !in keys || (key == KeyEvent.KEYCODE_BUTTON_X && closed)) return
        feedback(if (key == KeyEvent.KEYCODE_BUTTON_Y) com.arslandaim.omegaplayer.ui.common.Pulse.RECENTER
            else com.arslandaim.omegaplayer.ui.common.Pulse.TAP)
        when(key) {
            KeyEvent.KEYCODE_BUTTON_A -> {
                if(p.playWhenReady && p.playbackState != Player.STATE_ENDED) p.pause()
                else { if(p.playbackState == Player.STATE_ENDED) p.seekTo(0)
                    if(p.playbackState == Player.STATE_IDLE) p.prepare(); p.play() }
            }
            KeyEvent.KEYCODE_BUTTON_B -> { p.pause(); p.seekTo(0); p.stop() }
            KeyEvent.KEYCODE_BUTTON_X -> if(!closed) {
                closed = true; onClosing(); p.pause(); p.stop(); p.clearMediaItems(); onClosed()
            }
            KeyEvent.KEYCODE_BUTTON_Y -> vr.center()
            KeyEvent.KEYCODE_BUTTON_L1 -> { volume.adjust(false); p.volume=1f }
            KeyEvent.KEYCODE_BUTTON_R1 -> { volume.adjust(true); p.volume=1f }
            KeyEvent.KEYCODE_BUTTON_START -> { volume.toggleMute(); p.volume=1f }
            KeyEvent.KEYCODE_BUTTON_SELECT -> vr.rightEye = !vr.rightEye
            KeyEvent.KEYCODE_BUTTON_THUMBL -> { vr.toggleSbs() }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> if(vr.motionAvailable) { vr.motion = !vr.motion; vr.center() }
            KeyEvent.KEYCODE_DPAD_LEFT -> vr.look(-15f,0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> vr.look(15f,0f)
            KeyEvent.KEYCODE_DPAD_UP -> vr.look(0f,15f)
            KeyEvent.KEYCODE_DPAD_DOWN -> vr.look(0f,-15f)
        }
    }
    companion object {
        val keys = setOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
    }
}
