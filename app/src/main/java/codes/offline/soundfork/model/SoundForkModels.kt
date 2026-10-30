package codes.offline.soundfork.model

import codes.offline.soundfork.data.BassState
import codes.offline.soundfork.data.BoseRecentItem
import codes.offline.soundfork.data.DeviceSummary
import codes.offline.soundfork.data.DiscoveredSpeaker
import codes.offline.soundfork.data.EndpointResult
import codes.offline.soundfork.data.NowPlayingState
import codes.offline.soundfork.data.PresetItem
import codes.offline.soundfork.data.SourceItem
import codes.offline.soundfork.data.SoundTouchRepository
import codes.offline.soundfork.data.ToneControlState
import codes.offline.soundfork.data.VolumeState
import codes.offline.soundfork.data.ZoneState

enum class SoundForkScreenMode {
    DISCOVERY,
    DEVICE
}

enum class SoundForkDevicePage {
    RADIO,
    DEVICE,
    SOURCE,
    PRESETS,
    RECENTS,
    ZONES,
    DEBUG,
    INFO
}

data class RadioStation(
    val name: String,
    val description: String,
    val streamUrl: String,
    val radioBrowserId: String? = null
)

data class RecentRadioStation(
    val name: String,
    val description: String,
    val streamUrl: String,
    val lastPlayedAt: Long,
    val playCount: Int = 1,
    val radioBrowserId: String? = null
) {
    fun asRadioStation(): RadioStation = RadioStation(
        name = name,
        description = description,
        streamUrl = streamUrl,
        radioBrowserId = radioBrowserId
    )
}

data class SoundForkUiState(
    val screenMode: SoundForkScreenMode = SoundForkScreenMode.DISCOVERY,
    val devicePage: SoundForkDevicePage = SoundForkDevicePage.RADIO,
    val manualHostInput: String = "",
    val radioSearchQuery: String = "",
    val radioStations: List<RadioStation> = emptyList(),
    val isRadioBrowserOpen: Boolean = true,
    val radioBrowser: RadioBrowserSearchState = RadioBrowserSearchState(),
    val confirmedRadioStreamUrls: Set<String> = emptySet(),
    val favoriteRadioStreamUrls: Set<String> = emptySet(),
    val recentRadioStations: List<RecentRadioStation> = emptyList(),
    val discoveredSpeakers: List<DiscoveredSpeaker> = emptyList(),
    val lastSavedHost: String? = null,
    val lastSavedPort: Int = SoundTouchRepository.DEFAULT_PORT,
    val selectedHost: String? = null,
    val selectedPort: Int = SoundTouchRepository.DEFAULT_PORT,
    val selectedSummary: DeviceSummary? = null,
    val selectedVolume: VolumeState? = null,
    val selectedBass: BassState? = null,
    val selectedToneControls: ToneControlState? = null,
    val currentRadioStation: RadioStation? = null,
    val nowPlaying: NowPlayingState? = null,
    val presets: List<PresetItem> = emptyList(),
    val boseRecents: List<BoseRecentItem> = emptyList(),
    val sources: List<SourceItem> = emptyList(),
    val currentSource: SourceItem? = null,
    val zoneState: ZoneState? = null,
    val zoneXml: String? = null,
    val deviceNameDraft: String = "",
    val volumeDraft: Float = 0f,
    val bassDraft: Float = 0f,
    val trebleDraft: Float = 0f,
    val dlnaStreamUrlInput: String = "",
    val debugEndpointInput: String = "/now_playing",
    val debugEndpointResult: EndpointResult? = null,
    val endpointResults: List<EndpointResult> = emptyList(),
    val isDiscovering: Boolean = false,
    val isConnectingLastSpeaker: Boolean = false,
    val isLoadingSnapshot: Boolean = false,
    val isRenamingDevice: Boolean = false,
    val isApplyingVolume: Boolean = false,
    val isApplyingBass: Boolean = false,
    val isApplyingToneControls: Boolean = false,
    val isStartingDlnaStream: Boolean = false,
    val isSendingTransportKey: Boolean = false,
    val isSendingPresetCommand: Boolean = false,
    val isSendingRecentCommand: Boolean = false,
    val isTestingDebugEndpoint: Boolean = false,
    val isUpdatingZone: Boolean = false,
    val isSelectingSource: Boolean = false,
    val discoveryError: String? = null,
    val snapshotError: String? = null,
    val presetStatus: String? = null,
    val recentStatus: String? = null,
    val sourceStatus: String? = null,
    val zoneStatus: String? = null,
    val debugEndpointStatus: String? = null,
    val dlnaStreamStatus: String? = null,
    val realtimeStatus: String? = null,
    val realtimeError: String? = null
)
