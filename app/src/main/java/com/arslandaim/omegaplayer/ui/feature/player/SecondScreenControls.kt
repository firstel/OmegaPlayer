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
import com.arslandaim.omegaplayer.media.playbackUri
import com.arslandaim.omegaplayer.R
import androidx.compose.ui.res.stringResource

/** A controller only: the existing video surface and audio session stay on the main screen. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class PlaybackControlsPresentation(
    activity: Activity,
    display: Display,
    private val playback: Player,
    private val onVideoClosing: () -> Unit = {},
    private val onVideoClosed: () -> Unit = {},
    private val vr180: Vr180State? = null,
    private val gamepad: GamepadPlaybackControls? = null,
    private val controls: PlayerControlActions? = null
) : Presentation(activity, display, android.R.style.Theme_Material_NoActionBar) {
    private var attached = false
    internal val attachedPlayer: Player? get() = if (attached) playback else null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var progress: android.widget.SeekBar
    private lateinit var time: TextView
    private lateinit var audioStatus: TextView
    private lateinit var play: android.widget.Button
    private lateinit var mute: android.widget.Button
    private lateinit var speed: android.widget.Button
    private lateinit var volumeLabel: TextView
    private lateinit var timeline: VideoThumbnailTimeline
    private var scrubbing = false
    private val vrLabels = mutableListOf<Pair<android.widget.Button, () -> String>>()
    private val deviceVolume = DeviceMediaVolume(activity)
    private val ticker = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 250) }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { refresh() }
    }
    private fun refresh() {
        if (!::progress.isInitialized) return
        vrLabels.forEach { (button, label) -> button.text = label() }
        val duration = playback.duration.coerceAtLeast(0)
        if (!scrubbing) progress.progress = if (duration > 0)
            ((playback.currentPosition.coerceAtLeast(0).toDouble() / duration) * 1000).toInt() else 0
        progress.isEnabled = duration > 0 && playback.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        fun stamp(ms: Long): String {
            val seconds = ms.coerceAtLeast(0) / 1000
            return java.lang.String.format(java.util.Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
        }
        time.text = "${stamp(playback.currentPosition)} / ${stamp(duration)}"
        play.text = if (playback.playWhenReady && playback.playbackState != Player.STATE_ENDED) "Ⅱ Pause\nA" else "▶ Play\nA"
        mute.text = if (deviceVolume.muted) "♪ Unmute\nStart" else "♪ Mute\nStart"
        speed.text = context.getString(R.string.bottom_speed, playback.playbackParameters.speed) + "\nTouch"
        volumeLabel.text = context.getString(R.string.bottom_volume_level, deviceVolume.percent)
        timeline.load(playback.currentMediaItem?.playbackUri(), duration)
        val audioGroups = playback.currentTracks.groups.filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
        val message = if (playback.playbackState != Player.STATE_READY) null
            else if (audioGroups.isEmpty()) context.getString(R.string.audio_missing_track)
            else if (audioGroups.none { group -> (0 until group.length).any { group.isTrackSupported(it) } })
                context.getString(R.string.audio_unsupported_track)
            else if (audioGroups.none { it.isSelected }) context.getString(R.string.audio_disabled_track)
            else null
        audioStatus.text = message
        audioStatus.visibility = if (message == null) android.view.View.INVISIBLE else android.view.View.VISIBLE
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setVolumeControlStream(android.media.AudioManager.STREAM_MUSIC)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val vr = vr180 ?: Vr180State()
        val sensors = context.getSystemService(android.hardware.SensorManager::class.java)
        vr.motionAvailable = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR) != null ||
            sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR) != null
        val actions = controls ?: PlayerControlActions(playback, deviceVolume, vr, onVideoClosing) { dismiss(); onVideoClosed() }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(android.graphics.Color.rgb(20,20,20))
        }
        val header = LinearLayout(context)
        time = TextView(context).apply { textSize=14f; setTextColor(android.graphics.Color.WHITE) }
        volumeLabel = TextView(context).apply {
            id=R.id.bottom_volume_level; textSize=14f; gravity=Gravity.END; setTextColor(android.graphics.Color.LTGRAY)
        }
        header.addView(time, LinearLayout.LayoutParams(0,-1,1f))
        header.addView(volumeLabel, LinearLayout.LayoutParams(0,-1,1f))
        panel.addView(header,LinearLayout.LayoutParams(-1,dp(24)))
        audioStatus=TextView(context).apply {
            textSize=11f; setTextColor(android.graphics.Color.YELLOW); isSingleLine=true
            ellipsize=android.text.TextUtils.TruncateAt.END
        }
        panel.addView(audioStatus,LinearLayout.LayoutParams(-1,dp(16)))
        timeline=VideoThumbnailTimeline(context) { position ->
            if(playback.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) playback.seekTo(position)
        }.apply { id=R.id.bottom_timeline }
        panel.addView(timeline,LinearLayout.LayoutParams(-1,dp(52)))
        progress=android.widget.SeekBar(context).apply {
            max=1000
            setOnSeekBarChangeListener(object: android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: android.widget.SeekBar) { scrubbing=true }
                override fun onProgressChanged(bar: android.widget.SeekBar,value: Int,fromUser: Boolean) {}
                override fun onStopTrackingTouch(bar: android.widget.SeekBar) {
                    if(playback.duration>0) playback.seekTo((playback.duration.toDouble()*bar.progress/1000).toLong())
                    scrubbing=false; refresh()
                }
            })
        }
        panel.addView(progress,LinearLayout.LayoutParams(-1,dp(32)))
        fun row()=LinearLayout(context).apply {
            orientation=LinearLayout.HORIZONTAL
            panel.addView(this,LinearLayout.LayoutParams(-1,0,1f))
        }
        fun button(parent: LinearLayout,id: Int,label: ()->String,description: String,action: ()->Unit): android.widget.Button {
            return android.widget.Button(context).apply {
                this.id=id; text=label(); contentDescription=description
                isAllCaps=false; textSize=12f; gravity=Gravity.CENTER
                minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0
                setPadding(dp(2),0,dp(2),0); maxLines=2
                ellipsize=android.text.TextUtils.TruncateAt.END
                vrLabels.add(this to label)
                setOnClickListener { action(); refresh() }
                parent.addView(this,LinearLayout.LayoutParams(0,-1,1f))
            }
        }
        fun keyButton(parent: LinearLayout,id: Int,label: ()->String,description: String,key: Int)=
            button(parent,id,label,description) { actions.perform(key) }
        val transport=row()
        button(transport,R.id.bottom_seek_back,{ "◀ −10s\nL2" },"Back 10 seconds, L2") {
            playback.seekTo(skipPosition(playback.currentPosition,playback.duration,-10000))
        }
        play=keyButton(transport,R.id.bottom_play_pause,{ if(playback.playWhenReady && playback.playbackState != Player.STATE_ENDED) "Ⅱ Pause\nA" else "▶ Play\nA" },"Play or pause, A",android.view.KeyEvent.KEYCODE_BUTTON_A)
        button(transport,R.id.bottom_seek_forward,{ "+10s ▶\nR2" },"Forward 10 seconds, R2") {
            playback.seekTo(skipPosition(playback.currentPosition,playback.duration,10000))
        }
        keyButton(transport,R.id.bottom_stop,{ "■ Stop\nB" },"Stop video, B",android.view.KeyEvent.KEYCODE_BUTTON_B)
        keyButton(transport,R.id.bottom_close,{ "× Close\nX" },"Close video, X",android.view.KeyEvent.KEYCODE_BUTTON_X)
        val audio=row()
        keyButton(audio,R.id.bottom_volume_down,{ "− Volume\nL1" },"Volume down, L1",android.view.KeyEvent.KEYCODE_BUTTON_L1)
        mute=keyButton(audio,R.id.bottom_mute,{ if(deviceVolume.muted) "♪ Unmute\nStart" else "♪ Mute\nStart" },"Mute or unmute, Start",android.view.KeyEvent.KEYCODE_BUTTON_START)
        keyButton(audio,R.id.bottom_volume_up,{ "+ Volume\nR1" },"Volume up, R1",android.view.KeyEvent.KEYCODE_BUTTON_R1)
        speed=button(audio,R.id.bottom_speed,{ context.getString(R.string.bottom_speed,playback.playbackParameters.speed)+"\nTouch" },"Playback speed, touch") {
            val speeds=listOf(.5f,.75f,1f,1.25f,1.5f,2f)
            playback.setPlaybackSpeed(speeds.firstOrNull { it>playback.playbackParameters.speed+.01f } ?: speeds.first())
        }
        val vrRow=row()
        keyButton(vrRow,R.id.bottom_vr,{ if(vr.enabled) "SBS On\nL3" else "SBS Off\nL3" },"Toggle VR180 SBS, left stick click L3",android.view.KeyEvent.KEYCODE_BUTTON_THUMBL)
        keyButton(vrRow,R.id.bottom_eye,{ if(vr.rightEye) "Right eye\nSelect" else "Left eye\nSelect" },"Change source eye, Select",android.view.KeyEvent.KEYCODE_BUTTON_SELECT)
        keyButton(vrRow,R.id.bottom_recenter,{ "◎ Center\nY" },"Recenter view and zoom, Y",android.view.KeyEvent.KEYCODE_BUTTON_Y)
        keyButton(vrRow,R.id.bottom_motion,{ if(!vr.motionAvailable) "No motion\nR3" else if(vr.motion) "Motion On\nR3" else "Motion Off\nR3" },"Toggle motion tracking, right stick click R3",android.view.KeyEvent.KEYCODE_BUTTON_THUMBR).isEnabled=vr.motionAvailable
        val look=row()
        keyButton(look,R.id.bottom_look_left,{ "← Look\nD-pad ←" },"Look left, D-pad left",android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        keyButton(look,R.id.bottom_look_up,{ "↑ Look\nD-pad ↑" },"Look up, D-pad up",android.view.KeyEvent.KEYCODE_DPAD_UP)
        keyButton(look,R.id.bottom_look_down,{ "↓ Look\nD-pad ↓" },"Look down, D-pad down",android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        keyButton(look,R.id.bottom_look_right,{ "Look →\nD-pad →" },"Look right, D-pad right",android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        button(look,R.id.bottom_zoom_out,{ "− Zoom\nRS ↓" },"Zoom out, right stick down") { if(vr.enabled) vr.zoomBy(-.25f) }
        button(look,R.id.bottom_zoom_in,{ "+ Zoom\nRS ↑" },"Zoom in, right stick up") { if(vr.enabled) vr.zoomBy(.25f) }
        panel.addView(TextView(context).apply {
            text="Left stick: look   ·   Right stick: zoom"; textSize=11f
            gravity=Gravity.CENTER; setTextColor(android.graphics.Color.LTGRAY)
        },LinearLayout.LayoutParams(-1,dp(16)))
        setContentView(panel)
        refresh()
    }
    override fun onStart() {
        super.onStart()
        attached = true
        playback.addListener(listener)
        handler.post(ticker)
    }
    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean =
        gamepad?.motion(event) == true || super.dispatchGenericMotionEvent(event)
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean =
        gamepad?.key(event) == true || super.dispatchKeyEvent(event)
    override fun onStop() {
        gamepad?.reset()
        handler.removeCallbacks(ticker)
        playback.removeListener(listener)
        attached = false
        timeline.close()
        super.onStop()
    }
}

internal fun skipPosition(position: Long, duration: Long, offset: Long): Long =
    (position.coerceAtLeast(0) + offset).coerceAtLeast(0).let {
        if (duration > 0) it.coerceAtMost(duration) else it
    }

@Composable
internal fun SecondScreenControlsButton(
    player: Player?, locked: Boolean, videoUri: String,
    onVideoClosing: () -> Unit, onVideoClosed: () -> Unit, vr180: Vr180State? = null, gamepad: GamepadPlaybackControls? = null, controls: PlayerControlActions? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by rememberSaveable(videoUri) { mutableStateOf(true) }
    val currentClosing by rememberUpdatedState(onVideoClosing)
    val currentClosed by rememberUpdatedState(onVideoClosed)
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
    DisposableEffect(enabled, locked, player, revision, lifecycle, vr180, gamepad, controls) {
        var presentation: PlaybackControlsPresentation? = null
        fun hide() { presentation?.dismiss(); presentation = null; active = false }
        fun show() {
            if (!enabled || locked || player == null || presentation != null) return
            val display = manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .firstOrNull { it.displayId != activity.window.decorView.display?.displayId } ?: return
            try {
                presentation = PlaybackControlsPresentation(activity, display, player,
                    onVideoClosing = { currentClosing() }, onVideoClosed = { currentClosed() }, vr180 = vr180, gamepad = gamepad, controls = controls).also { panel ->
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
        if (manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                .none { it.displayId != activity.window.decorView.display?.displayId }) {
            Toast.makeText(context, R.string.second_screen_missing, Toast.LENGTH_LONG).show()
        } else if (active) enabled = false
        else { enabled = true; revision++ }
    }) {
        Text(stringResource(if (active) R.string.second_screen_disable else R.string.second_screen_enable),
            color = androidx.compose.ui.graphics.Color.White)
    }
}
