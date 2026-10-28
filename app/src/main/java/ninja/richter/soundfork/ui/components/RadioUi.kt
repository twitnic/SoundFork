package ninja.richter.soundfork

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.model.RadioStation
import ninja.richter.soundfork.model.RecentRadioStation
import java.util.Date

@Composable
fun NowPlayingCard(nowPlaying: NowPlayingState?, station: RadioStation?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StationArtwork(playbackTitle(station, nowPlaying), Modifier.size(52.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.currently_playing), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(playbackTitle(station, nowPlaying), style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playbackDetail(station, nowPlaying), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun RecentRadioCarousel(stations: List<RecentRadioStation>, activeStation: RadioStation?, isStarting: Boolean,
                        onPlayStation: (RecentRadioStation) -> Unit) {
    val listState = rememberLazyListState()
    val firstStationKey = stations.firstOrNull()?.let { "${it.streamUrl}|${it.lastPlayedAt}" }
    LaunchedEffect(firstStationKey) { if (firstStationKey != null) listState.animateScrollToItem(0) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TouchSectionHeading(stringResource(R.string.recently_heard), Modifier.weight(1f))
            TouchIcon(TouchSymbol.Next, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(stations, key = { it.streamUrl }) { station ->
                RecentRadioCard(station, station.matches(activeStation), !isStarting) { onPlayStation(station) }
            }
        }
    }
}

@Composable
fun RecentRadioCard(station: RecentRadioStation, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val playedAt = DateFormat.getTimeFormat(context).format(Date(station.lastPlayedAt))
    Column(Modifier.width(136.dp).semantics { selected = active }.clickable(enabled, role = Role.Button, onClick = onClick)) {
        Box {
            StationArtwork(station.name, Modifier.size(136.dp))
            if (active) {
                Surface(Modifier.align(Alignment.BottomEnd).padding(7.dp), shape = MaterialTheme.shapes.extraSmall,
                    color = Color.Black.copy(alpha = 0.75f), contentColor = Color.White) {
                    Text(stringResource(R.string.active), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(9.dp)) {
            Text(station.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.last_played, playedAt), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun RadioStationListCard(stations: List<RadioStation>, activeStation: RadioStation?, searchQuery: String,
                        favoriteStreamUrls: Set<String>, status: String?, isStarting: Boolean,
                        onSearchQueryChanged: (String) -> Unit, onToggleFavorite: (RadioStation) -> Unit,
                        onPlayStation: (RadioStation) -> Unit) {
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val displayedStations = remember(stations, searchQuery, favoriteStreamUrls, favoritesOnly) {
        stations.filter { it.matchesSearch(searchQuery) && (!favoritesOnly || it.streamUrl in favoriteStreamUrls) }
            .sortedWith(compareByDescending<RadioStation> { it.streamUrl in favoriteStreamUrls }.thenBy { it.name.lowercase() })
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextField(searchQuery, onSearchQueryChanged, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text(stringResource(R.string.search_station)) },
            leadingIcon = { TouchIcon(TouchSymbol.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(favoritesOnly.not(), { favoritesOnly = false }, { Text(stringResource(R.string.all_stations)) })
            FilterChip(favoritesOnly, { favoritesOnly = true }, { Text(stringResource(R.string.favorites)) })
            Spacer(Modifier.weight(1f))
            Text(displayedStations.size.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column {
                if (displayedStations.isEmpty()) {
                    Text(stringResource(if (favoritesOnly) R.string.no_favorite_stations else R.string.no_stations_found),
                        Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                displayedStations.forEachIndexed { index, station ->
                    RadioStationRow(station, station.matches(activeStation), station.streamUrl in favoriteStreamUrls,
                        !isStarting, { onToggleFavorite(station) }, { onPlayStation(station) })
                    if (index < displayedStations.lastIndex) HorizontalDivider(Modifier.padding(start = 76.dp))
                }
            }
        }
        if (status != null && isErrorStatus(status)) StatusMessage(status, true)
    }
}

@Composable
fun RadioStationRow(station: RadioStation, active: Boolean, favorite: Boolean, enabled: Boolean,
                    onToggleFavorite: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().semantics { selected = active }
        .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .clickable(enabled, role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(46.dp).background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent))
        StationArtwork(station.name, Modifier.padding(start = 10.dp).size(50.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp, top = 15.dp, bottom = 15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(station.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (active) stringResource(R.string.active) else station.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onToggleFavorite, enabled = enabled) {
            TouchIcon(if (favorite) TouchSymbol.Check else TouchSymbol.Heart,
                stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
    }
}

fun RadioStation.matchesSearch(query: String): Boolean = query.trim().let {
    it.isBlank() || name.contains(it, true) || description.contains(it, true) || streamUrl.contains(it, true)
}

fun RadioStation.matches(other: RadioStation?): Boolean = other != null &&
    (streamUrl.isNotBlank() && streamUrl == other.streamUrl || name.equals(other.name, true))

fun RecentRadioStation.matches(other: RadioStation?): Boolean = other != null &&
    (streamUrl.isNotBlank() && streamUrl == other.streamUrl || name.equals(other.name, true))

@Composable
fun TransportControlCard(enabled: Boolean, onKey: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TouchSectionHeading(stringResource(R.string.transport_controls))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportButton(stringResource(R.string.play), "PLAY", enabled, onKey)
            TransportButton(stringResource(R.string.pause), "PAUSE", enabled, onKey)
            TransportButton(stringResource(R.string.stop), "STOP", enabled, onKey)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportButton(stringResource(R.string.play_pause), "PLAY_PAUSE", enabled, onKey)
            TransportButton(stringResource(R.string.power), "POWER", enabled, onKey)
        }
    }
}

@Composable
fun TransportButton(label: String, key: String, enabled: Boolean, onKey: (String) -> Unit) {
    FilledTonalButton({ onKey(key) }, enabled = enabled) { Text(label) }
}
