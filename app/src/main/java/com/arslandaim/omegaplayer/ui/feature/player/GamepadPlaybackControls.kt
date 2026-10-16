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
    private val directions = intArrayOf(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN)
    private val hat = BooleanArray(4)
    private val dpadKeys = BooleanArray(4)
    private val dpadPressed = BooleanArray(4)
    private fun direction(index: Int) {
        val down = hat[index] || dpadKeys[index]
        if(down && !dpadPressed[index] && active()) action(directions[index])
        dpadPressed[index] = down
    }
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
        hat.fill(false); dpadKeys.fill(false); dpadPressed.fill(false)
    }
    internal fun step(seconds: Float) {
        if (!active()) { reset(); return }
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
        val directionIndex = directions.indexOf(event.keyCode)
        if(directionIndex >= 0) {
            if(player?.currentMediaItem == null) return false
            if(event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) dpadKeys[directionIndex]=true
            if(event.action == KeyEvent.ACTION_UP) dpadKeys[directionIndex]=false
            direction(directionIndex)
            return true
        }
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
        val joystick=event.isFromSource(InputDevice.SOURCE_JOYSTICK)
        val padSource=event.isFromSource(InputDevice.SOURCE_DPAD) || event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if ((!joystick && !padSource) || event.action != MotionEvent.ACTION_MOVE || player?.currentMediaItem == null) return false
        if (locked()) { reset(); return true }
        val hx=event.getAxisValue(MotionEvent.AXIS_HAT_X).let { if(!joystick && it==0f) event.getAxisValue(MotionEvent.AXIS_X) else it }
        val hy=event.getAxisValue(MotionEvent.AXIS_HAT_Y).let { if(!joystick && it==0f) event.getAxisValue(MotionEvent.AXIS_Y) else it }
        hat[0]=hx < -.5f; hat[1]=hx > .5f; hat[2]=hy < -.5f; hat[3]=hy > .5f
        for(index in 0..3) direction(index)
        if(!joystick) return true
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

/** Intercept before Compose/native focus navigation consumes D-pad keys. */
internal fun installPlaybackInputRouting(window: android.view.Window, controls: () -> GamepadPlaybackControls?) {
    val previous=window.callback
    window.callback=object: android.view.Window.Callback by previous {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            controls()?.key(event) == true || previous.dispatchKeyEvent(event)
        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
            controls()?.motion(event) == true || previous.dispatchGenericMotionEvent(event)
    }
}
