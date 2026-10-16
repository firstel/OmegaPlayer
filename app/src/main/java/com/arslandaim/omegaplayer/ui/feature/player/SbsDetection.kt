package com.arslandaim.omegaplayer.ui.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlin.math.*
import kotlin.random.Random

/** A single small, random interior frame. Failure leaves ordinary playback available. */
internal fun detectSbsVideo(context: Context, uri: Uri): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val time = if (duration > 10) Random.nextLong(duration / 10, duration * 9 / 10) else 0L
        val frame = if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(
            time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
        else retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        if (frame == null) false else try { similarStereoHalves(frame) } finally { frame.recycle() }
    } catch (_: Exception) { false }
    finally { runCatching { retriever.release() } }
}

/** Conservative stereo heuristic: textured matching eyes, allowing modest horizontal disparity. */
internal fun similarStereoHalves(source: Bitmap): Boolean {
    if (source.width < source.height || source.width < 32) return false
    val frame = Bitmap.createScaledBitmap(source, 128, 64, true)
    try {
        val pixels = IntArray(128 * 64)
        frame.getPixels(pixels, 0, 128, 0, 0, 128, 64)
        fun luma(x: Int, y: Int): Double {
            val c = pixels[y * 128 + x]
            return ((c shr 16 and 255) * .299 + (c shr 8 and 255) * .587 + (c and 255) * .114)
        }
        for (shift in -4..4) {
            var a=0.0; var b=0.0; var aa=0.0; var bb=0.0; var ab=0.0; var error=0.0; var n=0
            for (y in 4 until 60) for (x in 8 until 56) {
                val left=luma(x,y); val right=luma(64+x+shift,y)
                a+=left; b+=right; aa+=left*left; bb+=right*right; ab+=left*right
                error+=abs(left-right); n++
            }
            val va=aa-a*a/n; val vb=bb-b*b/n
            // Blank, dark and nearly uniform frames cannot establish stereo content.
            if (va/n < 180 || vb/n < 180) continue
            val correlation=(ab-a*b/n)/sqrt(va*vb)
            if (correlation > .90 && error/n < 28) return true
        }
        return false
    } finally { if (frame !== source) frame.recycle() }
}
