/*
 * OmegaPlayer Project Original (2026)
 * arslandaim-hub (GitHub.com/arslandaim-hub)
 * Licenced Under GPL-3.0+
*/

package com.arslandaim.omegaplayer.ui.feature.locker

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arslandaim.omegaplayer.R
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.arslandaim.omegaplayer.data.LockerDatabase
import com.arslandaim.omegaplayer.data.LockerSettings
import com.arslandaim.omegaplayer.util.SecurityUtils
import com.arslandaim.omegaplayer.ui.feature.library.components.formatDuration
import com.arslandaim.omegaplayer.ui.feature.settings.authenticateBiometric
import com.arslandaim.omegaplayer.ui.common.ModernLoadingDialog
import com.arslandaim.omegaplayer.viewmodel.LockerViewModel
import com.arslandaim.omegaplayer.viewmodel.PinState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun LockerScreen(
    viewModel: LockerViewModel,
    onBack: () -> Unit, 
    onVideoClick: (String) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    isFocused: Boolean = true
) {
    val expandedFolder by viewModel.expandedFolder.collectAsStateWithLifecycle()
    
    // Unified robust back handling for Locker
    BackHandler(enabled = isFocused) {
        if (expandedFolder != null) {
            viewModel.setExpandedFolder(null)
        } else {
            onBack()
        }
    }
    
    val context = LocalContext.current
    val pinState by viewModel.pinState.collectAsStateWithLifecycle()
    val lockedVideos by viewModel.lockedVideos.collectAsStateWithLifecycle()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Crossfade(targetState = pinState, label = "locker_fade") { state ->
            when (state) {
                PinState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                PinState.NotSet -> {
                    LockerSetupScreen(viewModel, bottomPadding)
                }
                PinState.Required -> {
                    LockerAuthScreen(viewModel, onBack, bottomPadding)
                }
                PinState.Unlocked -> {
                    LockedVideosList(
                        viewModel = viewModel,
                        videos = lockedVideos,
                        onBack = onBack,
                        onVideoClick = onVideoClick,
                        onUnlock = { video ->
                            // Video unlocking logic
                        },
                        onRefresh = { viewModel.fetchLockedVideos() },
                        bottomPadding = bottomPadding
                    )
                }
            }
        }
    }
}

@Composable
fun LockerAuthScreen(
    viewModel: LockerViewModel, 
    onBack: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    LockerPinInput(
        modifier = Modifier.statusBarsPadding().padding(bottom = bottomPadding),
        verify = { submitted ->
            val settings = viewModel.getSettings()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                settings?.pin == SecurityUtils.hashString(submitted)
            }
        },
        onUnlocked = { viewModel.unlock(true) },
        onIncorrect = { Toast.makeText(context, "Incorrect PIN", Toast.LENGTH_SHORT).show() },
        onBiometric = {
            scope.launch {
                val settings = viewModel.getSettings()
                if (settings?.isBiometricEnabled == true && activity != null) {
                    authenticateBiometric(activity, { viewModel.unlock(true) }, { errorCode, error ->
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON)
                            Toast.makeText(context, "Authentication failed: $error", Toast.LENGTH_SHORT).show()
                    })
                } else Toast.makeText(context, "Biometric unlock not enabled in Settings", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@Composable
fun NumButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isAction = label == "Back" || label == "OK"
    Surface(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        shape = CircleShape,
        color = if (isAction) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            when (label) {
                "Back" -> {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete last digit")
                }
                "OK" -> {
                    Icon(Icons.Default.Check, contentDescription = "Unlock")
                }
                else -> {
                    Text(
                        label, 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockerSetupScreen(
    viewModel: LockerViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var recoveryStep by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val setupScroll = rememberScrollState()
    LaunchedEffect(recoveryStep) { setupScroll.scrollTo(0) }
    BackHandler(enabled = recoveryStep) {
        focusManager.clearFocus()
        keyboard?.hide()
        recoveryStep = false
    }

    BoxWithConstraints(Modifier.fillMaxSize().imePadding().padding(bottom = bottomPadding)) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        val introduction: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Create private vault", style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                Text(if (recoveryStep) "Step 2 of 2 · Recovery" else "Step 1 of 2 · PIN",
                    color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.vault_storage_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val form: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (recoveryStep) R.string.vault_setup_recovery else R.string.vault_setup_pin),
                    style = MaterialTheme.typography.titleMedium)
                if (!recoveryStep) {
                    OutlinedTextField(value = pin,
                        onValueChange = { pin = it.filter { digit -> digit in '0'..'9' }.take(4) },
                        label = { Text(stringResource(R.string.pin_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = confirmation,
                        onValueChange = { confirmation = it.filter { digit -> digit in '0'..'9' }.take(4) },
                        label = { Text("Confirm PIN") }, singleLine = true,
                        isError = confirmation.length == 4 && confirmation != pin,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = question, onValueChange = { question = it },
                        label = { Text(stringResource(R.string.security_question_title)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = answer, onValueChange = { answer = it },
                        label = { Text(stringResource(R.string.your_answer_label)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (recoveryStep) TextButton(onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        recoveryStep = false
                    }) { Text("Back") }
                    Button(onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        if (!recoveryStep) recoveryStep = true
                        else {
                            saving = true
                            viewModel.saveSettings(LockerSettings(pin = pin,
                                securityQuestion = question.trim(), securityAnswer = answer.trim())) { success ->
                                saving = false
                                if (!success) Toast.makeText(context, "Could not save vault settings. Please try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, enabled = !saving && (if (recoveryStep) question.isNotBlank() && answer.isNotBlank()
                        else pin.length == 4 && pin == confirmation),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(if (recoveryStep) R.string.vault_setup_create else R.string.vault_setup_next))
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().verticalScroll(setupScroll).padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { introduction() }
                Box(Modifier.weight(1f).widthIn(max = 480.dp)) { key(recoveryStep) { form() } }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(setupScroll)
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 480.dp).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                introduction()
                key(recoveryStep) { form() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedVideosList(
    viewModel: LockerViewModel,
    videos: List<com.arslandaim.omegaplayer.data.LockedVideo>,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,
    onUnlock: (com.arslandaim.omegaplayer.data.LockedVideo) -> Unit,
    onRefresh: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val expandedFolder by viewModel.expandedFolder.collectAsStateWithLifecycle()

    // Robust Back Handling: Prioritize closing expanded folder over exiting locker
    // MOVED TO LockerScreen

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { LockerDatabase.getDatabase(context).lockerDao() }
    var isProcessing by remember { mutableStateOf(false) }
    
    // Group videos by folder origin
    val groupedVideos = remember(videos) {
        videos.groupBy { it.originFolderName }
    }
    
    if (isProcessing) {
        ModernLoadingDialog()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (expandedFolder != null) {
                            IconButton(onClick = { viewModel.setExpandedFolder(null) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Text(
                            if (expandedFolder != null) expandedFolder!! else "Private Vault", 
                            fontSize = 24.sp, 
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                    Icons.Default.ReplayCircleFilled, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.secondary
                )
                    }
                }
            )
        }
    ) { padding ->
        if (videos.isEmpty()) {
            EmptyVaultState()
        } else {
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(padding)
            
            if (expandedFolder != null) {
                // Show videos in the selected folder
                val folderVideos = groupedVideos[expandedFolder] ?: emptyList()
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = contentModifier,
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(folderVideos) { video ->
                        VaultItem(
                            video = video,
                            onClick = { onVideoClick(video.lockerPath) },
                            onUnlock = {
                                scope.launch {
                                    isProcessing = true
                                    val success = unlockVideo(context, video, dao)
                                    isProcessing = false
                                    if (success) {
                                        Toast.makeText(context, "Restored to Gallery", Toast.LENGTH_SHORT).show()
                                        onRefresh()
                                    }
                                }
                            }
                        )
                    }
                }
            } else {
                // Show folders and individual videos
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(160.dp),
                    modifier = contentModifier,
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + bottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Folders Section
                    val folderNames = groupedVideos.keys.filterNotNull()
                    if (folderNames.isNotEmpty()) {
                        items(folderNames) { folderName ->
                            VaultFolderItem(
                                name = folderName,
                                count = groupedVideos[folderName]?.size ?: 0,
                                onClick = { viewModel.setExpandedFolder(folderName) },
                                onUnlock = {
                                    scope.launch {
                                        isProcessing = true
                                        val folderVideos = groupedVideos[folderName] ?: emptyList()
                                        var successCount = 0
                                        folderVideos.forEach { video ->
                                            if (unlockVideo(context, video, dao)) successCount++
                                        }
                                        isProcessing = false
                                        if (successCount > 0) {
                                            Toast.makeText(context, "Restored $successCount videos to Gallery", Toast.LENGTH_SHORT).show()
                                            onRefresh()
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 2. Individual Videos Section
                    val individualVideos = groupedVideos[null] ?: emptyList()
                    items(individualVideos) { video ->
                        VaultItem(
                            video = video,
                            onClick = { onVideoClick(video.lockerPath) },
                            onUnlock = {
                                scope.launch {
                                    isProcessing = true
                                    val success = unlockVideo(context, video, dao)
                                    isProcessing = false
                                    if (success) {
                                        Toast.makeText(context, "Restored to Gallery", Toast.LENGTH_SHORT).show()
                                        onRefresh()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultFolderItem(
    name: String,
    count: Int,
    onClick: () -> Unit,
    onUnlock: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count videos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = { onUnlock() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = "Unlock Folder",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun VaultItem(
    video: com.arslandaim.omegaplayer.data.LockedVideo,
    onClick: () -> Unit,
    onUnlock: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(video.lockerPath))
                        .apply {
                            if (!video.isAudio) {
                                decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                                videoFrameMillis(1000)
                            }
                        }
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = rememberVectorPainter(if (video.isAudio) Icons.Default.MusicNote else Icons.Default.Movie),
                    fallback = rememberVectorPainter(if (video.isAudio) Icons.Default.MusicNote else Icons.Default.Movie)
                )
                
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                IconButton(
                    onClick = onUnlock,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Icon(Icons.Default.LockOpen, "Unlock", tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyVaultState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.VpnKey,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.locker_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
