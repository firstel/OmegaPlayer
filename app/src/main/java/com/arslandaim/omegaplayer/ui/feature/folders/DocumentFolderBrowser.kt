package com.arslandaim.omegaplayer.ui.feature.folders

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arslandaim.omegaplayer.data.storage.DocumentFolderStore
import com.arslandaim.omegaplayer.data.storage.DocumentMediaType
import com.arslandaim.omegaplayer.data.storage.FolderEntry
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentFolderBrowser(
    onDismiss: () -> Unit,
    onVideoClick: (String) -> Unit,
    onAudioClick: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { DocumentFolderStore(context) }
    var treeString by rememberSaveable { mutableStateOf(store.savedTree()?.toString()) }
    var path by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var entries by remember { mutableStateOf(emptyList<FolderEntry>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                store.rememberTree(uri)
                treeString = uri.toString()
                path = arrayListOf()
                refresh++
            } catch (_: SecurityException) {
                error = "Could not keep access to this folder. Please select it again."
            }
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(treeString, path, refresh) {
        entries = emptyList()
        error = null
        val tree = treeString?.let(Uri::parse) ?: return@LaunchedEffect
        loading = true
        try {
            entries = store.list(tree, path.lastOrNull()?.let(Uri::parse))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Cannot read this folder. Reinsert the SD card, retry, or select the folder again."
        } finally {
            loading = false
        }
    }
    fun goBack() {
        if (path.isEmpty()) onDismiss() else path = ArrayList(path.dropLast(1))
    }
    Dialog(onDismissRequest = ::goBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = ::goBack)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("SD card folders") },
                    navigationIcon = { TextButton(onClick = ::goBack) { Text(if (path.isEmpty()) "Close" else "Up") } }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Text("Choose a folder on your SD card using Android’s picker. If the card root is unavailable, choose Music, Movies, or another subfolder.")
                Row {
                    TextButton(onClick = { picker.launch(treeString?.let(Uri::parse)) }) {
                        Text(if (treeString == null) "Select folder" else "Change folder")
                    }
                    if (treeString != null) TextButton(onClick = { refresh++ }) { Text("Refresh") }
                }
                if (treeString != null) Text(
                    Uri.decode(path.lastOrNull()?.let(Uri::parse)?.lastPathSegment
                        ?: Uri.parse(treeString).lastPathSegment.orEmpty()),
                    style = MaterialTheme.typography.labelMedium
                )
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (!loading && error == null && treeString != null && entries.isEmpty()) {
                    Text("No audio, video, or subfolders found.")
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.uri.toString() }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.name) },
                            supportingContent = { Text(if (entry.isDirectory) "Folder" else if (entry.mediaType == DocumentMediaType.AUDIO) "Audio" else "Video") },
                            modifier = Modifier.clickable(enabled = !loading) {
                                if (entry.isDirectory) {
                                    path = ArrayList(path + entry.uri.toString())
                                } else {
                                    // Match the existing library callback's encoded URI contract.
                                    val encoded = URLEncoder.encode(entry.uri.toString(), StandardCharsets.UTF_8.toString())
                                    if (entry.mediaType == DocumentMediaType.AUDIO) onAudioClick(encoded)
                                    else onVideoClick(encoded)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
