package ninja.richter.soundfork

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
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
    SoundForkDevicePage.INFO -> R.string.nav_info
}

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
    folds: List<WindowFold> = emptyList(),
    content: @Composable (PaddingValues) -> Unit
) {
    // Moving the page between panes must preserve its scroll position and local edit state.
    val currentContent = rememberUpdatedState(content)
    val pageContent = remember { movableContentOf<PaddingValues> { currentContent.value(it) } }
    var windowOrigin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("fold_layout").onGloballyPositioned { windowOrigin = it.positionInWindow() }) {
            val windowBounds = Rect(windowOrigin, Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()))
            val split = calculateFoldPanes(windowBounds, folds)
            CompositionLocalProvider(LocalFoldSeparated provides (split != null)) {
                if (split == null) {
                    ResponsiveSoundTouchScaffold(state, onPage, onDiscovery, onKey, onVolumeChanged, onApplyVolume,
                        onToggleMute, onSelectSource, content = pageContent)
                } else {
                    val hasPlayer = state.screenMode == SoundForkScreenMode.DEVICE && split.supportsPlayer(density.density)
                    val pagePane = if (hasPlayer) split.first else split.largestPane()
                    FoldPaneLayout(listOfNotNull(pagePane, split.second.takeIf { hasPlayer })) {
                        Box(Modifier.fillMaxSize().clipToBounds().testTag("fold_page_pane")) {
                            ResponsiveSoundTouchScaffold(state, onPage, onDiscovery, onKey, onVolumeChanged, onApplyVolume,
                                onToggleMute, onSelectSource, externalPlayer = hasPlayer, content = pageContent)
                        }
                        if (hasPlayer) {
                            Box(Modifier.fillMaxSize().clipToBounds().testTag("fold_player_pane")) {
                                PersistentPlayer(state, onKey, onVolumeChanged, onApplyVolume, onToggleMute, onSelectSource, onPage,
                                    modifier = Modifier.fillMaxSize(), tabletop = split.orientation == FoldOrientation.HORIZONTAL)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Absolute placement is intentional: hinge coordinates describe physical left/right, also in RTL. */
@Composable
private fun FoldPaneLayout(panes: List<Rect>, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val placeables = measurables.zip(panes).map { (measurable, pane) ->
            measurable.measure(Constraints.fixed(pane.width.roundToInt(), pane.height.roundToInt()))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.zip(panes).forEach { (placeable, pane) ->
                placeable.place(pane.left.roundToInt(), pane.top.roundToInt())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponsiveSoundTouchScaffold(
    state: SoundForkUiState,
    onPage: (SoundForkDevicePage) -> Unit,
    onDiscovery: () -> Unit,
    onKey: (String) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onApplyVolume: () -> Unit,
    onToggleMute: () -> Unit,
    onSelectSource: (SourceItem) -> Unit,
    externalPlayer: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val connected = state.screenMode == SoundForkScreenMode.DEVICE
    val showingInfo = state.devicePage == SoundForkDevicePage.INFO
    BackHandler(enabled = showingInfo) { onPage(SoundForkDevicePage.RADIO) }
    val scope = rememberCoroutineScope()
    val foldSeparated = LocalFoldSeparated.current
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    var speakerOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val persistentNavigation = connected && maxWidth >= 600.dp
        val persistentPlayer = connected && !externalPlayer && maxWidth >= 1000.dp
        val expandedNavigation = maxWidth >= 1200.dp
        // A removed drawer has no anchors to animate to. Recreate its transient state on a
        // layout change so an open phone menu cannot reappear when returning from tablet mode.
        val drawer = remember(persistentNavigation, connected) { DrawerState(DrawerValue.Closed) }
        fun navigate(page: SoundForkDevicePage) {
            onPage(page)
            scope.launch { drawer.close() }
        }
        LaunchedEffect(persistentPlayer, connected, externalPlayer) {
            if (persistentPlayer || externalPlayer || !connected) {
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
                        title = { Text(stringResource(if (connected || showingInfo) state.devicePage.titleResource() else R.string.app_name),
                            style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            if (connected && !persistentNavigation) IconButton({ scope.launch { drawer.open() } }) {
                                TouchIcon(TouchSymbol.Menu, stringResource(R.string.open_menu))
                            } else if (!connected && showingInfo) IconButton({ onPage(SoundForkDevicePage.RADIO) }) {
                                TouchIcon(TouchSymbol.Back, stringResource(R.string.navigate_back))
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
                            } else if (!showingInfo) {
                                IconButton({ onPage(SoundForkDevicePage.INFO) }) {
                                    TouchIcon(TouchSymbol.Info, stringResource(R.string.nav_info))
                                }
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }, bottomBar = {
                if (connected && !persistentPlayer && !externalPlayer) MiniPlayerBar(state.currentRadioStation, state.nowPlaying, state.isMiniPlayerPlaying(),
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
        if (connected && !persistentPlayer && !externalPlayer && playerOpen) {
            if (foldSeparated) {
                PanePlayerOverlay({ playerOpen = false }) {
                    ExpandedPlayer(state, { playerOpen = false }, onKey) { playerOpen = false; speakerOpen = true }
                }
            } else {
                ModalBottomSheet(onDismissRequest = { playerOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    dragHandle = null, containerColor = MaterialTheme.colorScheme.surface) {
                    ExpandedPlayer(state, { playerOpen = false }, onKey) { playerOpen = false; speakerOpen = true }
                }
            }
        }
        if (connected && !persistentPlayer && !externalPlayer && speakerOpen) {
            if (foldSeparated) {
                PanePlayerOverlay({ speakerOpen = false }) {
                    Column {
                        IconButton({ speakerOpen = false }) { TouchIcon(TouchSymbol.Down, stringResource(R.string.close_player)) }
                        SpeakerControls(state, onVolumeChanged, onApplyVolume, onToggleMute, onSelectSource,
                            onPage = { speakerOpen = false; onPage(it) })
                    }
                }
            } else {
                ModalBottomSheet(onDismissRequest = { speakerOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    SpeakerControls(state, onVolumeChanged, onApplyVolume, onToggleMute, onSelectSource,
                        onPage = { speakerOpen = false; onPage(it) })
                }
            }
        }
    }
}

@Composable
private fun PanePlayerOverlay(onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize().testTag("pane_player_overlay"), color = MaterialTheme.colorScheme.surfaceContainer) {
        content()
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
    onPage: (SoundForkDevicePage) -> Unit,
    modifier: Modifier = Modifier.width(340.dp).fillMaxHeight(),
    tabletop: Boolean = false
) {
    Surface(modifier.testTag("persistent_player"), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // The lower tabletop pane opens with transport controls in reach, even at low heights.
                if (tabletop) TransportControls(state, onKey)
                else {
                    TouchSectionHeading(stringResource(R.string.currently_playing))
                    StationArtwork(playbackTitle(state.currentRadioStation, state.nowPlaying), Modifier.size(160.dp))
                }
                Text(playbackTitle(state.currentRadioStation, state.nowPlaying), style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(playbackDetail(state.currentRadioStation, state.nowPlaying), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (!tabletop) TransportControls(state, onKey)
                HorizontalDivider()
                SpeakerControlsContent(state, onVolumeChanged, onApplyVolume, onToggleMute, onSelectSource, onPage,
                    largeIllustration = false)
            }
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
