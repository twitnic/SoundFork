package ninja.richter.soundfork

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ninja.richter.soundfork.data.BoseRecentItem
import ninja.richter.soundfork.data.PresetItem

@Composable
fun PresetListCard(
    presets: List<PresetItem>,
    status: String?,
    enabled: Boolean,
    onPlayPreset: (PresetItem?, Int) -> Unit,
    onSavePreset: (Int) -> Unit
) {
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
                text = "Gespeicherte Presets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (presets.isEmpty()) {
                Text(
                    text = "Der Lautsprecher liefert aktuell keine Presets in /presets. Die sechs Speicherplaetze koennen trotzdem per Taste belegt werden.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val presetsById = remember(presets) {
                presets.mapNotNull { preset -> preset.id?.let { it to preset } }.toMap()
            }
            (1..6).forEach { presetId ->
                PresetRow(
                    presetId = presetId,
                    preset = presetsById[presetId],
                    enabled = enabled,
                    onPlayPreset = onPlayPreset,
                    onSavePreset = onSavePreset
                )
                if (presetId < 6) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 48.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
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
fun PresetRow(
    presetId: Int,
    preset: PresetItem?,
    enabled: Boolean,
    onPlayPreset: (PresetItem?, Int) -> Unit,
    onSavePreset: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 82.dp)
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
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = presetId.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = preset?.name ?: "Preset $presetId",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOfNotNull(
                        preset?.source?.takeIf { it.isNotBlank() },
                        preset?.sourceAccount?.takeIf { it.isNotBlank() }
                    ).joinToString(" | ").ifBlank { "Nicht belegt oder nicht vom Lautsprecher gemeldet" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                preset?.location?.takeIf { it.isNotBlank() }?.let { location ->
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (preset?.isPresetable == true) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = "presetable",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onPlayPreset(preset, presetId) },
                enabled = enabled
            ) {
                Text("Abspielen")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSavePreset(presetId) },
                enabled = enabled
            ) {
                Text("Aktuellen Stream speichern")
            }
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
                text = "Bose Recents",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (recents.isEmpty()) {
                Text(
                    text = "Der Lautsprecher liefert aktuell keine Eintraege in /recents.",
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
                        ?: "Recent",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOfNotNull(
                        recent.source?.takeIf { it.isNotBlank() },
                        recent.sourceAccount?.takeIf { it.isNotBlank() }
                    ).joinToString(" | ").ifBlank { "Quelle unbekannt" },
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
            Text("Abspielen")
        }
    }
}
