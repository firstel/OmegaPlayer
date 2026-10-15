package com.arslandaim.omegaplayer.ui.feature.locker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arslandaim.omegaplayer.R

/** Key size is bounded by height, never stretched to one third of a landscape screen. */
@Composable
internal fun LockerPinPanel(
    pinLength: Int,
    onKey: (String) -> Unit,
    onBiometric: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth >= 480.dp && maxWidth > maxHeight
        val keySize = if (landscape) ((maxHeight - 72.dp) / 4).coerceIn(48.dp, 64.dp) else 64.dp
        val heading: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Lock, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.locker_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.enter_pin_sub), style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 12.dp).semantics { stateDescription = "$pinLength of 4 digits entered" }) {
                    repeat(4) { index ->
                        Box(Modifier.size(14.dp).clip(CircleShape).background(
                            if (index < pinLength) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                    }
                }
                FilledTonalIconButton(onClick = onBiometric, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Fingerprint, "Fingerprint unlock")
                }
            }
        }
        val keypad: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Back", "0", "OK")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { key -> NumButton(key, Modifier.size(keySize)) { onKey(key) } }
                    }
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)) {
                Box(Modifier.weight(1f).widthIn(max = 360.dp), contentAlignment = Alignment.Center) { heading() }
                keypad()
                Spacer(Modifier.width(12.dp))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
                heading()
                keypad()
            }
        }
    }
}

/** Editing cancels an outstanding verification; stale results must never unlock the vault. */
@Composable
internal fun LockerPinInput(
    verify: suspend (String) -> Boolean,
    onUnlocked: () -> Unit,
    onIncorrect: () -> Unit,
    onBiometric: () -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var verification by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentVerify by rememberUpdatedState(verify)
    val currentUnlocked by rememberUpdatedState(onUnlocked)
    val currentIncorrect by rememberUpdatedState(onIncorrect)

    fun submit() {
        if (checking || input.length != 4) return
        val submitted = input
        val submittedRevision = revision
        checking = true
        verification = scope.launch {
            try {
                val correct = currentVerify(submitted)
                if (revision == submittedRevision) {
                    if (correct) currentUnlocked()
                    else { input = ""; currentIncorrect() }
                }
            } finally {
                if (revision == submittedRevision) checking = false
            }
        }
    }

    Box(modifier) {
        LockerPinPanel(input.length, onKey = { key ->
            when (key) {
                "Back" -> {
                    revision++
                    verification?.cancel()
                    checking = false
                    input = input.dropLast(1)
                }
                "OK" -> submit()
                else -> if (!checking && input.length < 4) {
                    input += key
                    revision++
                    if (input.length == 4) submit()
                }
            }
        }, onBiometric = onBiometric)
        if (checking) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(12.dp).size(24.dp))
    }
}
