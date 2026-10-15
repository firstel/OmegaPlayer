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
    private var controls: PlayerControlView? = null
    internal val attachedPlayer: Player? get() = controls?.player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        val density = context.resources.displayMetrics.density
        val padding = (24 * density).toInt()
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(android.graphics.Color.rgb(20, 20, 20))
        }
        panel.addView(TextView(context).apply {
            text = context.getString(R.string.second_screen_title)
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })
        controls = PlayerControlView(context).apply {
            player = playback
            setShowTimeoutMs(0)
            setAnimationEnabled(false)
            setShowRewindButton(true)
            setShowFastForwardButton(true)
            setShowPreviousButton(false)
            setShowNextButton(false)
        }
        panel.addView(controls, LinearLayout.LayoutParams(-1, -2))
        panel.addView(TextView(context).apply {
            text = context.getString(R.string.second_screen_hint)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.LTGRAY)
        })
        setContentView(panel)
    }

    override fun onStop() {
        controls?.player = null
        super.onStop()
    }
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
                presentation = PlaybackControlsPresentation(activity, display, player).also { it.show() }
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
        } else enabled = !enabled
    }) {
        Text(stringResource(if (active) R.string.second_screen_disable else R.string.second_screen_enable),
            color = androidx.compose.ui.graphics.Color.White)
    }
}
