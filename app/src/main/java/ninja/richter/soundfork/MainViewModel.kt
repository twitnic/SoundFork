package ninja.richter.soundfork

import android.app.Application
import android.content.Context
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ninja.richter.soundfork.data.BassState
import ninja.richter.soundfork.data.BoseRecentItem
import ninja.richter.soundfork.data.DeviceSummary
import ninja.richter.soundfork.data.DiscoveredSpeaker
import ninja.richter.soundfork.data.EndpointResult
import ninja.richter.soundfork.data.GabboUpdate
import ninja.richter.soundfork.data.GabboUpdateType
import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.data.PresetItem
import ninja.richter.soundfork.data.RecentRadioStore
import ninja.richter.soundfork.data.SourceItem
import ninja.richter.soundfork.data.SoundTouchDiscovery
import ninja.richter.soundfork.data.SoundTouchGabboClient
import ninja.richter.soundfork.data.SoundTouchXml
import ninja.richter.soundfork.data.SoundTouchRepository
import ninja.richter.soundfork.data.ZoneMember
import ninja.richter.soundfork.data.ZoneState
import ninja.richter.soundfork.model.RadioStation
import ninja.richter.soundfork.model.RecentRadioStation
import ninja.richter.soundfork.model.SoundForkDevicePage
import ninja.richter.soundfork.model.SoundForkScreenMode
import ninja.richter.soundfork.model.SoundForkUiState
import ninja.richter.soundfork.media.SoundForkMediaService
import ninja.richter.soundfork.usecase.DeviceControlUseCase
import ninja.richter.soundfork.usecase.GabboController
import ninja.richter.soundfork.usecase.PlaybackUseCase
import ninja.richter.soundfork.usecase.PresetUseCase
import ninja.richter.soundfork.usecase.ZoneUseCase
import kotlin.math.roundToInt

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SoundTouchRepository()
    private val deviceControlUseCase = DeviceControlUseCase(repository)
    private val playbackUseCase = PlaybackUseCase(repository)
    private val presetUseCase = PresetUseCase(repository)
    private val zoneUseCase = ZoneUseCase(repository)
    private val discovery = SoundTouchDiscovery(application.applicationContext, repository)
    private val gabboController = GabboController(SoundTouchGabboClient())
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val recentRadioStore = RecentRadioStore(preferences)
    private val initialRadioStations = loadRadioStations(application)
    private val initialFavoriteRadioStreamUrls = loadFavoriteRadioStreamUrls()
    private var nowPlayingRefreshJob: Job? = null
    private var gabboSnapshotRefreshJob: Job? = null

    private val _uiState = MutableStateFlow(
        SoundForkUiState(
            radioStations = initialRadioStations,
            favoriteRadioStreamUrls = initialFavoriteRadioStreamUrls,
            recentRadioStations = recentRadioStore.load(initialRadioStations)
        )
    )
    val uiState: StateFlow<SoundForkUiState> = _uiState.asStateFlow()

    init {
        val lastHost = preferences.getString(KEY_LAST_HOST, null)?.takeIf { it.isNotBlank() }
        val lastPort = preferences.getInt(KEY_LAST_PORT, SoundTouchRepository.DEFAULT_PORT)
        _uiState.update {
            it.copy(
                lastSavedHost = lastHost,
                lastSavedPort = lastPort
            )
        }
        connectToLastSpeakerIfAvailable()
    }

    fun onManualHostInputChanged(value: String) {
        _uiState.update { it.copy(manualHostInput = value.trim()) }
    }

    fun onRadioSearchQueryChanged(value: String) {
        _uiState.update { it.copy(radioSearchQuery = value) }
    }

    fun toggleRadioFavorite(station: RadioStation) {
        val key = station.favoriteKey()
        if (key.isBlank()) {
            return
        }
        _uiState.update { state ->
            val updatedFavorites = if (key in state.favoriteRadioStreamUrls) {
                state.favoriteRadioStreamUrls - key
            } else {
                state.favoriteRadioStreamUrls + key
            }
            persistFavoriteRadioStreamUrls(updatedFavorites)
            state.copy(favoriteRadioStreamUrls = updatedFavorites)
        }
    }

    fun discoverSpeakers() {
        if (_uiState.value.isDiscovering) {
            Log.i(TAG, "discoverSpeakers() ignored: already discovering")
            return
        }
        Log.i(TAG, "discoverSpeakers() start")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDiscovering = true,
                    discoveryError = null
                )
            }

            val result = runCatching { discovery.discover() }
            result.onSuccess { speakers ->
                val visibleSpeakers = speakers.filter { speaker ->
                    !speaker.type.isNullOrBlank() || !speaker.deviceId.isNullOrBlank()
                }
                Log.i(
                    TAG,
                    "discoverSpeakers() success count=${speakers.size} " +
                        "visible=${visibleSpeakers.size}"
                )
                _uiState.update {
                    it.copy(
                        isDiscovering = false,
                        discoveredSpeakers = visibleSpeakers,
                        discoveryError = null
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "discoverSpeakers() failed: ${throwable.message}")
                _uiState.update {
                    it.copy(
                        isDiscovering = false,
                        discoveryError = throwable.message ?: "Netzwerksuche fehlgeschlagen"
                    )
                }
            }
        }
    }

    fun connectUsingManualHost() {
        val host = _uiState.value.manualHostInput.trim()
        if (host.isBlank()) {
            Log.w(TAG, "connectUsingManualHost() ignored: empty input")
            _uiState.update { it.copy(snapshotError = "Bitte eine IP-Adresse oder Hostname eingeben") }
            return
        }
        Log.i(TAG, "connectUsingManualHost() host=$host")
        connectToHost(host)
    }

    fun connectToLastSavedSpeaker() {
        val state = _uiState.value
        val host = state.lastSavedHost?.takeIf { it.isNotBlank() } ?: return
        Log.i(TAG, "connectToLastSavedSpeaker() host=$host port=${state.lastSavedPort}")
        connectToHost(
            host = host,
            port = state.lastSavedPort,
            fallbackToDiscoveryOnFailure = false
        )
    }

    fun connectToHost(
        host: String,
        port: Int = SoundTouchRepository.DEFAULT_PORT
    ) {
        connectToHost(host = host, port = port, fallbackToDiscoveryOnFailure = false)
    }

    private fun connectToHost(
        host: String,
        port: Int = SoundTouchRepository.DEFAULT_PORT,
        fallbackToDiscoveryOnFailure: Boolean
    ) {
        Log.i(
            TAG,
            "connectToHost() start host=$host port=$port fallbackToDiscovery=$fallbackToDiscoveryOnFailure"
        )
        stopGabboUpdates(clearStatus = false)
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    screenMode = SoundForkScreenMode.DEVICE,
                    devicePage = SoundForkDevicePage.RADIO,
                    selectedHost = host,
                    selectedPort = port,
                    isLoadingSnapshot = true,
                    isConnectingLastSpeaker = fallbackToDiscoveryOnFailure,
                    snapshotError = null,
                    presetStatus = null,
                    sourceStatus = null,
                    realtimeStatus = "Live-Updates werden verbunden...",
                    realtimeError = null,
                    endpointResults = emptyList()
                )
            }

            val snapshotResult = runCatching {
                repository.loadSnapshot(host, port)
            }

            snapshotResult.onSuccess { snapshot ->
                Log.i(TAG, "connectToHost() success host=$host summary=${snapshot.summary?.name}")
                saveLastSpeaker(host, port)
                val initialVolume = snapshot.volumeState
                    ?.targetVolume
                    ?: snapshot.volumeState?.actualVolume
                    ?: 0
                val initialBass = snapshot.bassState
                    ?.targetBass
                    ?: snapshot.bassState?.actualBass
                    ?: 0
                val initialTreble = snapshot.toneControlState
                    ?.treble
                    ?.value
                    ?: 0
                val currentRadioStation = radioStationFromNowPlaying(
                    nowPlaying = snapshot.nowPlaying,
                    stations = _uiState.value.radioStations
                )
                _uiState.update {
                    it.copy(
                        isLoadingSnapshot = false,
                        isConnectingLastSpeaker = false,
                        selectedHost = host,
                        selectedPort = port,
                        selectedSummary = snapshot.summary,
                        selectedVolume = snapshot.volumeState,
                        selectedBass = snapshot.bassState,
                        selectedToneControls = snapshot.toneControlState,
                        currentRadioStation = currentRadioStation,
                        nowPlaying = snapshot.nowPlaying,
                        presets = snapshot.presets,
                        boseRecents = snapshot.recents,
                        sources = snapshot.sources,
                        currentSource = snapshot.currentSource,
                        zoneState = snapshot.zoneState,
                        zoneXml = snapshot.zoneXml,
                        deviceNameDraft = snapshot.summary?.name.orEmpty(),
                        volumeDraft = initialVolume.toFloat(),
                        bassDraft = initialBass.toFloat(),
                        trebleDraft = initialTreble.toFloat(),
                        endpointResults = snapshot.endpointResults,
                        snapshotError = null
                    )
                }
                updateMediaNotification()
                startGabboUpdates(host, port)
                startNowPlayingRefresh(host, port)
                currentRadioStation?.let(::recordRecentRadioStation)
            }.onFailure { throwable ->
                Log.w(TAG, "connectToHost() failed host=$host error=${throwable.message}", throwable)
                stopGabboUpdates(clearStatus = false)
                _uiState.update {
                    it.copy(
                        isLoadingSnapshot = false,
                        isConnectingLastSpeaker = false,
                        screenMode = if (fallbackToDiscoveryOnFailure) {
                            SoundForkScreenMode.DISCOVERY
                        } else {
                            SoundForkScreenMode.DEVICE
                        },
                        selectedHost = if (fallbackToDiscoveryOnFailure) null else host,
                        selectedPort = if (fallbackToDiscoveryOnFailure) {
                            SoundTouchRepository.DEFAULT_PORT
                        } else {
                            port
                        },
                        selectedSummary = null,
                        selectedVolume = null,
                        selectedBass = null,
                        selectedToneControls = null,
                        currentRadioStation = null,
                        nowPlaying = null,
                        presets = emptyList(),
                        boseRecents = emptyList(),
                        sources = emptyList(),
                        currentSource = null,
                        zoneState = null,
                        zoneXml = null,
                        deviceNameDraft = "",
                        trebleDraft = 0f,
                        endpointResults = emptyList(),
                        snapshotError = if (fallbackToDiscoveryOnFailure) {
                            null
                        } else {
                            throwable.message ?: "Gerät konnte nicht ausgelesen werden"
                        },
                        realtimeStatus = null,
                        realtimeError = null
                    )
                }
                if (fallbackToDiscoveryOnFailure) {
                    stopNowPlayingRefresh()
                }
            }
        }
    }

    fun onVolumeDraftChanged(value: Float) {
        _uiState.update {
            it.copy(volumeDraft = value.coerceIn(0f, 100f))
        }
    }

    fun onDeviceNameDraftChanged(value: String) {
        _uiState.update {
            it.copy(deviceNameDraft = value.take(MAX_DEVICE_NAME_LENGTH), snapshotError = null)
        }
    }

    fun applyDeviceName() {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isRenamingDevice) {
            return
        }
        val name = state.deviceNameDraft.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(snapshotError = "Gerätename darf nicht leer sein") }
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "applyDeviceName() host=$host port=$port name=$name")
        viewModelScope.launch {
            _uiState.update { it.copy(isRenamingDevice = true, snapshotError = null) }
            runCatching {
                deviceControlUseCase.renameDevice(host = host, port = port, name = name)
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        isRenamingDevice = false,
                        selectedSummary = summary ?: it.selectedSummary?.copy(name = name),
                        deviceNameDraft = summary?.name ?: name
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "applyDeviceName() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isRenamingDevice = false,
                        snapshotError = throwable.message ?: "Gerätename konnte nicht gesetzt werden"
                    )
                }
            }
        }
    }

    fun applyVolume() {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isApplyingVolume) {
            return
        }
        val targetVolume = state.volumeDraft.roundToInt().coerceIn(0, 100)
        val mute = state.selectedVolume?.muteEnabled
        val port = state.selectedPort

        Log.i(TAG, "applyVolume() host=$host port=$port targetVolume=$targetVolume mute=$mute")
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingVolume = true, snapshotError = null) }
            runCatching {
                deviceControlUseCase.setVolume(
                    host = host,
                    port = port,
                    targetVolume = targetVolume,
                    muteEnabled = mute
                )
            }.onSuccess { volumeState ->
                _uiState.update {
                    it.copy(
                        isApplyingVolume = false,
                        selectedVolume = volumeState,
                        volumeDraft = (volumeState.targetVolume ?: targetVolume).toFloat()
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "applyVolume() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isApplyingVolume = false,
                        snapshotError = throwable.message ?: "Lautstaerke konnte nicht gesetzt werden"
                    )
                }
            }
        }
    }

    fun toggleMute() {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isApplyingVolume) {
            return
        }
        val port = state.selectedPort
        val targetMute = !(state.selectedVolume?.muteEnabled ?: false)
        val currentTarget = state.volumeDraft.roundToInt().coerceIn(0, 100)

        Log.i(TAG, "toggleMute() host=$host port=$port targetMute=$targetMute")
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingVolume = true, snapshotError = null) }
            runCatching {
                deviceControlUseCase.setMute(
                    host = host,
                    port = port,
                    muteEnabled = targetMute,
                    targetVolume = currentTarget
                )
            }.onSuccess { volumeState ->
                _uiState.update {
                    it.copy(
                        isApplyingVolume = false,
                        selectedVolume = volumeState,
                        volumeDraft = (volumeState.targetVolume ?: currentTarget).toFloat()
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "toggleMute() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isApplyingVolume = false,
                        snapshotError = throwable.message ?: "Mute konnte nicht gesetzt werden"
                    )
                }
            }
        }
    }

    fun onBassDraftChanged(value: Float) {
        _uiState.update {
            it.copy(bassDraft = value.coerceIn(-9f, 9f))
        }
    }

    fun applyBass() {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isApplyingBass) {
            return
        }
        val port = state.selectedPort
        val targetBass = state.bassDraft.roundToInt().coerceIn(-9, 9)

        Log.i(TAG, "applyBass() host=$host port=$port targetBass=$targetBass")
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingBass = true, snapshotError = null) }
            runCatching {
                deviceControlUseCase.setBass(
                    host = host,
                    port = port,
                    targetBass = targetBass
                )
            }.onSuccess { bassState ->
                _uiState.update {
                    it.copy(
                        isApplyingBass = false,
                        selectedBass = bassState,
                        bassDraft = (bassState.targetBass ?: targetBass).toFloat()
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "applyBass() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isApplyingBass = false,
                        snapshotError = throwable.message ?: "Bass konnte nicht gesetzt werden"
                    )
                }
            }
        }
    }

    fun onTrebleDraftChanged(value: Float) {
        val treble = _uiState.value.selectedToneControls?.treble
        val min = (treble?.minValue ?: -10).toFloat()
        val max = (treble?.maxValue ?: 10).toFloat()
        _uiState.update {
            it.copy(trebleDraft = value.coerceIn(min, max))
        }
    }

    fun applyTreble() {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isApplyingToneControls) {
            return
        }
        val port = state.selectedPort
        val treble = state.selectedToneControls?.treble
        val min = treble?.minValue ?: -10
        val max = treble?.maxValue ?: 10
        val targetTreble = state.trebleDraft.roundToInt().coerceIn(min, max)

        Log.i(TAG, "applyTreble() host=$host port=$port targetTreble=$targetTreble")
        viewModelScope.launch {
            _uiState.update { it.copy(isApplyingToneControls = true, snapshotError = null) }
            runCatching {
                deviceControlUseCase.setTreble(
                    host = host,
                    port = port,
                    targetTreble = targetTreble
                )
            }.onSuccess { toneControls ->
                _uiState.update {
                    it.copy(
                        isApplyingToneControls = false,
                        selectedToneControls = toneControls,
                        trebleDraft = (toneControls.treble?.value ?: targetTreble).toFloat()
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "applyTreble() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isApplyingToneControls = false,
                        snapshotError = throwable.message ?: "Treble konnte nicht gesetzt werden"
                    )
                }
            }
        }
    }

    fun onDlnaStreamUrlChanged(value: String) {
        _uiState.update {
            it.copy(
                dlnaStreamUrlInput = value.trim(),
                dlnaStreamStatus = null
            )
        }
    }

    fun onDebugEndpointInputChanged(value: String) {
        _uiState.update {
            it.copy(
                debugEndpointInput = value.trim(),
                debugEndpointStatus = null
            )
        }
    }

    fun testDebugEndpoint() {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        val path = state.debugEndpointInput.trim()
        if (path.isBlank()) {
            _uiState.update { it.copy(debugEndpointStatus = "Bitte einen API-Pfad eingeben") }
            return
        }
        if (state.isTestingDebugEndpoint) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "testDebugEndpoint() host=$host port=$port path=$path")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestingDebugEndpoint = true,
                    debugEndpointStatus = null
                )
            }
            runCatching {
                repository.readEndpoint(host = host, port = port, path = path)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isTestingDebugEndpoint = false,
                        debugEndpointResult = result,
                        debugEndpointStatus = "API-Test abgeschlossen: HTTP ${result.httpCode ?: "n/a"}"
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "testDebugEndpoint() failed host=$host path=$path error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isTestingDebugEndpoint = false,
                        debugEndpointStatus = throwable.message ?: "API-Test fehlgeschlagen"
                    )
                }
            }
        }
    }

    fun playDlnaStream() {
        val state = _uiState.value
        val streamUrl = state.dlnaStreamUrlInput.trim()
        if (streamUrl.isBlank()) {
            _uiState.update { it.copy(dlnaStreamStatus = "Bitte eine Stream-URL eingeben") }
            return
        }
        playDlnaStreamUrl(streamUrl)
    }

    fun playRadioChemnitz() {
        playRadioStation(_uiState.value.radioStations.firstOrNull() ?: FALLBACK_RADIO_STATION)
    }

    fun playRadioStation(station: RadioStation) {
        _uiState.update {
            it.copy(
                currentRadioStation = station,
                dlnaStreamUrlInput = station.streamUrl,
                dlnaStreamStatus = null
            )
        }
        playDlnaStreamUrl(station.streamUrl, station)
    }

    private fun playDlnaStreamUrl(
        streamUrl: String,
        stationToRecord: RadioStation? = null
    ) {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isStartingDlnaStream) {
            return
        }

        Log.i(TAG, "playDlnaStream() host=$host url=$streamUrl")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isStartingDlnaStream = true,
                    dlnaStreamStatus = null
                )
            }
            runCatching {
                playbackUseCase.playDlnaStream(
                    host = host,
                    streamUrl = streamUrl
                )
            }.onSuccess {
                stationToRecord?.let(::recordRecentRadioStation)
                refreshNowPlayingOnce(host, state.selectedPort)
                updateMediaNotification()
                _uiState.update {
                    it.copy(
                        isStartingDlnaStream = false,
                        dlnaStreamStatus = "DLNA Stream wurde gestartet"
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "playDlnaStream() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isStartingDlnaStream = false,
                        dlnaStreamStatus = throwable.message ?: "DLNA Stream konnte nicht gestartet werden"
                    )
                }
            }
        }
    }

    fun sendTransportKey(key: String) {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isSendingTransportKey) {
            return
        }
        val port = state.selectedPort
        val normalizedKey = key.trim().uppercase()

        Log.i(TAG, "sendTransportKey() host=$host port=$port key=$normalizedKey")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSendingTransportKey = true,
                    snapshotError = null,
                    dlnaStreamStatus = null
                )
            }
            runCatching {
                if (normalizedKey in DLNA_TRANSPORT_KEYS) {
                    repository.sendDlnaTransportAction(
                        host = host,
                        action = normalizedKey
                    )
                } else {
                    playbackUseCase.sendTransportKey(
                        host = host,
                        port = port,
                        key = normalizedKey
                    )
                }
            }.onSuccess {
                refreshNowPlayingOnce(host, port)
                updateMediaNotification()
                _uiState.update {
                    it.copy(
                        isSendingTransportKey = false,
                        dlnaStreamStatus = "$normalizedKey gesendet"
                    )
                }
            }.onFailure { throwable ->
                Log.w(
                    TAG,
                    "sendTransportKey() failed host=$host key=$normalizedKey error=${throwable.message}",
                    throwable
                )
                _uiState.update {
                    it.copy(
                        isSendingTransportKey = false,
                        snapshotError = throwable.message ?: "Taste konnte nicht gesendet werden"
                    )
                }
            }
        }
    }

    fun selectSource(source: SourceItem) {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isSelectingSource) {
            return
        }
        val port = state.selectedPort
        Log.i(
            TAG,
            "selectSource() host=$host port=$port source=${source.source} account=${source.sourceAccount}"
        )
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSelectingSource = true,
                    snapshotError = null,
                    sourceStatus = null
                )
            }
            runCatching {
                repository.selectSource(host = host, port = port, source = source)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSelectingSource = false,
                        currentSource = source,
                        sourceStatus = "Quelle ${source.source} wurde ausgewaehlt"
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "selectSource() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isSelectingSource = false,
                        sourceStatus = throwable.message ?: "Quelle konnte nicht ausgewaehlt werden"
                    )
                }
            }
        }
    }

    fun playPreset(preset: PresetItem?, presetId: Int) {
        val id = preset?.id?.coerceIn(1, 6) ?: presetId.coerceIn(1, 6)
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isSendingPresetCommand) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "playPreset() host=$host port=$port presetId=$id")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSendingPresetCommand = true,
                    snapshotError = null,
                    presetStatus = null
                )
            }
            runCatching {
                presetUseCase.playPreset(host = host, port = port, preset = preset, presetId = id)
                refreshNowPlayingOnce(host, port)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSendingPresetCommand = false,
                        currentRadioStation = preset?.toRadioStation(id) ?: it.currentRadioStation,
                        presetStatus = "Preset $id wurde gestartet",
                        dlnaStreamStatus = "Preset $id wurde gestartet"
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "playPreset() failed host=$host presetId=$id error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isSendingPresetCommand = false,
                        presetStatus = throwable.message ?: "Preset konnte nicht gestartet werden"
                    )
                }
            }
        }
    }

    fun playBoseRecent(recent: BoseRecentItem) {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isSendingRecentCommand) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "playBoseRecent() host=$host port=$port source=${recent.source} location=${recent.location}")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSendingRecentCommand = true,
                    snapshotError = null,
                    recentStatus = null
                )
            }
            runCatching {
                presetUseCase.playRecent(host = host, port = port, recent = recent)
                refreshNowPlayingOnce(host, port)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSendingRecentCommand = false,
                        currentRadioStation = recent.toRadioStation() ?: it.currentRadioStation,
                        recentStatus = "Recent wurde gestartet",
                        dlnaStreamStatus = "Recent wurde gestartet"
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "playBoseRecent() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isSendingRecentCommand = false,
                        recentStatus = throwable.message ?: "Recent konnte nicht gestartet werden"
                    )
                }
            }
        }
    }

    fun saveCurrentAsPreset(presetId: Int) {
        val id = presetId.coerceIn(1, 6)
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isSendingPresetCommand) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "saveCurrentAsPreset() host=$host port=$port presetId=$id")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSendingPresetCommand = true,
                    snapshotError = null,
                    presetStatus = "Preset $id wird gespeichert..."
                )
            }
            runCatching {
                presetUseCase.saveCurrentAsPreset(host = host, port = port, presetId = id)
                repository.loadSnapshot(host, port)
            }.onSuccess { snapshot ->
                val currentRadioStation = radioStationFromNowPlaying(
                    nowPlaying = snapshot.nowPlaying,
                    stations = _uiState.value.radioStations
                )
                _uiState.update {
                    it.copy(
                        isSendingPresetCommand = false,
                        presets = snapshot.presets,
                        boseRecents = snapshot.recents,
                        nowPlaying = snapshot.nowPlaying,
                        currentRadioStation = currentRadioStation ?: it.currentRadioStation,
                        zoneState = snapshot.zoneState,
                        endpointResults = snapshot.endpointResults,
                        presetStatus = "Aktueller Stream wurde auf Preset $id gespeichert"
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "saveCurrentAsPreset() failed host=$host presetId=$id error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isSendingPresetCommand = false,
                        presetStatus = throwable.message ?: "Preset konnte nicht gespeichert werden"
                    )
                }
            }
        }
    }

    fun setZoneWithSpeaker(speaker: DiscoveredSpeaker) {
        runZoneCommand(
            actionLabel = "Zone wurde erstellt",
            command = { host, port, masterDeviceId ->
                zoneUseCase.setZone(
                    host = host,
                    port = port,
                    masterDeviceId = masterDeviceId,
                    masterIpAddress = host,
                    members = listOf(speaker.toZoneMember())
                )
            }
        )
    }

    fun addZoneSlave(speaker: DiscoveredSpeaker) {
        runZoneCommand(
            actionLabel = "Lautsprecher wurde zur Zone hinzugefügt",
            command = { host, port, masterDeviceId ->
                zoneUseCase.addZoneSlave(
                    host = host,
                    port = port,
                    masterDeviceId = masterDeviceId,
                    slave = speaker.toZoneMember()
                )
            }
        )
    }

    fun removeZoneSlave(member: ZoneMember) {
        runZoneCommand(
            actionLabel = "Lautsprecher wurde aus der Zone entfernt",
            command = { host, port, masterDeviceId ->
                zoneUseCase.removeZoneSlave(
                    host = host,
                    port = port,
                    masterDeviceId = masterDeviceId,
                    slave = member
                )
            }
        )
    }

    private fun runZoneCommand(
        actionLabel: String,
        command: suspend (host: String, port: Int, masterDeviceId: String) -> ZoneState?
    ) {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        val masterDeviceId = state.selectedSummary?.deviceId
            ?: state.zoneState?.masterDeviceId
            ?: run {
                _uiState.update { it.copy(zoneStatus = "Device-ID des Masters fehlt") }
                return
            }
        if (state.isUpdatingZone) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "runZoneCommand() host=$host port=$port master=$masterDeviceId action=$actionLabel")
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdatingZone = true,
                    zoneStatus = null,
                    snapshotError = null
                )
            }
            runCatching {
                val zoneState = command(host, port, masterDeviceId)
                val snapshot = repository.loadSnapshot(host, port)
                zoneState to snapshot
            }.onSuccess { (zoneState, snapshot) ->
                _uiState.update {
                    it.copy(
                        isUpdatingZone = false,
                        zoneState = snapshot.zoneState ?: zoneState,
                        zoneXml = snapshot.zoneXml,
                        boseRecents = snapshot.recents,
                        endpointResults = snapshot.endpointResults,
                        zoneStatus = actionLabel
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "runZoneCommand() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isUpdatingZone = false,
                        zoneStatus = throwable.message ?: "Zone konnte nicht aktualisiert werden"
                    )
                }
            }
        }
    }

    fun openDiscoveryScreen() {
        Log.i(TAG, "openDiscoveryScreen()")
        stopNowPlayingRefresh()
        stopGabboUpdates(clearStatus = true)
        SoundForkMediaService.stop(getApplication())
        _uiState.update {
            it.copy(
                screenMode = SoundForkScreenMode.DISCOVERY,
                snapshotError = null
            )
        }
    }

    fun openRadioPage() {
        _uiState.update {
            it.copy(devicePage = SoundForkDevicePage.RADIO)
        }
    }

    fun openDevicePage() {
        _uiState.update {
            it.copy(devicePage = SoundForkDevicePage.DEVICE)
        }
    }

    fun openSourcePage() {
        _uiState.update {
            it.copy(devicePage = SoundForkDevicePage.SOURCE)
        }
    }

    fun openPresetsPage() {
        _uiState.update {
            it.copy(devicePage = SoundForkDevicePage.PRESETS)
        }
    }

    fun openRecentsPage() {
        _uiState.update {
            it.copy(devicePage = SoundForkDevicePage.RECENTS)
        }
    }

    fun openZonesPage() {
        _uiState.update {
            it.copy(devicePage = SoundForkDevicePage.ZONES)
        }
    }

    fun openDebugPage() {
        _uiState.update {
            it.copy(devicePage = SoundForkDevicePage.DEBUG)
        }
    }

    fun refreshSelectedHost() {
        val host = _uiState.value.selectedHost ?: return
        val port = _uiState.value.selectedPort
        connectToHost(host, port)
    }

    private fun connectToLastSpeakerIfAvailable() {
        val host = preferences.getString(KEY_LAST_HOST, null)?.takeIf { it.isNotBlank() } ?: return
        val port = preferences.getInt(KEY_LAST_PORT, SoundTouchRepository.DEFAULT_PORT)
        Log.i(TAG, "connectToLastSpeakerIfAvailable() host=$host port=$port")
        connectToHost(
            host = host,
            port = port,
            fallbackToDiscoveryOnFailure = true
        )
    }

    private fun saveLastSpeaker(host: String, port: Int) {
        preferences.edit()
            .putString(KEY_LAST_HOST, host)
            .putInt(KEY_LAST_PORT, port)
            .apply()
        _uiState.update {
            it.copy(
                lastSavedHost = host,
                lastSavedPort = port
            )
        }
        Log.d(TAG, "saveLastSpeaker() host=$host port=$port")
    }

    private fun startGabboUpdates(host: String, port: Int) {
        stopGabboUpdates(clearStatus = false)
        gabboController.connect(
            host = host,
            port = port,
            onStatus = { status ->
                _uiState.update {
                    if (it.selectedHost == host && it.selectedPort == port) {
                        it.copy(realtimeStatus = status, realtimeError = null)
                    } else {
                        it
                    }
                }
            },
            onUpdate = { update ->
                handleGabboUpdate(host, port, update)
            }
        )
    }

    private fun stopGabboUpdates(clearStatus: Boolean) {
        gabboSnapshotRefreshJob?.cancel()
        gabboSnapshotRefreshJob = null
        gabboController.stop()
        if (clearStatus) {
            _uiState.update { it.copy(realtimeStatus = null, realtimeError = null) }
        }
    }

    private fun handleGabboUpdate(host: String, port: Int, update: GabboUpdate) {
        Log.d(TAG, "handleGabboUpdate() host=$host types=${update.types}")
        _uiState.update {
            if (it.selectedHost == host && it.selectedPort == port) {
                it.copy(
                    realtimeStatus = "Live-Update: ${update.types.toDisplayText()}",
                    realtimeError = if (GabboUpdateType.ERROR in update.types) {
                        update.errorMessage ?: "Lautsprecher meldet einen Fehler"
                    } else {
                        it.realtimeError
                    }
                )
            } else {
                it
            }
        }

        if (GabboUpdateType.VOLUME in update.types) {
            refreshVolumeFromGabbo(host, port, update)
        }
        if (GabboUpdateType.BASS in update.types) {
            refreshBassFromGabbo(host, port, update)
        }
        if (GabboUpdateType.NOW_PLAYING in update.types) {
            viewModelScope.launch {
                refreshNowPlayingOnce(host, port)
            }
        }
        if (
            update.types.any {
                it == GabboUpdateType.PRESETS ||
                    it == GabboUpdateType.SOURCES ||
                    it == GabboUpdateType.ZONE ||
                    it == GabboUpdateType.INFO
            }
        ) {
            scheduleSnapshotRefreshFromGabbo(host, port)
        }
    }

    private fun refreshVolumeFromGabbo(host: String, port: Int, update: GabboUpdate) {
        viewModelScope.launch {
            val volume = SoundTouchXml.parseVolume(update.rawXml)
                ?: runCatching {
                    repository.readVolumeStateOrNull(host, port)
                }.onFailure { throwable ->
                    Log.d(TAG, "refreshVolumeFromGabbo() failed host=$host error=${throwable.message}")
                }.getOrNull()
                ?: return@launch
            _uiState.update {
                if (it.selectedHost == host && it.selectedPort == port) {
                    it.copy(
                        selectedVolume = volume,
                        volumeDraft = (volume.targetVolume ?: volume.actualVolume ?: it.volumeDraft.toInt()).toFloat()
                    )
                } else {
                    it
                }
            }
        }
    }

    private fun refreshBassFromGabbo(host: String, port: Int, update: GabboUpdate) {
        viewModelScope.launch {
            val bass = SoundTouchXml.parseBass(update.rawXml)
                ?: runCatching {
                    repository.readBassStateOrNull(host, port)
                }.onFailure { throwable ->
                    Log.d(TAG, "refreshBassFromGabbo() failed host=$host error=${throwable.message}")
                }.getOrNull()
                ?: return@launch
            _uiState.update {
                if (it.selectedHost == host && it.selectedPort == port) {
                    it.copy(
                        selectedBass = bass,
                        bassDraft = (bass.targetBass ?: bass.actualBass ?: it.bassDraft.toInt()).toFloat()
                    )
                } else {
                    it
                }
            }
        }
    }

    private fun scheduleSnapshotRefreshFromGabbo(host: String, port: Int) {
        gabboSnapshotRefreshJob?.cancel()
        gabboSnapshotRefreshJob = viewModelScope.launch {
            delay(GABBO_REFRESH_DEBOUNCE_MS)
            val snapshot = runCatching {
                repository.loadSnapshot(host, port)
            }.onFailure { throwable ->
                Log.d(TAG, "scheduleSnapshotRefreshFromGabbo() failed host=$host error=${throwable.message}")
            }.getOrNull() ?: return@launch

            val currentRadioStation = radioStationFromNowPlaying(
                nowPlaying = snapshot.nowPlaying,
                stations = _uiState.value.radioStations
            )
            _uiState.update {
                if (it.selectedHost == host && it.selectedPort == port) {
                    it.copy(
                        selectedSummary = snapshot.summary ?: it.selectedSummary,
                        selectedVolume = snapshot.volumeState ?: it.selectedVolume,
                        selectedBass = snapshot.bassState ?: it.selectedBass,
                        selectedToneControls = snapshot.toneControlState ?: it.selectedToneControls,
                        trebleDraft = (
                            snapshot.toneControlState?.treble?.value
                                ?: it.trebleDraft.toInt()
                        ).toFloat(),
                        nowPlaying = snapshot.nowPlaying ?: it.nowPlaying,
                        currentRadioStation = currentRadioStation ?: it.currentRadioStation,
                        presets = snapshot.presets,
                        boseRecents = snapshot.recents,
                        sources = snapshot.sources,
                        currentSource = snapshot.currentSource ?: it.currentSource,
                        zoneState = snapshot.zoneState,
                        zoneXml = snapshot.zoneXml,
                        endpointResults = snapshot.endpointResults
                    )
                } else {
                    it
                }
            }
            updateMediaNotification()
        }
    }

    private fun startNowPlayingRefresh(host: String, port: Int) {
        nowPlayingRefreshJob?.cancel()
        nowPlayingRefreshJob = viewModelScope.launch {
            while (true) {
                delay(NOW_PLAYING_REFRESH_INTERVAL_MS)
                refreshNowPlayingOnce(host, port)
            }
        }
    }

    private fun stopNowPlayingRefresh() {
        nowPlayingRefreshJob?.cancel()
        nowPlayingRefreshJob = null
    }

    private suspend fun refreshNowPlayingOnce(host: String, port: Int) {
        val state = _uiState.value
        if (state.selectedHost != host || state.selectedPort != port) {
            return
        }
        val nowPlaying = runCatching {
            playbackUseCase.readNowPlaying(host = host, port = port)
        }.onFailure { throwable ->
            Log.d(TAG, "refreshNowPlayingOnce() failed host=$host error=${throwable.message}")
        }.getOrNull()

        if (nowPlaying == null) {
            markPlaybackUnavailable("Lautsprecher nicht erreichbar")
            return
        }

        if (!nowPlaying.isActivePlayback()) {
            _uiState.update {
                it.copy(
                    nowPlaying = nowPlaying,
                    currentRadioStation = null,
                    dlnaStreamStatus = "Keine Wiedergabe aktiv"
                )
            }
            SoundForkMediaService.stop(getApplication())
            return
        }

        val currentRadioStation = radioStationFromNowPlaying(
            nowPlaying = nowPlaying,
            stations = _uiState.value.radioStations
        )
        val currentSource = nowPlaying.source
            ?.takeIf { it.isNotBlank() }
            ?.let { source ->
                SourceItem(
                    source = source,
                    sourceAccount = nowPlaying.sourceAccount
                )
            }
        _uiState.update {
            it.copy(
                nowPlaying = nowPlaying,
                currentRadioStation = currentRadioStation ?: it.currentRadioStation,
                currentSource = currentSource ?: it.currentSource
            )
        }
        updateMediaNotification()
    }

    private fun markPlaybackUnavailable(status: String) {
        _uiState.update {
            it.copy(
                nowPlaying = null,
                currentRadioStation = null,
                dlnaStreamStatus = status
            )
        }
        SoundForkMediaService.stop(getApplication())
    }

    private fun updateMediaNotification() {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        SoundForkMediaService.update(
            context = getApplication(),
            host = host,
            port = state.selectedPort,
            station = state.currentRadioStation,
            nowPlaying = state.nowPlaying,
            isPlaying = state.isRemotePlaybackActive()
        )
    }

    private fun loadRadioStations(context: Context): List<RadioStation> {
        return runCatching {
            val parser = context.resources.getXml(R.xml.radio_stations)
            val stations = mutableListOf<RadioStation>()
            parser.use {
                var eventType = it.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && it.name == "station") {
                        val name = it.getAttributeValue(null, "name")?.trim().orEmpty()
                        val description = it.getAttributeValue(null, "description")?.trim().orEmpty()
                        val streamUrl = it.getAttributeValue(null, "streamUrl")?.trim().orEmpty()
                        if (name.isNotBlank() && streamUrl.isNotBlank()) {
                            stations += RadioStation(
                                name = name,
                                description = description.ifBlank { "Internet Radio" },
                                streamUrl = streamUrl
                            )
                        }
                    }
                    eventType = it.next()
                }
            }
            stations.ifEmpty { listOf(FALLBACK_RADIO_STATION) }
        }.onFailure { throwable ->
            Log.w(TAG, "loadRadioStations() failed error=${throwable.message}", throwable)
        }.getOrElse {
            listOf(FALLBACK_RADIO_STATION)
        }
    }

    private fun recordRecentRadioStation(station: RadioStation) {
        val state = _uiState.value
        val updatedStations = recentRadioStore.record(
            station = station,
            currentStations = state.recentRadioStations,
            catalogStations = state.radioStations
        )
        _uiState.update {
            it.copy(recentRadioStations = updatedStations)
        }
    }

    private fun loadFavoriteRadioStreamUrls(): Set<String> {
        return preferences
            .getStringSet(KEY_FAVORITE_RADIO_STREAM_URLS, emptySet())
            .orEmpty()
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun persistFavoriteRadioStreamUrls(streamUrls: Set<String>) {
        preferences.edit()
            .putStringSet(KEY_FAVORITE_RADIO_STREAM_URLS, streamUrls)
            .apply()
    }

    private fun RadioStation.favoriteKey(): String {
        return streamUrl.trim()
    }

    private fun DiscoveredSpeaker.toZoneMember(): ZoneMember {
        return ZoneMember(
            deviceId = deviceId,
            ipAddress = host,
            name = name,
            role = null
        )
    }

    private fun PresetItem.toRadioStation(presetId: Int): RadioStation? {
        val streamUrl = location?.takeIf { it.isNotBlank() } ?: return null
        val matchingStation = _uiState.value.radioStations.firstOrNull { station ->
            station.streamUrl.equals(streamUrl, ignoreCase = true)
        }
        return matchingStation ?: RadioStation(
            name = name?.takeIf { it.isNotBlank() } ?: "Preset $presetId",
            description = streamUrl,
            streamUrl = streamUrl
        )
    }

    private fun BoseRecentItem.toRadioStation(): RadioStation? {
        val streamUrl = location?.takeIf { it.isNotBlank() } ?: return null
        val matchingStation = _uiState.value.radioStations.firstOrNull { station ->
            station.streamUrl.equals(streamUrl, ignoreCase = true)
        }
        return matchingStation ?: RadioStation(
            name = name?.takeIf { it.isNotBlank() } ?: source?.takeIf { it.isNotBlank() } ?: "Recent",
            description = streamUrl,
            streamUrl = streamUrl
        )
    }

    private fun radioStationFromNowPlaying(
        nowPlaying: NowPlayingState?,
        stations: List<RadioStation>
    ): RadioStation? {
        if (nowPlaying == null || !nowPlaying.isActivePlayback()) {
            return null
        }

        val titleCandidates = listOfNotNull(
            nowPlaying.stationName?.takeIf { it.isUsefulNowPlayingTitle() },
            nowPlaying.track?.takeIf { it.isUsefulNowPlayingTitle() }
        )

        val matchedStation = stations.firstOrNull { station ->
            titleCandidates.any { candidate -> station.matchesNowPlayingText(candidate) }
        }
        if (matchedStation != null) {
            return matchedStation
        }

        val title = titleCandidates.firstOrNull() ?: return null
        val streamUrl = nowPlaying.location?.takeIf { it.isNotBlank() }.orEmpty()
        val description = listOfNotNull(
            nowPlaying.artist?.takeIf { it.isNotBlank() },
            nowPlaying.album?.takeIf { it.isNotBlank() },
            nowPlaying.streamType?.takeIf { it.isUsefulNowPlayingTitle() }?.replace('_', ' '),
            nowPlaying.source?.takeIf { it.isUsefulNowPlayingTitle() }
        )
            .distinct()
            .joinToString(" | ")
            .ifBlank { "Aktiv auf dem Lautsprecher" }

        return RadioStation(
            name = title,
            description = description,
            streamUrl = streamUrl
        )
    }

    private fun NowPlayingState.isActivePlayback(): Boolean {
        val normalizedSource = source.orEmpty().trim().uppercase()
        val normalizedStatus = playStatus.orEmpty().trim().uppercase()
        if (normalizedSource == "STANDBY" || normalizedStatus.contains("STOP")) {
            return false
        }
        val hasStreamLocation = !location.isNullOrBlank()
        return !stationName.isNullOrBlank() ||
            !track.isNullOrBlank() ||
            normalizedStatus.contains("PLAY") ||
            normalizedStatus.contains("PAUSE") ||
            normalizedStatus.contains("BUFFER") ||
            (normalizedSource == "UPNP" && hasStreamLocation) ||
            streamType?.contains("RADIO", ignoreCase = true) == true
    }

    private fun SoundForkUiState.isRemotePlaybackActive(): Boolean {
        val playStatus = nowPlaying?.playStatus.orEmpty().trim().uppercase()
        if (playStatus.contains("STOP")) {
            return false
        }
        if (playStatus.contains("PLAY") || playStatus.contains("PAUSE") || playStatus.contains("BUFFER")) {
            return true
        }
        val source = nowPlaying?.source.orEmpty().trim().uppercase()
        if (source == "UPNP" && !nowPlaying?.location.isNullOrBlank()) {
            return true
        }
        val status = dlnaStreamStatus.orEmpty().trim().uppercase()
        if (status.contains("STOP")) {
            return false
        }
        return status.contains("GESTARTET") || currentRadioStation != null
    }

    private fun RadioStation.matchesNowPlayingText(value: String): Boolean {
        val stationName = name.normalizedMatchText()
        val nowPlayingText = value.normalizedMatchText()
        if (stationName.isBlank() || nowPlayingText.isBlank()) {
            return false
        }
        return stationName == nowPlayingText ||
            stationName.contains(nowPlayingText) ||
            nowPlayingText.contains(stationName)
    }

    private fun Set<GabboUpdateType>.toDisplayText(): String {
        return joinToString(", ") { type ->
            when (type) {
                GabboUpdateType.NOW_PLAYING -> "Wiedergabe"
                GabboUpdateType.VOLUME -> "Lautstärke"
                GabboUpdateType.BASS -> "Bass"
                GabboUpdateType.PRESETS -> "Presets"
                GabboUpdateType.SOURCES -> "Quellen"
                GabboUpdateType.ZONE -> "Zonen"
                GabboUpdateType.INFO -> "Gerät"
                GabboUpdateType.CONNECTION -> "Verbindung"
                GabboUpdateType.USER_ACTIVITY -> "Bedienung"
                GabboUpdateType.ERROR -> "Fehler"
                GabboUpdateType.UNKNOWN -> "unbekannt"
            }
        }
    }

    private fun String.normalizedMatchText(): String {
        return trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    private fun String.isUsefulNowPlayingTitle(): Boolean {
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

    override fun onCleared() {
        stopNowPlayingRefresh()
        stopGabboUpdates(clearStatus = false)
        super.onCleared()
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val PREFERENCES_NAME = "soundfork_preferences"
        const val KEY_LAST_HOST = "last_host"
        const val KEY_LAST_PORT = "last_port"
        const val KEY_FAVORITE_RADIO_STREAM_URLS = "favorite_radio_stream_urls"
        const val RADIO_CHEMNITZ_STREAM_URL = "http://web.radio.radiochemnitz.de/radiochemnitz-live/stream/mp3"
        const val MAX_DEVICE_NAME_LENGTH = 64
        const val NOW_PLAYING_REFRESH_INTERVAL_MS = 6_000L
        const val GABBO_REFRESH_DEBOUNCE_MS = 250L
        val DLNA_TRANSPORT_KEYS = setOf("PLAY", "PAUSE", "STOP")
        val FALLBACK_RADIO_STATION = RadioStation("Radio Chemnitz", "Live MP3 Stream", RADIO_CHEMNITZ_STREAM_URL)
    }
}
