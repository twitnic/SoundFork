package ninja.richter.soundfork.model

import ninja.richter.soundfork.data.SoundTouchSnapshot

fun SoundForkUiState.connectingTo(host: String, port: Int, reconnecting: Boolean, status: String): SoundForkUiState =
    clearedDevice().copy(
        screenMode = SoundForkScreenMode.DEVICE, devicePage = SoundForkDevicePage.RADIO,
        selectedHost = host, selectedPort = port, isLoadingSnapshot = true,
        isConnectingLastSpeaker = reconnecting, realtimeStatus = status, discoveryError = null
    )

fun SoundForkUiState.connectedTo(host: String, port: Int, snapshot: SoundTouchSnapshot, station: RadioStation?): SoundForkUiState = copy(
    isLoadingSnapshot = false, isConnectingLastSpeaker = false,
    selectedHost = host, selectedPort = port, selectedSummary = snapshot.summary,
    selectedVolume = snapshot.volumeState, selectedBass = snapshot.bassState,
    selectedToneControls = snapshot.toneControlState, currentRadioStation = station, nowPlaying = snapshot.nowPlaying,
    presets = snapshot.presets, boseRecents = snapshot.recents, sources = snapshot.sources,
    currentSource = snapshot.currentSource, zoneState = snapshot.zoneState, zoneXml = snapshot.zoneXml,
    deviceNameDraft = snapshot.summary?.name.orEmpty(),
    volumeDraft = (snapshot.volumeState?.targetVolume ?: snapshot.volumeState?.actualVolume ?: 0).toFloat(),
    bassDraft = (snapshot.bassState?.targetBass ?: snapshot.bassState?.actualBass ?: 0).toFloat(),
    trebleDraft = (snapshot.toneControlState?.treble?.value ?: 0).toFloat(),
    endpointResults = snapshot.endpointResults, snapshotError = null
)

fun SoundForkUiState.disconnected(error: String? = null): SoundForkUiState = clearedDevice().copy(
    screenMode = SoundForkScreenMode.DISCOVERY, selectedHost = null, discoveryError = error
)

private fun SoundForkUiState.clearedDevice(): SoundForkUiState = copy(
    selectedSummary = null, selectedVolume = null, selectedBass = null, selectedToneControls = null,
    currentRadioStation = null, nowPlaying = null, currentSource = null, sources = emptyList(),
    presets = emptyList(), boseRecents = emptyList(), zoneState = null, zoneXml = null,
    deviceNameDraft = "", volumeDraft = 0f, bassDraft = 0f, trebleDraft = 0f,
    isLoadingSnapshot = false, isConnectingLastSpeaker = false, isRenamingDevice = false,
    isApplyingVolume = false, isApplyingBass = false, isApplyingToneControls = false,
    isStartingDlnaStream = false, isSendingTransportKey = false, isSendingPresetCommand = false,
    isSendingRecentCommand = false, isTestingDebugEndpoint = false, isUpdatingZone = false, isSelectingSource = false,
    presetStatus = null, sourceStatus = null, recentStatus = null, zoneStatus = null,
    dlnaStreamStatus = null, debugEndpointStatus = null, debugEndpointResult = null,
    snapshotError = null, realtimeStatus = null, realtimeError = null, endpointResults = emptyList()
)
