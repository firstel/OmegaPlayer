package com.arslandaim.omegaplayer.ui.feature.player

import android.app.Activity
import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerControlView
import com.arslandaim.omegaplayer.R
import androidx.compose.ui.res.stringResource

/** A controller only: the existing video surface and audio session stay on the main screen. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackControlsPresentation(
    activity: Activity,
    display: Display,
    private val playback: Player
) : Presentation(activity, display, android.R.style.Theme_Material_NoActionBar) {
    private var attached = false
    internal val attachedPlayer: Player? get() = if (attached) playback else null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progress: android.widget.SeekBar
    private lateinit var time: TextView
    private lateinit var play: android.widget.Button
    private lateinit var mute: android.widget.Button
    private lateinit var speed: android.widget.Button
    private var scrubbing = false
    private var previousVolume = 1f
    private val ticker = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 250) }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refresh() }
    }
    private fun refresh() {
        if (!::progress.isInitialized) return
        val duration = playback.duration.coerceAtLeast(0)
        if (!scrubbing) progress.progress = if (duration > 0)
            ((playback.currentPosition.coerceAtLeast(0).toDouble() / duration) * 1000).toInt() else 0
        progress.isEnabled = duration > 0 && playback.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        fun stamp(ms: Long): String {
            val seconds = ms.coerceAtLeast(0) / 1000
            return java.lang.String.format(java.util.Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
        }
        time.text = "${stamp(playback.currentPosition)} / ${stamp(duration)}"
        play.setText(if (playback.playWhenReady && playback.playbackState != Player.STATE_ENDED)
            R.string.bottom_pause else R.string.bottom_play)
        mute.setText(if (playback.volume == 0f) R.string.bottom_unmute else R.string.bottom_mute)
        speed.text = context.getString(R.string.bottom_speed, playback.playbackParameters.speed)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(android.graphics.Color.rgb(20, 20, 20))
        }
        panel.addView(TextView(context).apply {
            text = context.getString(R.string.second_screen_title)
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })
        time = TextView(context).apply {
            textSize = 16f; gravity = Gravity.CENTER; setTextColor(android.graphics.Color.LTGRAY)
        }
        panel.addView(time)
        progress = android.widget.SeekBar(context).apply {
            max = 1000
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) { scrubbing = true }
                override fun onProgressChanged(bar: android.widget.SeekBar, value: Int, fromUser: Boolean) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {
                    val duration = playback.duration
                    if (duration > 0) playback.seekTo((duration.toDouble() * bar.progress / 1000).toLong())
                    scrubbing = false; refresh()
                }
            })
        }
        panel.addView(progress, LinearLayout.LayoutParams(-1, dp(40)))
        fun row(): LinearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            panel.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        fun button(row: LinearLayout, id: Int, label: Int, action: () -> Unit): android.widget.Button {
            return android.widget.Button(context).apply {
                this.id = id; setText(label); isAllCaps = false; textSize = 16f
                minHeight = dp(52)
                setOnClickListener { action(); refresh() }
                row.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }
        val transport = row()
        button(transport, R.id.bottom_seek_back, R.string.bottom_back_ten) {
            playback.seekTo(skipPosition(playback.currentPosition, playback.duration, -10_000))
        }
        play = button(transport, R.id.bottom_play_pause, R.string.bottom_play) {
            if (playback.playWhenReady && playback.playbackState != Player.STATE_ENDED) playback.pause()
            else {
                if (playback.playbackState == Player.STATE_ENDED) playback.seekTo(0)
                if (playback.playbackState == Player.STATE_IDLE) playback.prepare()
                playback.play()
            }
        }
        button(transport, R.id.bottom_seek_forward, R.string.bottom_forward_ten) {
            playback.seekTo(skipPosition(playback.currentPosition, playback.duration, 10_000))
        }
        val options = row()
        button(options, R.id.bottom_stop, R.string.bottom_stop) {
            playback.pause(); playback.seekTo(0); playback.stop()
        }
        mute = button(options, R.id.bottom_mute, R.string.bottom_mute) {
            if (playback.volume > 0f) { previousVolume = playback.volume; playback.volume = 0f }
            else playback.volume = previousVolume
        }
        speed = button(options, R.id.bottom_speed, R.string.bottom_play) {
            val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
            playback.setPlaybackSpeed(speeds.firstOrNull { it > playback.playbackParameters.speed + 0.01f } ?: speeds.first())
        }
        button(row(), R.id.bottom_close, R.string.bottom_close) { cancel() }
        val scroll = android.widget.ScrollView(context).apply {
            isFillViewport = true
            addView(panel, android.widget.ScrollView.LayoutParams(-1, -1))
        }
        setContentView(scroll)
        refresh()
    }
    override fun onStart() {
        super.onStart()
        attached = true
        playback.addListener(listener)
        handler.post(ticker)
    }
    override fun onStop() {
        handler.removeCallbacks(ticker)
        playback.removeListener(listener)
        attached = false
        super.onStop()
    }
}

internal fun skipPosition(position: Long, duration: Long, offset: Long): Long =
    (position.coerceAtLeast(0) + offset).coerceAtLeast(0).let {
        if (duration > 0) it.coerceAtMost(duration) else it
    }

@Composable
internal fun SecondScreenControlsButton(player: Player?, locked: Boolean) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by rememberSaveable { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val manager = remember(context) { context.getSystemService(DisplayManager::class.java) }
    DisposableEffect(manager) {
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) { revision++ }
            override fun onDisplayRemoved(id: Int) { revision++ }
            override fun onDisplayChanged(id: Int) { revision++ }
        }
        manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager.unregisterDisplayListener(listener) }
    }
    DisposableEffect(enabled, locked, player, revision, lifecycle) {
        var presentation: PlaybackControlsPresentation? = null
        fun hide() { presentation?.dismiss(); presentation = null; active = false }
        fun show() {
            if (!enabled || locked || player == null || presentation != null) return
            val display = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull { it.displayId != activity.window.decorView.display?.displayId } ?: return
            try {
                presentation = PlaybackControlsPresentation(activity, display, player).also { panel ->
                    panel.setOnCancelListener { enabled = false }
                    panel.setOnDismissListener { active = false }
                    panel.show()
                }
                active = true
            } catch (_: WindowManager.InvalidDisplayException) { hide() }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> show()
                Lifecycle.Event.ON_STOP -> hide()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) show()
        onDispose { lifecycle.removeObserver(observer); hide() }
    }
    if (!locked) TextButton(onClick = {
        if (!enabled && manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .none { it.displayId != activity.window.decorView.display?.displayId }) {
            Toast.makeText(context, R.string.second_screen_missing, Toast.LENGTH_LONG).show()
        } else enabled = !active
    }) {
        Text(stringResource(if (active) R.string.second_screen_disable else R.string.second_screen_enable),
            color = androidx.compose.ui.graphics.Color.White)
    }
}
