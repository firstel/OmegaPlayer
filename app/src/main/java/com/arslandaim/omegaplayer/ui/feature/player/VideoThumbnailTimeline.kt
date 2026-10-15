package com.arslandaim.omegaplayer.ui.feature.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.arslandaim.omegaplayer.R
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Small in-memory previews; no media copies or thumbnail files are written. */
internal class VideoThumbnailTimeline(context: Context, private val onSeek: (Long) -> Unit) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    private var generation = 0
    private var closed = false
    private var key: Pair<Uri?, Long>? = null
    private val images = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()
    private var positions = emptyList<Long>()

    init {
        orientation = HORIZONTAL
        val density = resources.displayMetrics.density
        repeat(6) { index ->
            val cell = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = (48 * density).toInt()
                setPadding((2 * density).toInt(), 0, (2 * density).toInt(), 0)
                isFocusable = true
                setOnClickListener { positions.getOrNull(index)?.let(onSeek) }
            }
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.DKGRAY)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val label = TextView(context).apply {
                textSize = 10f; gravity = Gravity.CENTER; setTextColor(Color.LTGRAY)
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            cell.addView(image, LinearLayout.LayoutParams(-1, (36 * density).toInt()))
            cell.addView(label, LinearLayout.LayoutParams(-1, -2))
            addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            images += image
            labels += label
        }
    }

    fun load(uri: Uri?, duration: Long) {
        if (closed || key == (uri to duration)) return
        key = uri to duration
        val request = ++generation
        task?.cancel(true)
        positions = if (uri != null && duration > 0) List(6) { duration * it / 6 } else emptyList()
        images.forEach { it.setImageDrawable(null) }
        labels.forEachIndexed { index, label ->
            val position = positions.getOrNull(index)
            val stamp = position?.let { java.lang.String.format(java.util.Locale.getDefault(),
                "%d:%02d", it / 60_000, (it / 1000) % 60) }.orEmpty()
            label.text = stamp
            getChildAt(index).apply {
                isEnabled = position != null
                contentDescription = context.getString(R.string.bottom_preview_at, stamp)
            }
        }
        if (uri == null || positions.isEmpty()) return
        val requestedPositions = positions.toList()
        task = executor.submit {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context.applicationContext, uri)
                requestedPositions.forEachIndexed { index, position ->
                    if (Thread.currentThread().isInterrupted) return@submit
                    val frame = timelineFrame(retriever, position) ?: return@forEachIndexed
                    handler.post {
                        if (!closed && generation == request) images[index].setImageBitmap(frame)
                        else frame.recycle()
                    }
                }
            } catch (_: Exception) {
                // Playback stays available if the provider/codec cannot produce previews.
            } finally { runCatching { retriever.release() } }
        }
    }

    fun close() {
        closed = true
        generation++
        task?.cancel(true)
        executor.shutdownNow()
        images.forEach { it.setImageDrawable(null) }
    }
}

internal fun timelineFrame(retriever: MediaMetadataRetriever, positionMs: Long): Bitmap? {
    if (Build.VERSION.SDK_INT >= 27) return retriever.getScaledFrameAtTime(
        positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 90)
    val original = retriever.getFrameAtTime(positionMs * 1000,
        MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
    val scaled = Bitmap.createScaledBitmap(original, 160, 90, true)
    if (scaled !== original) original.recycle()
    return scaled
}
