package ninja.richter.soundfork

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ninja.richter.soundfork.data.BoseRecentItem
import ninja.richter.soundfork.data.PresetItem
import ninja.richter.soundfork.model.RadioStation

@Composable
fun PresetListCard(
    presets: List<PresetItem>, status: String?, enabled: Boolean,
    onPlayPreset: (PresetItem?, Int) -> Unit, onSavePreset: (Int) -> Unit,
    stations: List<RadioStation> = emptyList()
) {
    var saveSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.saved_presets), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.preset_hint), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                val presetsById = remember(presets) { presets.associateBy { it.id } }
                (1..6).forEach { id ->
                    val preset = presetsById[id]
                    val station = stations.firstOrNull { it.streamUrl == preset?.location }
                    PresetRow(id, preset, enabled, onPlayPreset, { saveSlot = it }, station?.name)
                    if (id < 6) HorizontalDivider(Modifier.padding(start = 40.dp))
                }
            }
        }
        if (status != null) StatusMessage(status, isErrorStatus(status))
    }
    saveSlot?.let { id ->
        AlertDialog(onDismissRequest = { saveSlot = null },
            title = { Text(stringResource(R.string.preset_confirm_title, id)) },
            text = { Text(stringResource(R.string.preset_confirm_body)) },
            confirmButton = { TextButton({ saveSlot = null; onSavePreset(id) }, enabled = enabled) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ saveSlot = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
fun PresetRow(presetId: Int, preset: PresetItem?, enabled: Boolean,
              onPlayPreset: (PresetItem?, Int) -> Unit, onSavePreset: (Int) -> Unit, stationName: String? = null) {
    val title = stationName ?: preset?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.preset_name, presetId)
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(presetId.toString(), Modifier.width(32.dp), style = MaterialTheme.typography.titleLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (preset != null) StationArtwork(title, Modifier.padding(horizontal = 8.dp).size(48.dp))
        else Box(Modifier.padding(horizontal = 8.dp).size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            TouchIcon(TouchSymbol.Plus, null, tint = MaterialTheme.colorScheme.outline)
        }
        Column(Modifier.weight(1f).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(when {
                preset == null -> stringResource(R.string.preset_unassigned)
                preset.source == "UPNP" -> stringResource(R.string.internet_radio)
                else -> preset.source ?: stringResource(R.string.unknown_source)
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton({ onPlayPreset(preset, presetId) }, enabled = enabled) {
            TouchIcon(TouchSymbol.Play, stringResource(R.string.play))
        }
        IconButton({ onSavePreset(presetId) }, enabled = enabled) {
            TouchIcon(TouchSymbol.Plus, stringResource(R.string.save_to_preset, presetId), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun BoseRecentsCard(
    recents: List<BoseRecentItem>,
    status: String?,
    enabled: Boolean,
    onPlayRecent: (BoseRecentItem) -> Unit
) {
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
                text = stringResource(R.string.bose_recents),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (recents.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_recents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recents.forEachIndexed { index, recent ->
                    BoseRecentRow(
                        recent = recent,
                        enabled = enabled,
                        onPlayRecent = onPlayRecent
                    )
                    if (index < recents.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isErrorStatus(status)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

@Composable
private fun BoseRecentRow(
    recent: BoseRecentItem,
    enabled: Boolean,
    onPlayRecent: (BoseRecentItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = recent.id?.toString() ?: "R",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = recent.name?.takeIf { it.isNotBlank() }
                        ?: recent.location?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.recent),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOfNotNull(
                        recent.source?.takeIf { it.isNotBlank() },
                        recent.sourceAccount?.takeIf { it.isNotBlank() }
                    ).joinToString(" | ").ifBlank { stringResource(R.string.unknown_source) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recent.location?.takeIf { it.isNotBlank() }?.let { location ->
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        FilledTonalButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp),
            onClick = { onPlayRecent(recent) },
            enabled = enabled && !recent.source.isNullOrBlank()
        ) {
            Text(stringResource(R.string.play))
        }
    }
}
