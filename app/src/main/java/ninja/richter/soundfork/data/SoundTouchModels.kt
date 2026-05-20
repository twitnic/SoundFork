package ninja.richter.soundfork.data

data class EndpointDescriptor(
    val title: String,
    val aliases: List<String>,
    val optional: Boolean = false
)

data class EndpointResult(
    val title: String,
    val requestedAliases: List<String>,
    val resolvedPath: String?,
    val httpCode: Int?,
    val body: String?,
    val isError: Boolean,
    val errorMessage: String?
)

data class DeviceSummary(
    val host: String,
    val port: Int = 8090,
    val name: String? = null,
    val type: String? = null,
    val deviceId: String? = null
)

enum class DiscoveryMethod {
    MDNS,
    SSDP
}

data class DiscoveredSpeaker(
    val host: String,
    val port: Int = 8090,
    val name: String? = null,
    val type: String? = null,
    val deviceId: String? = null,
    val methods: Set<DiscoveryMethod> = emptySet()
)

data class VolumeState(
    val targetVolume: Int? = null,
    val actualVolume: Int? = null,
    val muteEnabled: Boolean? = null
)

data class BassState(
    val targetBass: Int? = null,
    val actualBass: Int? = null
)

data class AudioControlValue(
    val value: Int? = null,
    val minValue: Int? = null,
    val maxValue: Int? = null,
    val step: Int? = null
)

data class ToneControlState(
    val bass: AudioControlValue? = null,
    val treble: AudioControlValue? = null
)

data class SourceItem(
    val source: String,
    val sourceAccount: String? = null,
    val name: String? = null,
    val status: String? = null
)

data class NowPlayingState(
    val source: String? = null,
    val sourceAccount: String? = null,
    val location: String? = null,
    val track: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val stationName: String? = null,
    val playStatus: String? = null,
    val streamType: String? = null,
    val artUrl: String? = null
)

data class PresetItem(
    val id: Int? = null,
    val name: String? = null,
    val source: String? = null,
    val sourceAccount: String? = null,
    val location: String? = null,
    val isPresetable: Boolean? = null
)

data class BoseRecentItem(
    val id: Int? = null,
    val name: String? = null,
    val source: String? = null,
    val sourceAccount: String? = null,
    val location: String? = null,
    val isPresetable: Boolean? = null
)

data class ZoneMember(
    val deviceId: String? = null,
    val ipAddress: String? = null,
    val name: String? = null,
    val role: String? = null
)

data class ZoneState(
    val masterDeviceId: String? = null,
    val senderIpAddress: String? = null,
    val members: List<ZoneMember> = emptyList()
)

data class SoundTouchSnapshot(
    val summary: DeviceSummary?,
    val endpointResults: List<EndpointResult>,
    val volumeState: VolumeState? = null,
    val bassState: BassState? = null,
    val toneControlState: ToneControlState? = null,
    val sources: List<SourceItem> = emptyList(),
    val currentSource: SourceItem? = null,
    val nowPlaying: NowPlayingState? = null,
    val presets: List<PresetItem> = emptyList(),
    val recents: List<BoseRecentItem> = emptyList(),
    val zoneState: ZoneState? = null,
    val zoneXml: String? = null
)
