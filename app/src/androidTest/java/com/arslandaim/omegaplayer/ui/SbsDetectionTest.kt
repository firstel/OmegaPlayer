package com.arslandaim.omegaplayer.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.arslandaim.omegaplayer.ui.feature.player.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SbsDetectionTest {
    @Test fun recognizesMatchingEyesWithDisparityButRejectsUnrelatedAndBlankFrames() {
        val image=Bitmap.createBitmap(256,128,Bitmap.Config.ARGB_8888)
        val random=Random(27)
        val texture=IntArray(128*128) { Color.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256)) }
        try {
            for(y in 0 until 128) for(x in 0 until 128) {
                image.setPixel(x,y,texture[y*128+x])
                image.setPixel(128+x,y,texture[y*128+(x-4).coerceAtLeast(0)])
            }
            assertTrue(similarStereoHalves(image))
            for(y in 0 until 128) for(x in 128 until 256) image.setPixel(x,y,Color.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256)))
            assertFalse(similarStereoHalves(image))
            image.eraseColor(Color.BLACK)
            assertFalse(similarStereoHalves(image))
            image.eraseColor(Color.GRAY)
            assertFalse(similarStereoHalves(image))
        } finally { image.recycle() }
    }
    @Test fun manualChoiceAndNewVideoIgnoreOldDetection() {
        val state=Vr180State()
        val first=state.startVideo()
        state.toggleSbs()
        state.detectedSbs(first,false)
        assertTrue(state.enabled)
        val second=state.startVideo()
        state.detectedSbs(first,true)
        assertFalse(state.enabled)
        state.detectedSbs(second,true)
        assertTrue(state.enabled)
    }
    @Test fun unreadableFileFallsBackToNormalPlayback() {
        assertFalse(detectSbsVideo(InstrumentationRegistry.getInstrumentation().targetContext,
            Uri.parse("file:///missing-sbs-test.mp4")))
    }
}
