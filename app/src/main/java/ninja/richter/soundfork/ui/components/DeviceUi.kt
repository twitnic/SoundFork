package ninja.richter.soundfork

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    host: String,
    port: Int,
    name: String?,
    type: String?,
    deviceId: String?,
    nameDraft: String,
    realtimeStatus: String?,
    realtimeError: String?,
    isRefreshing: Boolean,
    isRenaming: Boolean,
    onNameChanged: (String) -> Unit,
    onApplyName: () -> Unit,
    onRefresh: () -> Unit
) {
    val tag = TAG
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.connected_device),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = name ?: stringResource(R.string.unknown_name),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Host: $host:$port",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
            if (type != null) {
                Text(stringResource(R.string.type_prefix, type), style = MaterialTheme.typography.bodySmall)
            }
            if (deviceId != null) {
                Text("Device ID: $deviceId", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = nameDraft,
                onValueChange = onNameChanged,
                label = { Text(stringResource(R.string.device_name)) },
                singleLine = true,
                enabled = !isRenaming,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onApplyName() })
            )
            FilledTonalButton(
                onClick = onApplyName,
                enabled = !isRenaming && nameDraft.isNotBlank() && nameDraft != name.orEmpty()
            ) {
                Text(if (isRenaming) stringResource(R.string.saving) else stringResource(R.string.save_name))
            }
            realtimeStatus?.let { status ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            realtimeError?.let { error ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    Log.d(tag, "Manual refresh clicked host=$host")
                    onRefresh()
                },
                enabled = !isRefreshing
            ) {
                Text(stringResource(R.string.reload))
            }
        }
    }
}

@Composable
fun VolumeControlCard(
    volumeDraft: Float,
    volumeState: VolumeState?,
    isApplying: Boolean,
    onVolumeChanged: (Float) -> Unit,
    onApplyVolume: () -> Unit,
    onToggleMute: () -> Unit
) {
    val target = volumeState?.targetVolume ?: volumeDraft.toInt()
    val actual = volumeState?.actualVolume
    val muteEnabled = volumeState?.muteEnabled == true

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.volume),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            Slider(
                value = volumeDraft,
                onValueChange = onVolumeChanged,
                onValueChangeFinished = onApplyVolume,
                valueRange = 0f..100f,
                steps = 99,
                enabled = !isApplying
            )

            Text(
                text = stringResource(R.string.target_actual, target.toString(), actual?.toString() ?: "-"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = stringResource(
                    R.string.mute_state,
                    if (muteEnabled) stringResource(R.string.on) else stringResource(R.string.off)
                ),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )

            FilledTonalButton(
                onClick = onToggleMute,
                enabled = !isApplying
            ) {
                Text(
                    if (muteEnabled) {
                        stringResource(R.string.disable_mute)
                    } else {
                        stringResource(R.string.enable_mute)
                    }
                )
            }
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
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
            Slider(
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
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

            Slider(
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
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
