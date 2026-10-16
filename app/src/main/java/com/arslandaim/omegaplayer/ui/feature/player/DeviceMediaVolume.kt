package com.arslandaim.omegaplayer.ui.feature.player

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/** The same media stream used by the hardware volume buttons. Never raises volume on startup. */
internal class DeviceMediaVolume(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private var previousLevel = 0
    val maximum: Int get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val level: Int get() = if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) 0
        else audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    val fraction: Float get() = level.toFloat() / maximum
    val percent: Int get() = (fraction * 100).roundToInt()
    val muted: Boolean get() = level == 0

    fun adjust(up: Boolean) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
    }
    fun setFraction(value: Float) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,
            (value.coerceIn(0f, 1f) * maximum).roundToInt(), 0)
    }
    fun toggleMute() {
        if (muted) audio.setStreamVolume(AudioManager.STREAM_MUSIC,
            previousLevel.takeIf { it > 0 } ?: (maximum / 3).coerceAtLeast(1), 0)
        else {
            previousLevel = level
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        }
    }
}
