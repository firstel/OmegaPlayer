package com.arslandaim.omegaplayer.ui.feature.library

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arslandaim.omegaplayer.data.RecentPlayback
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

/** Probe the saved URI itself: MediaStore, SAF and private vault paths need different access. */
internal fun canReadHistoryMedia(context: Context, value: String): Boolean {
    return try {
        val uri = Uri.parse(value)
        when (uri.scheme) {
            "content" -> context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            "file", null -> File(uri.path ?: return false).let { it.isFile && it.canRead() }
            else -> false
        }
    } catch (_: SecurityException) { false }
      catch (_: IOException) { false }
      catch (_: IllegalArgumentException) { false }
}

internal class HistoryAccessState(
    private val scope: CoroutineScope,
    private val readable: suspend (String) -> Boolean
) {
    var items by mutableStateOf<List<RecentPlayback>>(emptyList())
        private set
    private var refresh: Job? = null

    fun refresh(source: List<RecentPlayback>) {
        refresh?.cancel()
        // Never show an unchecked button while a refresh is in progress.
        items = emptyList()
        refresh = scope.launch {
            items = source.filter { ensureActive(); readable(it.uri) }
        }
    }

    fun open(item: RecentPlayback, onAvailable: () -> Unit) {
        scope.launch {
            // It may have been deleted or the SD card removed since the last scan.
            if (readable(item.uri)) onAvailable()
            else items = items.filterNot { it.uri == item.uri }
        }
    }
}

@Composable
internal fun rememberHistoryAccess(source: List<RecentPlayback>): HistoryAccessState {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val state = remember(context, scope) {
        HistoryAccessState(scope) { uri -> withContext(Dispatchers.IO) { canReadHistoryMedia(context, uri) } }
    }
    val currentSource by rememberUpdatedState(source)
    LaunchedEffect(source) { state.refresh(source) }
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh(currentSource)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}
