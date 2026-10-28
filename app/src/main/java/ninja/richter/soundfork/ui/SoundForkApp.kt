package ninja.richter.soundfork

import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ninja.richter.soundfork.model.SoundForkDevicePage
import ninja.richter.soundfork.model.SoundForkScreenMode
import ninja.richter.soundfork.report.shareSpeakerReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundForkAppScreen(viewModel: MainViewModel = viewModel(), folds: List<WindowFold> = emptyList()) {
    val tag = TAG
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val expandedResults = remember { mutableStateMapOf<String, Boolean>() }
    val context = LocalContext.current
    val permissionError = remember { mutableStateOf<String?>(null) }
    val requiredPermissions = remember { requiredDiscoveryPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Log.i(tag, "permissionLauncher result=$result")
        if (result.values.all { it }) {
            permissionError.value = null
            viewModel.discoverSpeakers()
        } else {
            permissionError.value = context.getString(R.string.permission_discovery_missing)
        }
    }
    val missingPermissions = requiredPermissions.filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(state.discoveryError) {
        if (state.discoveryError != null) {
            Log.w(tag, "discoveryError=${state.discoveryError}")
        }
        if (state.discoveryError != null) {
            permissionError.value = null
        }
    }

    val isDeviceScreen = state.screenMode == SoundForkScreenMode.DEVICE

    SoundTouchScaffold(
        state = state,
        folds = folds,
        onPage = { page ->
            when (page) {
                SoundForkDevicePage.RADIO -> viewModel.openRadioPage()
                SoundForkDevicePage.DEVICE -> viewModel.openDevicePage()
                SoundForkDevicePage.SOURCE -> viewModel.openSourcePage()
                SoundForkDevicePage.PRESETS -> viewModel.openPresetsPage()
                SoundForkDevicePage.RECENTS -> viewModel.openRecentsPage()
                SoundForkDevicePage.ZONES -> viewModel.openZonesPage()
                SoundForkDevicePage.DEBUG -> viewModel.openDebugPage()
            }
        },
        onDiscovery = viewModel::openDiscoveryScreen,
        onKey = viewModel::sendTransportKey,
        onVolumeChanged = viewModel::onVolumeDraftChanged,
        onApplyVolume = viewModel::applyVolume,
        onToggleMute = viewModel::toggleMute,
        onSelectSource = viewModel::selectSource
    ) { paddingValues ->
        LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
            if (!isDeviceScreen) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            SpeakerIllustration(Modifier.size(112.dp).align(Alignment.CenterHorizontally))
                            Text(
                                text = stringResource(R.string.your_speaker),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.connection_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        Log.i(tag, "Netzwerk suchen clicked, missingPermissions=$missingPermissions")
                                        if (missingPermissions.isEmpty()) {
                                            Log.d(tag, "Starting discoverSpeakers() from UI")
                                            permissionError.value = null
                                            viewModel.discoverSpeakers()
                                        } else {
                                            Log.w(tag, "Requesting permissions before discovery")
                                            permissionLauncher.launch(missingPermissions.toTypedArray())
                                        }
                                    },
                                    enabled = !state.isDiscovering
                                ) {
                                    Text(
                                        if (state.isDiscovering) {
                                            stringResource(R.string.discovery_running)
                                        } else {
                                            stringResource(R.string.discovery_search_network)
                                        }
                                    )
                                }
                                if (state.lastSavedHost != null) {
                                    FilledTonalButton(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = viewModel::connectToLastSavedSpeaker,
                                        enabled = !state.isLoadingSnapshot && !state.isConnectingLastSpeaker
                                    ) {
                                        Text(
                                            if (state.isConnectingLastSpeaker) {
                                                stringResource(R.string.discovery_connecting)
                                            } else {
                                                stringResource(R.string.discovery_open_last)
                                            }
                                        )
                                    }
                                }
                            }
                            state.lastSavedHost?.let { host ->
                                Text(
                                    text = stringResource(
                                        R.string.discovery_last_saved,
                                        host,
                                        state.lastSavedPort
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            if (permissionError.value != null) {
                                StatusMessage(
                                    text = permissionError.value.orEmpty(),
                                    isError = true
                                )
                            }
                            if (state.discoveryError != null) {
                                StatusMessage(
                                    text = state.discoveryError.orEmpty(),
                                    isError = true
                                )
                            }
                        }
                    }
                }

                item {
                    ManualConnectCard(
                        hostInput = state.manualHostInput,
                        onHostInputChanged = viewModel::onManualHostInputChanged,
                        onConnect = viewModel::connectUsingManualHost,
                        enabled = !state.isLoadingSnapshot
                    )
                }

                if (state.discoveredSpeakers.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.discovery_found_speakers),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(state.discoveredSpeakers, key = { it.host }) { speaker ->
                        DiscoveredSpeakerCard(
                            speaker = speaker,
                            onConnect = { viewModel.connectToHost(speaker.host, speaker.port) },
                            enabled = !state.isLoadingSnapshot
                        )
                    }
                }
            } else {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                when (state.devicePage) {
                    SoundForkDevicePage.RADIO -> {
                        item {
                            NowPlayingCard(
                                nowPlaying = state.nowPlaying,
                                station = state.currentRadioStation
                            )
                        }

                        if (state.recentRadioStations.isNotEmpty()) {
                            item {
                                RecentRadioCarousel(
                                    stations = state.recentRadioStations,
                                    activeStation = state.currentRadioStation,
                                    isStarting = state.isStartingDlnaStream,
                                    onPlayStation = { recentStation ->
                                        viewModel.playRadioStation(recentStation.asRadioStation())
                                    }
                                )
                            }
                        }

                        item {
                            RadioStationListCard(
                                stations = state.radioStations,
                                activeStation = state.currentRadioStation,
                                searchQuery = state.radioSearchQuery,
                                favoriteStreamUrls = state.favoriteRadioStreamUrls,
                                status = state.dlnaStreamStatus,
                                isStarting = state.isStartingDlnaStream,
                                onSearchQueryChanged = viewModel::onRadioSearchQueryChanged,
                                onToggleFavorite = viewModel::toggleRadioFavorite,
                                onPlayStation = viewModel::playRadioStation
                            )
                        }

                    }

                    SoundForkDevicePage.DEVICE -> {
                        state.selectedHost?.let { selectedHost ->
                            item {
                                SelectedDeviceCard(
                                    host = selectedHost,
                                    port = state.selectedPort,
                                    name = state.selectedSummary?.name,
                                    type = state.selectedSummary?.type,
                                    deviceId = state.selectedSummary?.deviceId,
                                    nameDraft = state.deviceNameDraft,
                                    realtimeStatus = state.realtimeStatus,
                                    realtimeError = state.realtimeError,
                                    isRefreshing = state.isLoadingSnapshot,
                                    isRenaming = state.isRenamingDevice,
                                    onNameChanged = viewModel::onDeviceNameDraftChanged,
                                    onApplyName = viewModel::applyDeviceName,
                                    onRefresh = viewModel::refreshSelectedHost
                                )
                            }
                        }

                        item {
                            VolumeControlCard(
                                volumeDraft = state.volumeDraft,
                                volumeState = state.selectedVolume,
                                isApplying = state.isApplyingVolume,
                                onVolumeChanged = viewModel::onVolumeDraftChanged,
                                onApplyVolume = viewModel::applyVolume,
                                onToggleMute = viewModel::toggleMute
                            )
                        }

                        item {
                            BassControlCard(
                                bassDraft = state.bassDraft,
                                bassState = state.selectedBass,
                                isApplying = state.isApplyingBass,
                                onBassChanged = viewModel::onBassDraftChanged,
                                onApplyBass = viewModel::applyBass
                            )
                        }

                        item {
                            ToneControlCard(
                                toneControls = state.selectedToneControls,
                                trebleDraft = state.trebleDraft,
                                isApplying = state.isApplyingToneControls,
                                onTrebleChanged = viewModel::onTrebleDraftChanged,
                                onApplyTreble = viewModel::applyTreble
                            )
                        }

                        item {
                            DynamicAudioControlsCard(endpointResults = state.endpointResults)
                        }
                    }

                    SoundForkDevicePage.SOURCE -> {
                        item {
                            SourceListCard(
                                sources = state.sources,
                                currentSource = state.currentSource,
                                status = state.sourceStatus,
                                isSelecting = state.isSelectingSource,
                                onSelectSource = viewModel::selectSource
                            )
                        }
                    }

                    SoundForkDevicePage.PRESETS -> {
                        item {
                            PresetListCard(
                                presets = state.presets,
                                status = state.presetStatus,
                                enabled = !state.isSendingPresetCommand,
                                onPlayPreset = viewModel::playPreset,
                                onSavePreset = viewModel::saveCurrentAsPreset,
                                stations = state.radioStations
                            )
                        }
                    }

                    SoundForkDevicePage.RECENTS -> {
                        item {
                            BoseRecentsCard(
                                recents = state.boseRecents,
                                status = state.recentStatus,
                                enabled = !state.isSendingRecentCommand,
                                onPlayRecent = viewModel::playBoseRecent
                            )
                        }
                    }

                    SoundForkDevicePage.ZONES -> {
                        item {
                            ZoneInfoCard(
                                zoneState = state.zoneState,
                                zoneXml = state.zoneXml,
                                discoveredSpeakers = state.discoveredSpeakers,
                                selectedHost = state.selectedHost,
                                isUpdating = state.isUpdatingZone,
                                status = state.zoneStatus,
                                onSetZoneWithSpeaker = viewModel::setZoneWithSpeaker,
                                onAddZoneSlave = viewModel::addZoneSlave,
                                onRemoveZoneSlave = viewModel::removeZoneSlave
                            )
                        }
                    }

                    SoundForkDevicePage.DEBUG -> {
                        item {
                            val debugResultKey = state.debugEndpointResult?.let { resultKey(it) } ?: "debug-endpoint"
                            ApiTestCard(
                                endpointInput = state.debugEndpointInput,
                                result = state.debugEndpointResult,
                                status = state.debugEndpointStatus,
                                isTesting = state.isTestingDebugEndpoint,
                                expanded = expandedResults[debugResultKey] == true,
                                onEndpointInputChanged = viewModel::onDebugEndpointInputChanged,
                                onTestEndpoint = viewModel::testDebugEndpoint,
                                onToggleExpand = {
                                    expandedResults[debugResultKey] = expandedResults[debugResultKey] != true
                                }
                            )
                        }

                        item {
                            DlnaStreamCard(
                                streamUrl = state.dlnaStreamUrlInput,
                                status = state.dlnaStreamStatus,
                                isStarting = state.isStartingDlnaStream,
                                onStreamUrlChanged = viewModel::onDlnaStreamUrlChanged,
                                onPlay = viewModel::playDlnaStream
                            )
                        }

                        item {
                            ReportCard(
                                enabled = state.endpointResults.isNotEmpty(),
                                onSendReport = {
                                    shareSpeakerReport(context, state)
                                }
                            )
                        }
                    }
                }

                if (state.isLoadingSnapshot) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (state.snapshotError != null) {
                    item {
                        StatusMessage(
                            text = state.snapshotError.orEmpty(),
                            isError = true
                        )
                    }
                }

                if (state.devicePage == SoundForkDevicePage.DEBUG && state.endpointResults.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.read_api_endpoints),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    items(
                        items = state.endpointResults,
                        key = { result -> resultKey(result) }
                    ) { result ->
                        val key = resultKey(result)
                        val expanded = expandedResults[key] == true
                        EndpointResultCard(
                            result = result,
                            expanded = expanded,
                            onToggleExpand = {
                                expandedResults[key] = !expanded
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
            }
        }
}


private const val TAG = "SoundForkUI"
