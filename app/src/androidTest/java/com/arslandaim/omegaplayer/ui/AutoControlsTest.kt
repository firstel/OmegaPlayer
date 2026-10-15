package com.arslandaim.omegaplayer.ui

import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.view.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.ui.feature.player.SecondScreenControlsButton
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controlsOpenAutomaticallyAndReenableForNextVideo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var texture: SurfaceTexture
        lateinit var surface: Surface
        lateinit var display: android.hardware.display.VirtualDisplay
        lateinit var player: ExoPlayer
        val uri = mutableStateOf("content://test/first.mp4")
        compose.runOnIdle {
            texture = SurfaceTexture(0).apply { setDefaultBufferSize(800, 600) }
            surface = Surface(texture)
            display = context.getSystemService(DisplayManager::class.java).createVirtualDisplay(
                "Automatic controls test", 800, 600, 160, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
            player = ExoPlayer.Builder(context).build()
        }
        try {
            compose.setContent { SecondScreenControlsButton(player, false, uri.value, {}, {}) }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Turn off bottom controls").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Turn off bottom controls").performClick()
            compose.onNodeWithText("Bottom-screen controls").assertExists()
            compose.runOnIdle { uri.value = "content://test/second.mp4" }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Turn off bottom controls").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            compose.setContent { }
            compose.runOnIdle { player.release(); display.release(); surface.release(); texture.release() }
        }
    }
}
