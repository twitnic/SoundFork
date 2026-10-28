package ninja.richter.soundfork

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ninja.richter.soundfork.data.SourceItem
import ninja.richter.soundfork.model.SoundForkDevicePage
import ninja.richter.soundfork.model.SoundForkScreenMode
import ninja.richter.soundfork.model.SoundForkUiState

@StringRes
fun SoundForkDevicePage.titleResource(): Int = when (this) {
    SoundForkDevicePage.RADIO -> R.string.nav_radio
    SoundForkDevicePage.DEVICE -> R.string.nav_device
    SoundForkDevicePage.SOURCE -> R.string.nav_source
    SoundForkDevicePage.PRESETS -> R.string.nav_presets
    SoundForkDevicePage.RECENTS -> R.string.nav_recents
    SoundForkDevicePage.ZONES -> R.string.nav_zones
    SoundForkDevicePage.DEBUG -> R.string.nav_debug
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundTouchScaffold(
    state: SoundForkUiState,
    onPage: (SoundForkDevicePage) -> Unit,
    onDiscovery: () -> Unit,
    onKey: (String) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onApplyVolume: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectSource: (SourceItem) -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val connected = state.screenMode == SoundForkScreenMode.DEVICE
    val scope = rememberCoroutineScope()
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var speakerOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val persistentNavigation = connected && maxWidth >= 600.dp
        val persistentPlayer = connected && maxWidth >= 1000.dp
        val expandedNavigation = maxWidth >= 1200.dp
        // A removed drawer has no anchors to animate to. Recreate its transient state on a
        // layout change so an open phone menu cannot reappear when returning from tablet mode.
        val drawer = remember(persistentNavigation, connected) { DrawerState(DrawerValue.Closed) }
        fun navigate(page: SoundForkDevicePage) {
            onPage(page)
            scope.launch { drawer.close() }
        }
        LaunchedEffect(persistentPlayer, connected) {
            if (persistentPlayer || !connected) {
                playerOpen = false
                speakerOpen = false
            }
        }
        ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = connected && !persistentNavigation, drawerContent = {
            if (connected && !persistentNavigation) SoundForkDrawer(state.devicePage, state.selectedSummary?.name,
                onPage = ::navigate,
                onOpenDiscovery = { onDiscovery(); scope.launch { drawer.close() } })
        }) {
            Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        title = { Text(stringResource(if (connected) state.devicePage.titleResource() else R.string.app_name),
                            style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp) },
                        navigationIcon = {
                            if (connected && !persistentNavigation) IconButton({ scope.launch { drawer.open() } }) {
                                TouchIcon(TouchSymbol.Menu, stringResource(R.string.open_menu))
                            }
                        },
                        actions = {
                            if (connected) {
                                IconButton({ onPage(SoundForkDevicePage.PRESETS) }) {
                                    TouchIcon(TouchSymbol.Grid, stringResource(R.string.nav_presets))
                                }
                                IconButton({ onKey("POWER") }, enabled = !state.isSendingTransportKey && !state.isLoadingSnapshot) {
                                    TouchIcon(TouchSymbol.Power, stringResource(R.string.power_toggle))
                                }
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }, bottomBar = {
                if (connected && !persistentPlayer) MiniPlayerBar(state.currentRadioStation, state.nowPlaying, state.isMiniPlayerPlaying(),
                    !state.isSendingTransportKey && !state.isLoadingSnapshot, state.selectedSummary?.name,
                    state.currentSource?.displayName(), { playerOpen = true }, { speakerOpen = true }, onKey)
            }) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (persistentNavigation) {
                        SoundForkSidebar(state.devicePage, state.selectedSummary?.name, expandedNavigation,
                            onPage, onDiscovery)
                        VerticalDivider()
                    }
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = if (connected) 900.dp else 680.dp).fillMaxSize().testTag("page_content")) {
                            content(PaddingValues())
                        }
                    }
                    if (persistentPlayer) {
                        VerticalDivider()
                        PersistentPlayer(state, onKey, onVolumeChanged, onApplyVolume, onToggleMute, onSelectSource, onPage)
                    }
                }
            }
        }
        if (connected && !persistentPlayer && playerOpen) {
            ModalBottomSheet(onDismissRequest = { playerOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                dragHandle = null, containerColor = MaterialTheme.colorScheme.surface) {
                ExpandedPlayer(state, { playerOpen = false }, onKey) { playerOpen = false; speakerOpen = true }
            }
        }
        if (connected && !persistentPlayer && speakerOpen) {
            ModalBottomSheet(onDismissRequest = { speakerOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                SpeakerControls(state, onVolumeChanged, onApplyVolume, onToggleMute, onSelectSource,
                    onPage = { speakerOpen = false; onPage(it) })
            }
        }
    }
}

@Composable
private fun PersistentPlayer(
    state: SoundForkUiState,
    onKey: (String) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onApplyVolume: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectSource: (SourceItem) -> Unit,
    onPage: (SoundForkDevicePage) -> Unit
) {
    Surface(Modifier.width(340.dp).fillMaxHeight().testTag("persistent_player"), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TouchSectionHeading(stringResource(R.string.currently_playing))
            StationArtwork(playbackTitle(state.currentRadioStation, state.nowPlaying), Modifier.size(160.dp))
            Text(playbackTitle(state.currentRadioStation, state.nowPlaying), style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(playbackDetail(state.currentRadioStation, state.nowPlaying), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            TransportControls(state, onKey)
            HorizontalDivider()
            SpeakerControlsContent(state, onVolumeChanged, onApplyVolume, onToggleMute, onSelectSource, onPage,
                largeIllustration = false)
        }
    }
}

@Composable
private fun TransportControls(state: SoundForkUiState, onKey: (String) -> Unit, onSpeaker: (() -> Unit)? = null) {
    val available = !state.isLoadingSnapshot && !state.isSendingTransportKey &&
        (state.currentRadioStation != null || state.nowPlaying?.source?.let { it != "STANDBY" } == true)
    Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton({ onKey("STOP") }, enabled = available, modifier = Modifier.size(48.dp)) {
            TouchIcon(TouchSymbol.Stop, stringResource(R.string.stop))
        }
        FilledIconButton({ onKey(if (state.isMiniPlayerPlaying()) "PAUSE" else "PLAY") },
            enabled = available, modifier = Modifier.size(76.dp)) {
            TouchIcon(if (state.isMiniPlayerPlaying()) TouchSymbol.Pause else TouchSymbol.Play,
                stringResource(if (state.isMiniPlayerPlaying()) R.string.pause else R.string.play), Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onPrimary)
        }
        if (onSpeaker != null) OutlinedIconButton(onSpeaker, modifier = Modifier.size(48.dp)) {
            TouchIcon(TouchSymbol.Speaker, stringResource(R.string.speaker_controls))
        }
    }
}

@Composable
private fun ExpandedPlayer(state: SoundForkUiState, onClose: () -> Unit, onKey: (String) -> Unit, onSpeaker: () -> Unit) {
    val title = playbackTitle(state.currentRadioStation, state.nowPlaying)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { TouchIcon(TouchSymbol.Down, stringResource(R.string.close_player)) }
            Text(stringResource(R.string.currently_playing), Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium)
            IconButton(onSpeaker) { TouchIcon(TouchSymbol.Volume, stringResource(R.string.speaker_controls)) }
        }
        Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.secondaryContainer))).padding(horizontal = 54.dp, vertical = 22.dp), contentAlignment = Alignment.Center) {
            StationArtwork(title, Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(1f))
        }
        Text(title, Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp), style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(playbackDetail(state.currentRadioStation, state.nowPlaying), Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Text(state.currentSource?.displayName() ?: stringResource(R.string.internet_radio),
            Modifier.padding(bottom = 24.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TransportControls(state, onKey, onSpeaker)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SpeakerControls(state: SoundForkUiState, onVolumeChanged: (Float) -> Unit, onApplyVolume: () -> Unit,
                            onToggleMute: () -> Unit, onSelectSource: (SourceItem) -> Unit, onPage: (SoundForkDevicePage) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SpeakerControlsContent(state, onVolumeChanged, onApplyVolume, onToggleMute, onSelectSource, onPage)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SpeakerControlsContent(
    state: SoundForkUiState, onVolumeChanged: (Float) -> Unit, onApplyVolume: () -> Unit,
    onToggleMute: () -> Unit, onSelectSource: (SourceItem) -> Unit, onPage: (SoundForkDevicePage) -> Unit,
    largeIllustration: Boolean = true
) {
    if (largeIllustration) SpeakerIllustration(Modifier.size(116.dp))
    Text(state.selectedSummary?.name ?: stringResource(R.string.connected_speaker), style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
    Text(state.selectedSummary?.type ?: state.selectedHost.orEmpty(), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.volume), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(state.volumeDraft.toInt().toString(), style = MaterialTheme.typography.bodyMedium)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(onToggleMute, enabled = !state.isApplyingVolume) {
            TouchIcon(if (state.selectedVolume?.muteEnabled == true) TouchSymbol.Muted else TouchSymbol.Volume,
                stringResource(if (state.selectedVolume?.muteEnabled == true) R.string.disable_mute else R.string.enable_mute))
        }
        SoundTouchSlider(state.volumeDraft, onVolumeChanged, Modifier.weight(1f), enabled = !state.isApplyingVolume,
            valueRange = 0f..100f, onValueChangeFinished = onApplyVolume)
    }
    if (state.sources.isNotEmpty()) {
        HorizontalDivider()
        TouchSectionHeading(stringResource(R.string.sources), Modifier.align(Alignment.Start))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.sources.forEach { source ->
                FilterChip(source.matches(state.currentSource), { onSelectSource(source) },
                    label = { Text(source.displayName()) }, enabled = !state.isSelectingSource && source.isSelectable())
            }
        }
    }
    HorizontalDivider()
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TextButton({ onPage(SoundForkDevicePage.DEVICE) }) { Text(stringResource(R.string.device_settings)) }
        TextButton({ onPage(SoundForkDevicePage.ZONES) }) { Text(stringResource(R.string.nav_zones)) }
    }
}
