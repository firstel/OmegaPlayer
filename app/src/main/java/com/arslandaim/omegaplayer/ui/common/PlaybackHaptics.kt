package com.arslandaim.omegaplayer.ui.common

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

internal enum class Pulse { TAP, SKIP, RECENTER }

/** Global preference is shared by playback and PIN keys; no pulses during stick movement. */
internal class PlaybackHaptics(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("playback_haptics", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = preferences.getBoolean("enabled", true)
        set(value) { preferences.edit().putBoolean("enabled", value).apply() }
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else app.getSystemService(Vibrator::class.java)
    private var lastPulse = -100L
    fun pulse(kind: Pulse = Pulse.TAP) {
        if (!enabled || vibrator?.hasVibrator() != true ||
            Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPulse < 45) return
        lastPulse = now
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                val effect = if (kind == Pulse.RECENTER) VibrationEffect.createWaveform(longArrayOf(0,10,25,10),-1)
                    else VibrationEffect.createOneShot(if(kind == Pulse.SKIP) 22L else 12L,VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if(kind == Pulse.SKIP) 22L else 12L)
            }
        } catch (_: SecurityException) { /* Playback must remain usable if vibration is unavailable. */ }
    }
}
