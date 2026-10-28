package ninja.richter.soundfork

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.IconButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ninja.richter.soundfork.data.BassState
import ninja.richter.soundfork.data.EndpointResult
import ninja.richter.soundfork.data.ToneControlState
import ninja.richter.soundfork.data.VolumeState

@Composable
fun SelectedDeviceCard(
    host: String, port: Int, name: String?, type: String?, deviceId: String?, nameDraft: String,
    realtimeStatus: String?, realtimeError: String?, isRefreshing: Boolean, isRenaming: Boolean,
    onNameChanged: (String) -> Unit, onApplyName: () -> Unit, onRefresh: () -> Unit
) {
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SpeakerIllustration(Modifier.padding(top = 8.dp).size(128.dp))
        Text(name ?: stringResource(R.string.unknown_name), style = MaterialTheme.typography.titleLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(type ?: stringResource(R.string.connected_device), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ settingsOpen = !settingsOpen }) {
            TouchIcon(TouchSymbol.Settings, null, Modifier.padding(end = 8.dp).size(18.dp))
            Text(stringResource(R.string.device_settings))
        }
        if (settingsOpen) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("$host:$port", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    deviceId?.let { Text("ID: $it", style = MaterialTheme.typography.bodySmall) }
                    OutlinedTextField(nameDraft, onNameChanged, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.device_name)) }, enabled = !isRenaming,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onApplyName() }))
                    FilledTonalButton(onApplyName, enabled = !isRenaming && nameDraft.isNotBlank() && nameDraft != name.orEmpty()) {
                        Text(stringResource(if (isRenaming) R.string.saving else R.string.save_name))
                    }
                    realtimeStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedButton(onRefresh, enabled = !isRefreshing) { Text(stringResource(R.string.reload)) }
                }
            }
        }
        realtimeError?.let { StatusMessage(it, true) }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
fun VolumeControlCard(
    volumeDraft: Float, volumeState: VolumeState?, isApplying: Boolean,
    onVolumeChanged: (Float) -> Unit, onApplyVolume: () -> Unit, onToggleMute: () -> Unit
) {
    val muted = volumeState?.muteEnabled == true
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.volume), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(volumeDraft.toInt().toString(), style = MaterialTheme.typography.titleMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onToggleMute, enabled = !isApplying) {
                    TouchIcon(if (muted) TouchSymbol.Muted else TouchSymbol.Volume,
                        stringResource(if (muted) R.string.disable_mute else R.string.enable_mute))
                }
                SoundTouchSlider(volumeDraft, onVolumeChanged, Modifier.weight(1f), onValueChangeFinished = onApplyVolume,
                    valueRange = 0f..100f, enabled = !isApplying)
            }
            if (muted) Text(stringResource(R.string.mute_state, stringResource(R.string.on)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BassControlCard(
    bassDraft: Float,
    bassState: BassState?,
    isApplying: Boolean,
    onBassChanged: (Float) -> Unit,
    onApplyBass: () -> Unit
) {
    val target = bassState?.targetBass ?: bassDraft.toInt()
    val actual = bassState?.actualBass

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.bass),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            SoundTouchSlider(
                value = bassDraft,
                onValueChange = onBassChanged,
                onValueChangeFinished = onApplyBass,
                valueRange = -9f..9f,
                steps = 17,
                enabled = !isApplying
            )
            Text(
                text = stringResource(R.string.target_actual, target.toString(), actual?.toString() ?: "-"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ToneControlCard(
    toneControls: ToneControlState?,
    trebleDraft: Float,
    isApplying: Boolean,
    onTrebleChanged: (Float) -> Unit,
    onApplyTreble: () -> Unit
) {
    val treble = toneControls?.treble
    val current = treble?.value ?: trebleDraft.toInt()
    val min = treble?.minValue ?: -10
    val max = treble?.maxValue ?: 10
    val step = treble?.step ?: 1
    val sliderSteps = remember(min, max, step) {
        if (step > 0 && max > min) {
            (((max - min) / step) - 1).coerceAtLeast(0)
        } else {
            0
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.treble),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (treble == null || min >= max) {
                Text(
                    text = stringResource(R.string.treble_not_reported),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            SoundTouchSlider(
                value = trebleDraft.coerceIn(min.toFloat(), max.toFloat()),
                onValueChange = onTrebleChanged,
                onValueChangeFinished = onApplyTreble,
                valueRange = min.toFloat()..max.toFloat(),
                steps = sliderSteps,
                enabled = !isApplying
            )
            Text(
                text = stringResource(R.string.treble_current_range, current, min, max, step),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            FilledTonalButton(
                onClick = onApplyTreble,
                enabled = !isApplying
            ) {
                Text(if (isApplying) stringResource(R.string.saving) else stringResource(R.string.save_treble))
            }
        }
    }
}

@Composable
fun DynamicAudioControlsCard(endpointResults: List<EndpointResult>) {
    val audioResults = remember(endpointResults) {
        endpointResults.filter { result ->
            val path = result.resolvedPath.orEmpty().lowercase()
            path.contains("audio") || path.contains("tone") || path.contains("level")
        }
    }

    if (audioResults.isEmpty()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.dynamic_audio_controls),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.dynamic_audio_controls_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            audioResults.forEachIndexed { index, result ->
                AudioEndpointPreview(result = result)
                if (index < audioResults.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun AudioEndpointPreview(result: EndpointResult) {
    val preview = remember(result.body) {
        result.body
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(420)
            .orEmpty()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = result.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "${result.resolvedPath ?: result.requestedAliases.firstOrNull().orEmpty()} | HTTP ${result.httpCode ?: "n/a"}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (preview.isNotBlank()) {
            Text(
                text = preview,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 96.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = result.errorMessage ?: stringResource(R.string.no_response_data),
                style = MaterialTheme.typography.bodySmall,
                color = if (result.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

private const val TAG = "SoundForkUI"
