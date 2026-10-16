package com.arslandaim.omegaplayer.ui

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.ui.feature.player.GamepadPlaybackControls
import com.arslandaim.omegaplayer.ui.feature.player.Vr180State
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class GamepadPlaybackTest {
    private fun motion(x: Float = 0f, y: Float = 0f, rz: Float = 0f, lt: Float = 0f, rt: Float = 0f): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_UNKNOWN }
        val coords = MotionEvent.PointerCoords().apply {
            setAxisValue(MotionEvent.AXIS_X,x); setAxisValue(MotionEvent.AXIS_Y,y)
            setAxisValue(MotionEvent.AXIS_RZ,rz); setAxisValue(MotionEvent.AXIS_LTRIGGER,lt)
            setAxisValue(MotionEvent.AXIS_RTRIGGER,rt)
        }
        return MotionEvent.obtain(0,0,MotionEvent.ACTION_MOVE,1,arrayOf(properties),arrayOf(coords),
            0,0,1f,1f,-1,0,InputDevice.SOURCE_JOYSTICK,0)
    }
    @Test fun sticksTriggersDeadZoneAndLockUseTheSharedPlayer() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val engine=ExoPlayer.Builder(instrumentation.targetContext).build()
            var position=30000L
            var seeks=0
            val player=object: ForwardingPlayer(engine) {
                override fun getCurrentMediaItem()=MediaItem.fromUri("file:///test.mp4")
                override fun getCurrentPosition()=position
                override fun getDuration()=60000L
                override fun isCommandAvailable(command: Int)=true
                override fun seekTo(positionMs: Long) { position=positionMs; seeks++ }
            }
            val state=Vr180State().apply { enabled=true }
            var locked=false
            val controls=GamepadPlaybackControls(player,state) { locked }
            fun send(event: MotionEvent) { try { assertTrue(controls.motion(event)) } finally { event.recycle() } }
            try {
                send(motion(x=.1f,y=-.1f,rz=.1f)); controls.step(1f)
                assertEquals(0f,state.yaw,.001f); assertEquals(1f,state.zoom,.001f)
                send(motion(x=1f,y=-1f,rz=-1f)); controls.step(.5f)
                assertTrue("Left stick moves yaw", state.yaw>0)
                assertTrue("Left stick moves pitch", state.pitch>0)
                assertTrue("Right stick zooms even without range metadata", state.zoom>1f)
                send(motion()); val yaw=state.yaw; val zoom=state.zoom
                controls.step(.5f); assertEquals(yaw,state.yaw,.001f); assertEquals(zoom,state.zoom,.001f)
                send(motion(rt=1f)); assertEquals(40000L,position)
                send(motion(rt=1f)); assertEquals(1,seeks)
                controls.key(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_R2))
                assertEquals(1,seeks) // Same physical press reported as both analog and digital.
                controls.key(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R2)); send(motion())
                controls.key(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_L2))
                assertEquals(30000L,position)
                controls.key(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_L2))
                position=5000; send(motion(lt=1f)); assertEquals(0L,position)
                send(motion()); position=58000; send(motion(rt=1f)); assertEquals(60000L,position)
                send(motion()); locked=true; val before=seeks
                send(motion(x=1f,rt=1f)); controls.step(1f)
                assertEquals(before,seeks); assertEquals(yaw,state.yaw,.001f)
                controls.reset(); state.center(); assertEquals(1f,state.zoom,.001f)
            } finally { controls.reset(); engine.release() }
        }
    }
}
