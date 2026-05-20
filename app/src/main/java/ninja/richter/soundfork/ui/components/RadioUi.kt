package ninja.richter.soundfork

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.model.RadioStation
import ninja.richter.soundfork.model.RecentRadioStation

@Composable
fun NowPlayingCard(
    nowPlaying: NowPlayingState?,
    station: RadioStation?
) {
    val bufferingText = stringResource(R.string.buffering)
    val playingText = stringResource(R.string.playing)
    val pausedText = stringResource(R.string.paused)
    val stoppedText = stringResource(R.string.stopped)
    val title = station?.name
        ?: nowPlaying?.stationName?.takeIf { it.isUsefulNowPlayingText() }
        ?: nowPlaying?.track?.takeIf { it.isUsefulNowPlayingText() }
        ?: nowPlaying?.location?.toDisplayStreamHost()
        ?: stringResource(R.string.no_station_active)
    val detailText = listOfNotNull(
        nowPlaying?.track?.takeIf { it.isUsefulNowPlayingText() && it != title },
        nowPlaying?.artist?.takeIf { it.isUsefulNowPlayingText() },
        nowPlaying?.streamType?.takeIf { it.isUsefulNowPlayingText() }?.replace('_', ' '),
        station?.description?.takeIf { it.isNotBlank() }
    ).distinct().joinToString(" | ").ifBlank {
        nowPlaying?.location?.toDisplayStreamHost()
            ?: stringResource(R.string.status_from_now_playing)
    }
    val sourceText = listOfNotNull(
        nowPlaying?.source?.takeIf { it.isUsefulSourceText() }
            ?.let { stringResource(R.string.source_prefix, it) },
        nowPlaying?.playStatus?.toDisplayPlayStatus(
            bufferingText = bufferingText,
            playingText = playingText,
            pausedText = pausedText,
            stoppedText = stoppedText
        )
    ).joinToString(" | ").ifBlank { stringResource(R.string.playback_on_speaker) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.currently_playing),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = station?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "♪",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = sourceText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

private fun String.isUsefulNowPlayingText(): Boolean {
    val normalized = normalizedNowPlayingText()
    return normalized.isNotBlank() &&
        normalized != "UPNP" &&
        normalized != "TRACK ONDEMAND" &&
        normalized != "TRACK ONDEMAND TRACK ONDEMAND" &&
        normalized != "ONDEMAND" &&
        normalized != "UNKNOWN"
}

private fun String.isUsefulSourceText(): Boolean {
    val normalized = trim().uppercase()
    return normalized.isNotBlank() &&
        normalized != "UPNP" &&
        normalized != "STANDBY"
}

private fun String.toDisplayPlayStatus(
    bufferingText: String,
    playingText: String,
    pausedText: String,
    stoppedText: String
): String? {
    val normalized = trim().uppercase()
    return when {
        normalized.contains("BUFFER") -> bufferingText
        normalized.contains("PLAY") -> playingText
        normalized.contains("PAUSE") -> pausedText
        normalized.contains("STOP") -> stoppedText
        normalized.isBlank() -> null
        else -> trim()
    }
}

private fun String.normalizedNowPlayingText(): String {
    return trim()
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .uppercase()
}

private fun String.toDisplayStreamHost(): String? {
    val value = trim()
    if (value.isBlank()) {
        return null
    }
    return runCatching {
        val withoutProtocol = value
            .removePrefix("https://")
            .removePrefix("http://")
        withoutProtocol.substringBefore('/').takeIf { it.isNotBlank() }
    }.getOrNull()
}

@Composable
fun RecentRadioCarousel(
    stations: List<RecentRadioStation>,
    activeStation: RadioStation?,
    isStarting: Boolean,
    onPlayStation: (RecentRadioStation) -> Unit
) {
    val listState = rememberLazyListState()
    val firstStationKey = stations.firstOrNull()?.let { station ->
        "${station.streamUrl}|${station.lastPlayedAt}"
    }

    LaunchedEffect(firstStationKey) {
        if (firstStationKey != null) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
            Text(
            text = stringResource(R.string.recently_heard),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = stations,
                key = { station -> station.streamUrl }
            ) { station ->
                RecentRadioCard(
                    station = station,
                    active = station.matches(activeStation),
                    enabled = !isStarting,
                    onClick = { onPlayStation(station) }
                )
            }
        }
    }
}

@Composable
fun RecentRadioCard(
    station: RecentRadioStation,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val playedAt = remember(station.lastPlayedAt) {
        SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(station.lastPlayedAt))
    }

    Surface(
        modifier = Modifier
            .width(218.dp)
            .height(132.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = if (active) 6.dp else 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = station.name.firstOrNull()?.uppercaseChar()?.toString() ?: "♪",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = station.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1
                    )
                    Text(
                        text = if (active) {
                            stringResource(R.string.active)
                        } else {
                            stringResource(R.string.last_played, playedAt)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = station.description,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
                maxLines = 2
            )
        }
    }
}

@Composable
fun RadioStationListCard(
    stations: List<RadioStation>,
    activeStation: RadioStation?,
    searchQuery: String,
    favoriteStreamUrls: Set<String>,
    status: String?,
    isStarting: Boolean,
    onSearchQueryChanged: (String) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onPlayStation: (RadioStation) -> Unit
) {
    val displayedStations = remember(stations, searchQuery, favoriteStreamUrls) {
        stations
            .filter { station -> station.matchesSearch(searchQuery) }
            .sortedWith(
                compareByDescending<RadioStation> { station ->
                    station.streamUrl in favoriteStreamUrls
                }.thenBy { station -> station.name.lowercase() }
            )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .shadow(elevation = 2.dp, shape = MaterialTheme.shapes.extraSmall),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_station)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (displayedStations.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_stations_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            displayedStations.forEachIndexed { index, station ->
                val isActive = station.matches(activeStation)
                RadioStationRow(
                    station = station,
                    active = isActive,
                    favorite = station.streamUrl in favoriteStreamUrls,
                    enabled = !isStarting,
                    onToggleFavorite = { onToggleFavorite(station) },
                    onClick = { onPlayStation(station) }
                )
                if (index < displayedStations.lastIndex && !isActive) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 54.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
            if (status != null) {
                Text(
                    text = status,
                    modifier = Modifier.padding(top = 10.dp),
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
fun RadioStationRow(
    station: RadioStation,
    active: Boolean,
    favorite: Boolean,
    enabled: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (active) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val avatarColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val avatarContentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .background(containerColor, MaterialTheme.shapes.large)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (active) 12.dp else 0.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(avatarColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = station.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.labelLarge,
                color = avatarContentColor
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = station.name,
                style = if (active) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor
            )
            Text(
                text = station.description,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }
        if (active) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = stringResource(R.string.active),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        TextButton(
            onClick = onToggleFavorite,
            enabled = enabled
        ) {
            Text(if (favorite) "★" else "☆")
        }
    }
}

fun RadioStation.matchesSearch(query: String): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return true
    }
    return name.contains(normalizedQuery, ignoreCase = true) ||
        description.contains(normalizedQuery, ignoreCase = true) ||
        streamUrl.contains(normalizedQuery, ignoreCase = true)
}

fun RadioStation.matches(other: RadioStation?): Boolean {
    if (other == null) {
        return false
    }
    return streamUrl.isNotBlank() && streamUrl == other.streamUrl ||
        name.equals(other.name, ignoreCase = true)
}

fun RecentRadioStation.matches(other: RadioStation?): Boolean {
    if (other == null) {
        return false
    }
    return streamUrl.isNotBlank() && streamUrl == other.streamUrl ||
        name.equals(other.name, ignoreCase = true)
}

@Composable
fun TransportControlCard(
    enabled: Boolean,
    onKey: (String) -> Unit
) {
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
                text = stringResource(R.string.transport_controls),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportButton(label = stringResource(R.string.play), key = "PLAY", enabled = enabled, onKey = onKey)
                TransportButton(label = stringResource(R.string.pause), key = "PAUSE", enabled = enabled, onKey = onKey)
                TransportButton(label = stringResource(R.string.stop), key = "STOP", enabled = enabled, onKey = onKey)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransportButton(label = stringResource(R.string.play_pause), key = "PLAY_PAUSE", enabled = enabled, onKey = onKey)
                TransportButton(label = stringResource(R.string.power), key = "POWER", enabled = enabled, onKey = onKey)
            }
        }
    }
}

@Composable
fun TransportButton(
    label: String,
    key: String,
    enabled: Boolean,
    onKey: (String) -> Unit
) {
    FilledTonalButton(
        onClick = { onKey(key) },
        enabled = enabled
    ) {
        Text(label)
    }
}
