package ninja.richter.soundfork

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.model.RadioStation
import ninja.richter.soundfork.model.SoundForkDevicePage
import ninja.richter.soundfork.model.SoundForkUiState
import ninja.richter.soundfork.model.isPlaybackRunning

@Composable
fun SoundForkDrawer(
    selectedPage: SoundForkDevicePage,
    speakerName: String?,
    onPage: (SoundForkDevicePage) -> Unit,
    onOpenDiscovery: () -> Unit
) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer) {
        SoundForkNavigationContent(selectedPage, speakerName, false, onPage, onOpenDiscovery)
    }
}

@Composable
fun SoundForkSidebar(
    selectedPage: SoundForkDevicePage,
    speakerName: String?,
    expanded: Boolean,
    onPage: (SoundForkDevicePage) -> Unit,
    onOpenDiscovery: () -> Unit
) {
    Surface(Modifier.width(if (expanded) 224.dp else 96.dp).fillMaxHeight().testTag("persistent_navigation"),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        SoundForkNavigationContent(selectedPage, speakerName, !expanded, onPage, onOpenDiscovery)
    }
}

private val navigationPages = listOf(
    SoundForkDevicePage.RADIO to TouchSymbol.Radio,
    SoundForkDevicePage.PRESETS to TouchSymbol.Grid,
    SoundForkDevicePage.RECENTS to TouchSymbol.Clock,
    SoundForkDevicePage.DEVICE to TouchSymbol.Settings,
    SoundForkDevicePage.SOURCE to TouchSymbol.Source,
    SoundForkDevicePage.ZONES to TouchSymbol.Zone,
    SoundForkDevicePage.DEBUG to TouchSymbol.Debug,
    SoundForkDevicePage.INFO to TouchSymbol.Info
)

@Composable
private fun SoundForkNavigationContent(
    selectedPage: SoundForkDevicePage,
    speakerName: String?,
    compact: Boolean,
    onPage: (SoundForkDevicePage) -> Unit,
    onOpenDiscovery: () -> Unit
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 24.dp)) {
        if (!compact) {
            Text(stringResource(R.string.app_name), Modifier.padding(horizontal = 28.dp),
                style = MaterialTheme.typography.headlineMedium, letterSpacing = 3.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(speakerName ?: stringResource(R.string.connected_speaker),
                Modifier.padding(start = 28.dp, top = 8.dp, end = 20.dp, bottom = 30.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        navigationPages.forEachIndexed { index, (page, symbol) ->
            if (index == 3) HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            if (compact) NavigationRailItem(selected = selectedPage == page, onClick = { onPage(page) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                icon = { TouchIcon(symbol, null) }, label = { Text(stringResource(page.titleResource()), textAlign = TextAlign.Center) })
            else DrawerRow(stringResource(page.titleResource()), symbol, selectedPage == page) { onPage(page) }
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        if (compact) NavigationRailItem(selected = false, onClick = onOpenDiscovery,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            icon = { TouchIcon(TouchSymbol.Search, null) },
            label = { Text(stringResource(R.string.nav_search_speaker), textAlign = TextAlign.Center) })
        else DrawerRow(stringResource(R.string.nav_search_speaker), TouchSymbol.Search, false, onOpenDiscovery)
    }
}

@Composable
private fun DrawerRow(label: String, symbol: TouchSymbol, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .selectable(selected = selected, role = Role.Tab, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(3.dp).height(32.dp).background(if (selected) color else Color.Transparent))
        TouchIcon(symbol, null, Modifier.padding(start = 24.dp, end = 22.dp), tint = color)
        Text(label, Modifier.weight(1f).padding(vertical = 16.dp, horizontal = 4.dp),
            color = color, style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, letterSpacing = 1.1.sp)
    }
}

@Composable
fun playbackTitle(station: RadioStation?, nowPlaying: NowPlayingState?): String =
    station?.name?.takeIf { it.isNotBlank() }
        ?: nowPlaying?.stationName?.takeIf { it.isUsefulPlaybackText() }
        ?: nowPlaying?.track?.takeIf { it.isUsefulPlaybackText() }
        ?: nowPlaying?.location?.substringAfter("://")?.substringBefore('/')?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.no_station_active)

@Composable
fun playbackDetail(station: RadioStation?, nowPlaying: NowPlayingState?): String {
    val state = nowPlaying?.playStatus.orEmpty().uppercase(java.util.Locale.ROOT)
    val status = when {
        state.contains("BUFFER") -> stringResource(R.string.buffering)
        state.contains("PAUSE") -> stringResource(R.string.paused)
        state.contains("STOP") || nowPlaying?.source == "STANDBY" -> stringResource(R.string.stopped)
        state.contains("PLAY") -> stringResource(R.string.playing)
        else -> null
    }
    val track = nowPlaying?.track?.takeIf { it.isUsefulPlaybackText() && it != station?.name }
    return listOfNotNull(
        track ?: station?.description?.takeIf { it.isNotBlank() },
        nowPlaying?.artist?.takeIf { it.isUsefulPlaybackText() }, status
    ).distinct().joinToString(" · ").ifBlank { stringResource(R.string.choose_station_from_list) }
}

fun String.isUsefulPlaybackText(): Boolean = trim().replace('_', ' ').uppercase(java.util.Locale.ROOT)
    .let { it.isNotBlank() && it !in setOf("UPNP", "TRACK ONDEMAND", "TRACK ONDEMAND TRACK ONDEMAND", "ONDEMAND", "UNKNOWN") }

@Composable
fun MiniPlayerBar(
    station: RadioStation?, nowPlaying: NowPlayingState?, isPlaying: Boolean,
    enabled: Boolean, speakerName: String?, sourceName: String?,
    onOpenPlayer: () -> Unit, onOpenSpeaker: () -> Unit, onKey: (String) -> Unit
) {
    val title = playbackTitle(station, nowPlaying)
    val available = station != null || nowPlaying?.source?.let { it != "STANDBY" } == true
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(
            listOf(Color(0xFF80645F), Color(0xFFB99B91), Color(0xFF786065))
        )).heightIn(min = 66.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).testTag("mini_player_open").clickable(role = Role.Button, onClickLabel = stringResource(R.string.open_player), onClick = onOpenPlayer)
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TouchIcon(TouchSymbol.Up, null, tint = Color.White)
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(playbackDetail(station, nowPlaying), color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            PlaybackIconButton(isPlaying, enabled && available, onKey, light = true)
            IconButton(onClick = { onKey("STOP") }, enabled = enabled && available) {
                TouchIcon(TouchSymbol.Stop, stringResource(R.string.stop), tint = Color.White.copy(alpha = if (available) 1f else 0.4f))
            }
            Spacer(Modifier.width(6.dp))
        }
        Row(Modifier.fillMaxWidth().testTag("speaker_controls_open").clickable(role = Role.Button, onClickLabel = stringResource(R.string.speaker_controls), onClick = onOpenSpeaker)
            .padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeakerIllustration(Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Text(speakerName ?: stringResource(R.string.connected_speaker), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sourceName ?: stringResource(R.string.playback_on_speaker), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TouchIcon(TouchSymbol.Volume, null)
        }
    }
}

@Composable
fun PlaybackIconButton(isPlaying: Boolean, enabled: Boolean, onKey: (String) -> Unit, light: Boolean = false) {
    val color = if (light) Color.White else MaterialTheme.colorScheme.onSurface
    OutlinedIconButton(onClick = { onKey(if (isPlaying) "PAUSE" else "PLAY") }, enabled = enabled,
        border = BorderStroke(1.dp, color.copy(alpha = if (enabled) 0.8f else 0.3f)), modifier = Modifier.size(44.dp)) {
        TouchIcon(if (isPlaying) TouchSymbol.Pause else TouchSymbol.Play,
            stringResource(if (isPlaying) R.string.pause else R.string.play), tint = color.copy(alpha = if (enabled) 1f else 0.4f))
    }
}

fun SoundForkUiState.isMiniPlayerPlaying(): Boolean = isPlaybackRunning()

@Composable
fun StatusMessage(text: String, isError: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
    }
}
