package com.arslandaim.omegaplayer.ui.feature.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.max

/** Shared by the main window and the lower Presentation, whichever currently has focus. */
internal class GamepadPlaybackControls(
    private val player: Player?,
    private val view: Vr180State,
    private val action: (Int) -> Unit = {},
    private val feedback: () -> Unit = {},
    private val locked: () -> Boolean
) {
    private val handler = Handler(Looper.getMainLooper())
    private var x = 0f
    private var y = 0f
    private var zoom = 0f
    private val analog = booleanArrayOf(false, false)
    private val digital = booleanArrayOf(false, false)
    private val pressed = booleanArrayOf(false, false)
    private var ticking = false
    private var lastTime = 0L
    private val ticker = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            step(((now - lastTime) / 1000f).coerceIn(0f, .05f))
            lastTime = now
            if (ticking) handler.postDelayed(this, 16)
        }
    }
    private fun active() = player?.currentMediaItem != null && !locked()
    fun reset() {
        handler.removeCallbacks(ticker); ticking = false
        x = 0f; y = 0f; zoom = 0f
        analog.fill(false); digital.fill(false); pressed.fill(false)
    }
    internal fun step(seconds: Float) {
        if (!active() || !view.enabled) { reset(); return }
        view.look(x * 70f * seconds, -y * 60f * seconds)
        view.zoomBy(-zoom * seconds)
    }
    private fun trigger(index: Int) {
        val down = analog[index] || digital[index]
        if (down && !pressed[index] && active() && player!!.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            player.seekTo(skipPosition(player.currentPosition, player.duration, if(index == 0) -10000 else 10000))
            feedback()
        }
        pressed[index] = down
    }
    fun key(event: KeyEvent): Boolean {
        if (event.keyCode in PlayerControlActions.keys) {
            if (player?.currentMediaItem == null) return false
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && !locked()) action(event.keyCode)
            return true
        }
        val index = when(event.keyCode) { KeyEvent.KEYCODE_BUTTON_L2 -> 0; KeyEvent.KEYCODE_BUTTON_R2 -> 1; else -> return false }
        if (player?.currentMediaItem == null) return false
        if (event.action == KeyEvent.ACTION_UP) digital[index] = false
        else if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) digital[index] = true
        trigger(index)
        return true
    }
    fun motion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE || player?.currentMediaItem == null) return false
        if (locked()) { reset(); return true }
        fun axis(id: Int): Float {
            val flat = max(.18f, event.device?.getMotionRange(id, event.source)?.flat ?: 0f).coerceAtMost(.95f)
            val value = event.getAxisValue(id)
            return if (abs(value) <= flat) 0f else ((abs(value)-flat)/(1f-flat)).coerceIn(0f,1f) * if(value < 0) -1f else 1f
        }
        fun triggerAxis(primary: Int, fallback: Int): Float = max(event.getAxisValue(primary), event.getAxisValue(fallback))
        val levels = floatArrayOf(triggerAxis(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE),
            triggerAxis(MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS))
        for(index in 0..1) {
            if(levels[index] >= .6f) analog[index] = true
            else if(levels[index] <= .3f) analog[index] = false
            trigger(index)
        }
        if (!view.enabled) return levels.any { it > .3f }
        x = axis(MotionEvent.AXIS_X); y = axis(MotionEvent.AXIS_Y)
        // Some built-in/virtual controllers report values without declaring all axis ranges.
        zoom = if(event.device?.getMotionRange(MotionEvent.AXIS_RZ, event.source) != null ||
            event.getAxisValue(MotionEvent.AXIS_RZ) != 0f)
            axis(MotionEvent.AXIS_RZ) else axis(MotionEvent.AXIS_RY)
        if(x != 0f || y != 0f || zoom != 0f) {
            if(!ticking) { ticking = true; lastTime = SystemClock.uptimeMillis(); handler.post(ticker) }
        } else { handler.removeCallbacks(ticker); ticking = false }
        return true
    }
}
