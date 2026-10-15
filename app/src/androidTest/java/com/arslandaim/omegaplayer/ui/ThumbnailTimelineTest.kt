package com.arslandaim.omegaplayer.ui

import android.content.ContentValues
import android.graphics.drawable.BitmapDrawable
import android.provider.MediaStore
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.MainActivity
import com.arslandaim.omegaplayer.ui.feature.player.VideoThumbnailTimeline
import org.junit.Assert.*
import org.junit.Test

class ThumbnailTimelineTest {
    @Test fun contentVideoLoadsSixPreviewsAndThumbnailTapSeeks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Omega timeline test.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/OmegaTimelineTest")
        })!!
        try {
            instrumentation.context.assets.open("timeline-test.mp4").use { input ->
                resolver.openOutputStream(uri)!!.use { output -> input.copyTo(output) }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var timeline: VideoThumbnailTimeline
                var seekPosition = -1L
                scenario.onActivity { activity ->
                    timeline = VideoThumbnailTimeline(activity) { seekPosition = it }
                    timeline.load(uri, 2_000)
                    assertEquals(6, timeline.childCount)
                    timeline.getChildAt(3).performClick()
                    assertEquals(1_000L, seekPosition)
                }
                try {
                    var loaded = 0
                    val deadline = System.currentTimeMillis() + 10_000
                    while (loaded < 6 && System.currentTimeMillis() < deadline) {
                        instrumentation.runOnMainSync {
                            loaded = (0 until 6).count { index ->
                                val image = (timeline.getChildAt(index) as LinearLayout).getChildAt(0) as ImageView
                                image.drawable is BitmapDrawable
                            }
                        }
                        if (loaded < 6) Thread.sleep(50)
                    }
                    assertEquals(6, loaded)
                    scenario.onActivity {
                        timeline.load(null, 0)
                        assertFalse(timeline.getChildAt(0).isEnabled)
                    }
                } finally { instrumentation.runOnMainSync { timeline.close() } }
            }
        } finally { resolver.delete(uri, null, null) }
    }
}
