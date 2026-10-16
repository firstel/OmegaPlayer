package com.arslandaim.omegaplayer.ui

import android.graphics.*
import android.hardware.display.DisplayManager
import android.view.*
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.R
import com.arslandaim.omegaplayer.ui.feature.player.*
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CompactControlsTest {
    @Test fun everyControlFitsShortLandscapeAndXClosesThroughSharedActions() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var panel: PlaybackControlsPresentation
            lateinit var player: ExoPlayer
            lateinit var texture: SurfaceTexture
            lateinit var surface: Surface
            lateinit var display: android.hardware.display.VirtualDisplay
            lateinit var pad: GamepadPlaybackControls
            val state=Vr180State()
            var saved=false; var closed=false
            scenario.onActivity { activity ->
                texture=SurfaceTexture(0).apply { setDefaultBufferSize(800,360) }; surface=Surface(texture)
                display=activity.getSystemService(DisplayManager::class.java).createVirtualDisplay("Compact controls",800,360,160,surface,DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
                player=ExoPlayer.Builder(activity).build().apply { setMediaItem(MediaItem.fromUri("file:///layout-test.mp4")) }
                val actions=PlayerControlActions(player,DeviceMediaVolume(activity),state,
                    onClosing={ saved=player.mediaItemCount==1 },onClosed={ closed=true; panel.dismiss() })
                pad=GamepadPlaybackControls(player,state,action={ actions.perform(it) }) { false }
                panel=PlaybackControlsPresentation(activity,display.display,player,vr180=state,gamepad=pad,controls=actions)
                panel.show()
            }
            try {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity {
                    val ids=listOf(R.id.bottom_seek_back,R.id.bottom_play_pause,R.id.bottom_seek_forward,R.id.bottom_stop,R.id.bottom_close,
                        R.id.bottom_volume_down,R.id.bottom_mute,R.id.bottom_volume_up,R.id.bottom_speed,R.id.bottom_haptics,R.id.bottom_vr,R.id.bottom_eye,
                        R.id.bottom_recenter,R.id.bottom_motion,R.id.bottom_look_left,R.id.bottom_look_up,R.id.bottom_look_down,
                        R.id.bottom_look_right,R.id.bottom_zoom_in,R.id.bottom_zoom_out)
                    ids.forEach { id ->
                        val button=panel.findViewById<View>(id)
                        val bounds=Rect()
                        assertTrue("Control $id must be visible",button.getGlobalVisibleRect(bounds))
                        assertEquals("Full button must fit vertically",button.height,bounds.height())
                        assertEquals("Full button must fit horizontally",button.width,bounds.width())
                        assertTrue("Touch height at least 48dp",button.height>=48)
                    }
                    val root=panel.window!!.decorView
                    val image=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(image))
                    val resolver=it.contentResolver
                    val capture=resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"compact-bottom-controls.png")
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/OmegaPlayerLayout")
                        })!!
                    resolver.openOutputStream(capture)!!.use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                    image.recycle()
                    assertTrue(state.enabled)
                    panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_THUMBL))
                    assertFalse(state.enabled)
                    state.zoomBy(1f); state.look(20f,20f)
                    panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_Y))
                    assertEquals(1f,state.zoom,.001f); assertEquals(0f,state.yaw,.001f)
                    state.startVideo(); assertTrue(state.enabled); assertFalse(state.motion)
                    panel.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_X))
                    assertTrue(saved); assertTrue(closed); assertEquals(0,player.mediaItemCount)
                    assertFalse(panel.isShowing)
                }
            } finally { scenario.onActivity { pad.reset(); panel.dismiss(); player.release(); display.release(); surface.release(); texture.release() } }
        }
    }
}
