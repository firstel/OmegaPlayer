package com.arslandaim.omegaplayer.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.ui.feature.player.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class Vr180Test {
    @Test fun hemisphereMappingAndLookLimits() {
        assertEquals(.25f, vr180Uv(0f, 0f, false)!!.first, .001f)
        assertEquals(.75f, vr180Uv(0f, 0f, true)!!.first, .001f)
        assertEquals(0f, vr180Uv(-90f, 0f, false)!!.first, .001f)
        assertEquals(.5f, vr180Uv(90f, 0f, false)!!.first, .001f)
        assertNull(vr180Uv(100f, 0f, false))
        val state = Vr180State()
        state.look(200f, -200f)
        assertEquals(89f, state.yaw, .001f)
        assertEquals(-85f, state.pitch, .001f)
        state.center()
        assertEquals(0f, state.yaw, .001f)
        assertEquals(0f, state.pitch, .001f)
    }

    @Test fun decodedSideBySideFramesRenderTheSelectedEye() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "vr180-check.mp4")
        instrumentation.context.assets.open("vr180-test.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                lateinit var view: Vr180View
                lateinit var player: ExoPlayer
                val state = Vr180State().apply { enabled = true }
                scenario.onActivity { activity ->
                    view = Vr180View(activity, state)
                    activity.setContentView(view)
                    player = ExoPlayer.Builder(activity).build().apply {
                        repeatMode = Player.REPEAT_MODE_ONE
                        setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(file)))
                        prepare(); play()
                    }
                    view.bind(player)
                }
                fun waitForEye(right: Boolean) {
                    var matched = false
                    val deadline = System.currentTimeMillis() + 15000
                    while (!matched && System.currentTimeMillis() < deadline) {
                        val bitmap = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
                        val latch = CountDownLatch(1)
                        var result = -1
                        scenario.onActivity {
                            try { PixelCopy.request(view, bitmap, { code -> result=code; latch.countDown() }, Handler(Looper.getMainLooper())) }
                            catch (_: IllegalArgumentException) { latch.countDown() }
                        }
                        assertTrue(latch.await(3,TimeUnit.SECONDS))
                        if(result == PixelCopy.SUCCESS) {
                            val color=bitmap.getPixel(16,16)
                            matched=if(right) Color.blue(color)>150 && Color.red(color)<80
                                else Color.red(color)>150 && Color.blue(color)<80
                        }
                        bitmap.recycle()
                        if(!matched) Thread.sleep(50)
                    }
                    assertTrue("Decoded VR180 view must show the selected ${if(right) "right/blue" else "left/red"} eye", matched)
                }
                try {
                    waitForEye(false)
                    scenario.onActivity { state.rightEye=true; view.bind(player) }
                    waitForEye(true)
                    scenario.onActivity { view.onPause(); view.onResume() }
                    waitForEye(true)
                } finally { scenario.onActivity { view.close(); player.release() } }
            }
        } finally { file.delete() }
    }
}
