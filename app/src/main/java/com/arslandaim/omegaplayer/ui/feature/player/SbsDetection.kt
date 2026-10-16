package com.arslandaim.omegaplayer.ui.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlin.math.*
import kotlin.random.Random

/** Try up to three small interior frames; stop as soon as stereo detail matches. */
internal fun detectSbsVideo(context: Context, uri: Uri): Boolean {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val fraction = Random.nextDouble(.2, .4)
        val times = if (duration > 10) listOf(fraction, fraction + .3, .85)
            .map { (duration * it).toLong() } else listOf(0L)
        times.any { time ->
            val frame = if (Build.VERSION.SDK_INT >= 27) retriever.getScaledFrameAtTime(
                time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180)
            else retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame == null) false else try { similarStereoHalves(frame) } finally { frame.recycle() }
        }
    } catch (_: Exception) { false }
    finally { runCatching { retriever.release() } }
}

/** Match local detail so varying stereo depth, brightness and small vertical offsets are tolerated. */
internal fun similarStereoHalves(source: Bitmap): Boolean {
    if (source.width < source.height || source.width < 32) return false
    val frame = Bitmap.createScaledBitmap(source, 128, 64, true)
    try {
        val pixels = IntArray(128 * 64)
        frame.getPixels(pixels, 0, 128, 0, 0, 128, 64)
        val gray = DoubleArray(pixels.size) { i ->
            val c = pixels[i]
            (c shr 16 and 255) * .299 + (c shr 8 and 255) * .587 + (c and 255) * .114
        }
        var textured = 0
        var matched = 0
        for (top in listOf(8, 20, 32, 44)) for (left in listOf(8, 20, 32, 44)) {
            var sum = 0.0; var square = 0.0; var detail = 0.0
            for (y in top until top + 12) for (x in left until left + 12) {
                val v = gray[y * 128 + x]
                sum += v; square += v * v
                // Smooth gradients and letterbox bars are not useful stereo evidence.
                detail += abs(v - gray[y * 128 + x - 1]) + abs(v - gray[(y - 1) * 128 + x])
            }
            val variance = square - sum * sum / 144
            if (variance / 144 < 70 || detail / 288 < 3) continue
            textured++
            var best = 0.0
            for (dy in -2..2) for (dx in -10..10) {
                if (left + dx < 0 || left + dx + 12 > 64) continue
                var other = 0.0; var otherSquare = 0.0; var product = 0.0
                for (y in top until top + 12) for (x in left until left + 12) {
                    val a = gray[y * 128 + x]
                    val b = gray[(y + dy) * 128 + 64 + x + dx]
                    other += b; otherSquare += b * b; product += a * b
                }
                val otherVariance = otherSquare - other * other / 144
                if (otherVariance / 144 < 70) continue
                best = max(best, (product - sum * other / 144) / sqrt(variance * otherVariance))
            }
            if (best >= .78) matched++
        }
        return textured >= 6 && matched >= 6 && matched.toDouble() / textured >= .60
    } finally { if (frame !== source) frame.recycle() }
}
