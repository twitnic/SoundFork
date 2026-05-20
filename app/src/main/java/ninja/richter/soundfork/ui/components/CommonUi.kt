package ninja.richter.soundfork

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.model.RadioStation
import ninja.richter.soundfork.model.SoundForkDevicePage
import ninja.richter.soundfork.model.SoundForkUiState

@Composable
fun SoundForkDrawer(
    selectedPage: SoundForkDevicePage,
    speakerName: String?,
    onOpenRadio: () -> Unit,
    onOpenDevice: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenPresets: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenDiscovery: () -> Unit
) {
    ModalDrawerSheet {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SoundFork",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = speakerName ?: "Verbundener Lautsprecher",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NavigationDrawerItem(
                label = { Text("Radio") },
                selected = selectedPage == SoundForkDevicePage.RADIO,
                onClick = onOpenRadio
            )
            NavigationDrawerItem(
                label = { Text("Gerät") },
                selected = selectedPage == SoundForkDevicePage.DEVICE,
                onClick = onOpenDevice
            )
            NavigationDrawerItem(
                label = { Text("Quelle") },
                selected = selectedPage == SoundForkDevicePage.SOURCE,
                onClick = onOpenSource
            )
            NavigationDrawerItem(
                label = { Text("Presets") },
                selected = selectedPage == SoundForkDevicePage.PRESETS,
                onClick = onOpenPresets
            )
            NavigationDrawerItem(
                label = { Text("Recents") },
                selected = selectedPage == SoundForkDevicePage.RECENTS,
                onClick = onOpenRecents
            )
            NavigationDrawerItem(
                label = { Text("Zonen") },
                selected = selectedPage == SoundForkDevicePage.ZONES,
                onClick = onOpenZones
            )
            NavigationDrawerItem(
                label = { Text("Debug") },
                selected = selectedPage == SoundForkDevicePage.DEBUG,
                onClick = onOpenDebug
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("Lautsprecher suchen") },
                selected = false,
                onClick = onOpenDiscovery
            )
        }
    }
}

@Composable
fun MiniPlayerBar(
    station: RadioStation?,
    nowPlaying: NowPlayingState?,
    status: String?,
    isPlaying: Boolean,
    enabled: Boolean,
    onKey: (String) -> Unit
) {
    val title = station?.name
        ?: nowPlaying?.stationName?.takeIf { it.isUsefulMiniPlayerText() }
        ?: nowPlaying?.track?.takeIf { it.isUsefulMiniPlayerText() }
        ?: nowPlaying?.location?.toMiniPlayerStreamHost()
        ?: "Kein Sender aktiv"
    val detail = listOfNotNull(
        status?.takeIf { it.isNotBlank() },
        nowPlaying?.playStatus?.toMiniPlayerPlayStatus(),
        station?.description?.takeIf { it.isNotBlank() },
        nowPlaying?.streamType?.takeIf { it.isUsefulMiniPlayerText() }?.replace('_', ' '),
        nowPlaying?.location?.toMiniPlayerStreamHost()
    ).distinct().joinToString(" | ").ifBlank {
        "Wähle einen Sender aus der Liste"
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            .shadow(elevation = 8.dp, shape = MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title.firstOrNull()?.uppercaseChar()?.toString() ?: "♪",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            MiniPlayerButton(
                label = if (isPlaying) "Ⅱ" else "▶",
                key = if (isPlaying) "PAUSE" else "PLAY",
                enabled = enabled,
                onKey = onKey
            )
            MiniPlayerButton(label = "■", key = "STOP", enabled = enabled, onKey = onKey)
        }
    }
}

fun SoundForkUiState.isMiniPlayerPlaying(): Boolean {
    val statusText = dlnaStreamStatus.orEmpty().trim().uppercase()
    if (statusText.contains("STOP")) {
        return false
    }
    if (statusText.contains("PLAY") || statusText.contains("GESTARTET") || statusText.contains("BUFFER")) {
        return true
    }

    val playStatus = nowPlaying?.playStatus.orEmpty().trim().uppercase()
    if (playStatus.contains("STOP")) {
        return false
    }
    if (playStatus.contains("PLAY") || playStatus.contains("PAUSE") || playStatus.contains("BUFFER")) {
        return true
    }
    return nowPlaying?.source.orEmpty().trim().uppercase() == "UPNP" &&
        !nowPlaying?.location.isNullOrBlank()
}

private fun String.isUsefulMiniPlayerText(): Boolean {
    val normalized = trim()
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .uppercase()
    return normalized.isNotBlank() &&
        normalized != "UPNP" &&
        normalized != "TRACK ONDEMAND" &&
        normalized != "TRACK ONDEMAND TRACK ONDEMAND" &&
        normalized != "ONDEMAND" &&
        normalized != "UNKNOWN"
}

private fun String.toMiniPlayerPlayStatus(): String? {
    val normalized = trim().uppercase()
    return when {
        normalized.contains("BUFFER") -> "Puffert"
        normalized.contains("PLAY") -> "Läuft"
        normalized.contains("PAUSE") -> "Pausiert"
        normalized.contains("STOP") -> "Gestoppt"
        normalized.isBlank() -> null
        else -> trim()
    }
}

private fun String.toMiniPlayerStreamHost(): String? {
    val value = trim()
    if (value.isBlank()) {
        return null
    }
    return value
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore('/')
        .takeIf { it.isNotBlank() }
}

@Composable
fun MiniPlayerButton(
    label: String,
    key: String,
    enabled: Boolean,
    onKey: (String) -> Unit
) {
    TextButton(
        modifier = Modifier.width(38.dp),
        onClick = { onKey(key) },
        enabled = enabled
    ) {
        Text(label)
    }
}

@Composable
fun StatusMessage(
    text: String,
    isError: Boolean
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
