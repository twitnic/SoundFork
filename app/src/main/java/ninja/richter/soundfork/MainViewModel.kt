package ninja.richter.soundfork

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ninja.richter.soundfork.data.BoseRecentItem
import ninja.richter.soundfork.data.DiscoveredSpeaker
import ninja.richter.soundfork.data.GabboUpdate
import ninja.richter.soundfork.data.GabboUpdateType
import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.data.PresetItem
import ninja.richter.soundfork.data.SavedSpeaker
import ninja.richter.soundfork.data.SharedPreferencesSettingsRepository
import ninja.richter.soundfork.data.SettingsRepository
import ninja.richter.soundfork.data.RadioBrowserRepository
import ninja.richter.soundfork.data.SourceItem
import ninja.richter.soundfork.data.SoundTouchDiscovery
import ninja.richter.soundfork.data.SoundTouchGabboClient
import ninja.richter.soundfork.data.SoundTouchXml
import ninja.richter.soundfork.data.SoundTouchRepository
import ninja.richter.soundfork.data.ZoneMember
import ninja.richter.soundfork.data.ZoneState
import ninja.richter.soundfork.model.RadioStation
import ninja.richter.soundfork.model.RadioBrowserQuery
import ninja.richter.soundfork.model.RadioBrowserSearchState
import ninja.richter.soundfork.model.confirmsRadioStream
import ninja.richter.soundfork.model.localRadioLibrary
import ninja.richter.soundfork.model.SoundForkDevicePage
import ninja.richter.soundfork.model.SoundForkUiState
import ninja.richter.soundfork.media.SoundForkMediaService
import ninja.richter.soundfork.usecase.DeviceConnectionController
import ninja.richter.soundfork.usecase.PlaybackRefreshController
import ninja.richter.soundfork.model.PlaybackStateResolver
import ninja.richter.soundfork.model.connectingTo
import ninja.richter.soundfork.model.connectedTo
import ninja.richter.soundfork.model.disconnected
import ninja.richter.soundfork.model.hasActivePlayback
import ninja.richter.soundfork.model.isPlaybackRunning
import ninja.richter.soundfork.model.speakerErrorMessage
import ninja.richter.soundfork.usecase.DeviceSessionController
import ninja.richter.soundfork.core.runSuspendCatching
import ninja.richter.soundfork.usecase.DeviceControlUseCase
import ninja.richter.soundfork.usecase.GabboController
import ninja.richter.soundfork.usecase.GabboStatusTexts
import ninja.richter.soundfork.usecase.PlaybackUseCase
import ninja.richter.soundfork.usecase.RadioBrowserSearchController
import ninja.richter.soundfork.usecase.DeviceSession
import ninja.richter.soundfork.usecase.PresetUseCase
import ninja.richter.soundfork.usecase.ZoneUseCase
import kotlin.math.roundToInt

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val sessions = DeviceSessionController(viewModelScope)
    private val repository = SoundTouchRepository()
    private val deviceControlUseCase = DeviceControlUseCase(repository)
    private val playbackUseCase = PlaybackUseCase(repository)
    private val presetUseCase = PresetUseCase(repository)
    private val zoneUseCase = ZoneUseCase(repository)
    private val discovery = SoundTouchDiscovery(application.applicationContext, repository)
    private val gabboController = GabboController(SoundTouchGabboClient())
    private val settings: SettingsRepository = SharedPreferencesSettingsRepository(application)
    private val radioBrowserRepository = RadioBrowserRepository()
    private val radioSearch = RadioBrowserSearchController(viewModelScope, radioBrowserRepository::search)
    private val connection = DeviceConnectionController(sessions, repository::loadSnapshot)
    private val playbackRefresh = PlaybackRefreshController(sessions, playbackUseCase::readNowPlaying)
    private val playbackResolver = PlaybackStateResolver()
    private val initialFavoriteRadioStreamUrls = settings.favoriteStreamUrls()
    private val initialSavedRadioStations = settings.savedRadioStations()
    private val initialRecentRadioStations = settings.recentStations(initialSavedRadioStations)
    private val initialRadioStations = localRadioLibrary(initialSavedRadioStations, initialRecentRadioStations, initialFavoriteRadioStreamUrls)
    private var gabboSnapshotRefreshJob: Job? = null
    private data class PendingRadioConfirmation(
        val session: DeviceSession, val speaker: String, val station: RadioStation,
        val acceptedUrl: String, val startedAt: Long = android.os.SystemClock.elapsedRealtime()
    )
    private var pendingRadioConfirmation: PendingRadioConfirmation? = null
    private var activeRadioRequest: Pair<String, RadioStation>? = null

    private val _uiState = MutableStateFlow(
        SoundForkUiState(
            radioStations = initialRadioStations,
            favoriteRadioStreamUrls = initialFavoriteRadioStreamUrls,
            recentRadioStations = initialRecentRadioStations
        )
    )
    val uiState: StateFlow<SoundForkUiState> = _uiState.asStateFlow()

    private fun str(@StringRes resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    init {
        val lastSpeaker = settings.lastSpeaker()
        val lastHost = lastSpeaker?.host
        val lastPort = lastSpeaker?.port ?: SoundTouchRepository.DEFAULT_PORT
        _uiState.update {
            it.copy(
                lastSavedHost = lastHost,
                lastSavedPort = lastPort
            )
        }
        connectToLastSpeakerIfAvailable()
    }

    fun onManualHostInputChanged(value: String) {
        _uiState.update { it.copy(manualHostInput = value.trim(), discoveryError = null, snapshotError = null) }
    }

    fun onRadioSearchQueryChanged(value: String) {
        _uiState.update { it.copy(radioSearchQuery = value) }
    }

    fun setRadioBrowserOpen(open: Boolean) {
        _uiState.update { it.copy(isRadioBrowserOpen = open) }
    }

    fun onRadioBrowserQueryChanged(query: RadioBrowserQuery) {
        radioSearch.cancel()
        _uiState.update { it.copy(radioBrowser = RadioBrowserSearchState(query = query)) }
    }

    fun searchRadioBrowser(loadMore: Boolean = false) {
        val current = _uiState.value.radioBrowser
        if (current.query.isEmpty() || current.isSearching || (loadMore && !current.hasMore)) return
        val offset = if (loadMore) current.nextOffset else 0
        _uiState.update { it.copy(radioBrowser = current.copy(isSearching = true, error = null,
            stations = if (loadMore) current.stations else emptyList(),
            hasSearched = loadMore && current.hasSearched, hasMore = loadMore && current.hasMore,
            nextOffset = if (loadMore) current.nextOffset else 0)) }
        radioSearch.search(current.query.normalized(), offset) { result ->
            _uiState.update { state ->
                state.copy(radioBrowser = result.fold(
                    onSuccess = { page -> state.radioBrowser.copy(
                        stations = ((if (loadMore) current.stations else emptyList()) + page.stations).distinctBy { it.streamUrl },
                        isSearching = false, hasSearched = true, error = null,
                        nextOffset = page.nextOffset, hasMore = page.hasMore) },
                    onFailure = { state.radioBrowser.copy(isSearching = false,
                        error = str(R.string.radio_browser_error)) }
                ))
            }
        }
    }

    fun toggleRadioFavorite(station: RadioStation) {
        val key = station.favoriteKey()
        if (key.isBlank()) {
            return
        }
        val favorites = _uiState.value.favoriteRadioStreamUrls
        val updatedFavorites = if (key in favorites) {
            favorites - key
        } else {
            favorites + key
        }
        settings.saveFavoriteStreamUrls(updatedFavorites)
        val savedStations = if (key in updatedFavorites) {
            (settings.savedRadioStations().filterNot { it.streamUrl == key } + station)
        } else settings.savedRadioStations().filterNot { it.streamUrl == key }
        settings.saveRadioStations(savedStations)
        val libraryStations = localRadioLibrary(savedStations, _uiState.value.recentRadioStations, updatedFavorites)
        _uiState.update { it.copy(favoriteRadioStreamUrls = updatedFavorites,
            radioStations = libraryStations) }
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

            val result = runSuspendCatching { discovery.discover() }
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
                        discoveryError = throwable.speakerErrorMessage(getApplication(), R.string.network_search_failed)
                    )
                }
            }
        }
    }

    fun connectUsingManualHost() {
        val host = _uiState.value.manualHostInput.trim()
        if (host.isBlank()) {
            Log.w(TAG, "connectUsingManualHost() ignored: empty input")
            _uiState.update { it.copy(discoveryError = str(R.string.host_required)) }
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
        stopNowPlayingRefresh()
        stopGabboUpdates(clearStatus = false)
        SoundForkMediaService.stop(getApplication())
        connection.connect(
            host, port,
            onConnecting = { session ->
                pendingRadioConfirmation = null
                activeRadioRequest = null
                _uiState.update { it.connectingTo(session.host, session.port, fallbackToDiscoveryOnFailure, str(R.string.live_updates_connecting)) }
            },
            onConnected = { session, snapshot ->
                saveLastSpeaker(session.host, session.port)
                val station = radioStationFromNowPlaying(snapshot.nowPlaying, _uiState.value.radioStations)
                _uiState.update { it.connectedTo(session.host, session.port, snapshot, station) }
                _uiState.update { it.copy(confirmedRadioStreamUrls = settings.confirmedStreams(speakerKey())) }
                updateMediaNotification()
                startGabboUpdates(session.host, session.port)
                startNowPlayingRefresh(session.host, session.port)
                station?.let(::recordRecentRadioStation)
            },
            onFailure = { throwable ->
                Log.w(TAG, "connectToHost() failed host=$host port=$port", throwable)
                stopGabboUpdates(clearStatus = false)
                _uiState.update { it.disconnected(if (fallbackToDiscoveryOnFailure) null else throwable.speakerErrorMessage(getApplication(), R.string.device_read_failed)) }
            }
        )
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
        if (state.isLoadingSnapshot || state.isRenamingDevice) {
            return
        }
        val name = state.deviceNameDraft.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(snapshotError = str(R.string.device_name_empty)) }
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "applyDeviceName() host=$host port=$port name=$name")
        sessions.launch {
            _uiState.update { it.copy(isRenamingDevice = true, snapshotError = null) }
            runSuspendCatching {
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
                        snapshotError = throwable.speakerErrorMessage(getApplication(), R.string.device_name_failed)
                    )
                }
            }
        }
    }

    fun applyVolume() {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isLoadingSnapshot || state.isApplyingVolume) {
            return
        }
        val targetVolume = state.volumeDraft.roundToInt().coerceIn(0, 100)
        val mute = state.selectedVolume?.muteEnabled
        val port = state.selectedPort

        Log.i(TAG, "applyVolume() host=$host port=$port targetVolume=$targetVolume mute=$mute")
        sessions.launch {
            _uiState.update { it.copy(isApplyingVolume = true, snapshotError = null) }
            runSuspendCatching {
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
                        snapshotError = throwable.speakerErrorMessage(getApplication(), R.string.volume_failed)
                    )
                }
            }
        }
    }

    fun toggleMute() {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isLoadingSnapshot || state.isApplyingVolume) {
            return
        }
        val port = state.selectedPort
        val targetMute = !(state.selectedVolume?.muteEnabled ?: false)
        val currentTarget = state.volumeDraft.roundToInt().coerceIn(0, 100)

        Log.i(TAG, "toggleMute() host=$host port=$port targetMute=$targetMute")
        sessions.launch {
            _uiState.update { it.copy(isApplyingVolume = true, snapshotError = null) }
            runSuspendCatching {
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
                        snapshotError = throwable.speakerErrorMessage(getApplication(), R.string.mute_failed)
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
        if (state.isLoadingSnapshot || state.isApplyingBass) {
            return
        }
        val port = state.selectedPort
        val targetBass = state.bassDraft.roundToInt().coerceIn(-9, 9)

        Log.i(TAG, "applyBass() host=$host port=$port targetBass=$targetBass")
        sessions.launch {
            _uiState.update { it.copy(isApplyingBass = true, snapshotError = null) }
            runSuspendCatching {
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
                        snapshotError = throwable.speakerErrorMessage(getApplication(), R.string.bass_failed)
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
        if (state.isLoadingSnapshot || state.isApplyingToneControls) {
            return
        }
        val port = state.selectedPort
        val treble = state.selectedToneControls?.treble
        val min = treble?.minValue ?: -10
        val max = treble?.maxValue ?: 10
        val targetTreble = state.trebleDraft.roundToInt().coerceIn(min, max)

        Log.i(TAG, "applyTreble() host=$host port=$port targetTreble=$targetTreble")
        sessions.launch {
            _uiState.update { it.copy(isApplyingToneControls = true, snapshotError = null) }
            runSuspendCatching {
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
                        snapshotError = throwable.speakerErrorMessage(getApplication(), R.string.treble_failed)
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
            _uiState.update { it.copy(debugEndpointStatus = str(R.string.api_path_required)) }
            return
        }
        if (state.isLoadingSnapshot || state.isTestingDebugEndpoint) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "testDebugEndpoint() host=$host port=$port path=$path")
        sessions.launch {
            _uiState.update {
                it.copy(
                    isTestingDebugEndpoint = true,
                    debugEndpointStatus = null
                )
            }
            runSuspendCatching {
                repository.readEndpoint(host = host, port = port, path = path)
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isTestingDebugEndpoint = false,
                        debugEndpointResult = result,
                        debugEndpointStatus = str(R.string.api_test_done, result.httpCode ?: "n/a")
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "testDebugEndpoint() failed host=$host path=$path error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isTestingDebugEndpoint = false,
                        debugEndpointStatus = throwable.speakerErrorMessage(getApplication(), R.string.api_test_failed)
                    )
                }
            }
        }
    }

    fun playDlnaStream() {
        val state = _uiState.value
        val streamUrl = state.dlnaStreamUrlInput.trim()
        if (streamUrl.isBlank()) {
            _uiState.update { it.copy(dlnaStreamStatus = str(R.string.stream_url_required)) }
            return
        }
        playDlnaStreamUrl(streamUrl)
    }

    fun playRadioStation(station: RadioStation) {
        if (_uiState.value.isStartingDlnaStream || _uiState.value.isLoadingSnapshot || _uiState.value.selectedHost == null) return
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
        if (state.isLoadingSnapshot || state.isStartingDlnaStream) {
            return
        }

        val session = sessions.current ?: return
        pendingRadioConfirmation = null
        activeRadioRequest = null
        Log.i(TAG, "playDlnaStream() host=$host url=$streamUrl")
        sessions.launch {
            _uiState.update {
                it.copy(
                    isStartingDlnaStream = true,
                    dlnaStreamStatus = null
                )
            }
            runSuspendCatching {
                playbackUseCase.playDlnaStream(
                    host = host,
                    streamUrl = streamUrl
                )
            }.onSuccess { acceptedUrl ->
                stationToRecord?.let { station ->
                    activeRadioRequest = acceptedUrl to station
                    if (station.radioBrowserId != null) {
                        pendingRadioConfirmation = PendingRadioConfirmation(session, speakerKey(), station, acceptedUrl)
                        viewModelScope.launch {
                            runSuspendCatching { radioBrowserRepository.countClick(station.radioBrowserId) }
                        }
                    }
                }
                stationToRecord?.let(::recordRecentRadioStation)
                refreshNowPlayingOnce(host, state.selectedPort)
                updateMediaNotification()
                _uiState.update {
                    it.copy(
                        isStartingDlnaStream = false,
                        dlnaStreamStatus = if (stationToRecord?.radioBrowserId != null) {
                            if (pendingRadioConfirmation != null) str(R.string.radio_stream_requested) else null
                        } else str(R.string.dlna_started)
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "playDlnaStream() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isStartingDlnaStream = false,
                        dlnaStreamStatus = throwable.speakerErrorMessage(getApplication(), R.string.dlna_failed)
                    )
                }
            }
        }
    }

    fun sendTransportKey(key: String) {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isLoadingSnapshot || state.isSendingTransportKey) {
            return
        }
        val port = state.selectedPort
        val normalizedKey = key.trim().uppercase()

        Log.i(TAG, "sendTransportKey() host=$host port=$port key=$normalizedKey")
        sessions.launch {
            _uiState.update {
                it.copy(
                    isSendingTransportKey = true,
                    snapshotError = null,
                    dlnaStreamStatus = null
                )
            }
            runSuspendCatching {
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
                        dlnaStreamStatus = str(R.string.key_sent, normalizedKey)
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
                        snapshotError = throwable.speakerErrorMessage(getApplication(), R.string.key_failed)
                    )
                }
            }
        }
    }

    fun selectSource(source: SourceItem) {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isLoadingSnapshot || state.isSelectingSource) {
            return
        }
        val port = state.selectedPort
        Log.i(
            TAG,
            "selectSource() host=$host port=$port source=${source.source} account=${source.sourceAccount}"
        )
        sessions.launch {
            _uiState.update {
                it.copy(
                    isSelectingSource = true,
                    snapshotError = null,
                    sourceStatus = null
                )
            }
            runSuspendCatching {
                repository.selectSource(host = host, port = port, source = source)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSelectingSource = false,
                        currentSource = source,
                        sourceStatus = str(R.string.source_selected, source.source)
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "selectSource() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isSelectingSource = false,
                        sourceStatus = throwable.speakerErrorMessage(getApplication(), R.string.source_select_failed)
                    )
                }
            }
        }
    }

    fun playPreset(preset: PresetItem?, presetId: Int) {
        val id = preset?.id?.coerceIn(1, 6) ?: presetId.coerceIn(1, 6)
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isLoadingSnapshot || state.isSendingPresetCommand) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "playPreset() host=$host port=$port presetId=$id")
        sessions.launch {
            _uiState.update {
                it.copy(
                    isSendingPresetCommand = true,
                    snapshotError = null,
                    presetStatus = null
                )
            }
            runSuspendCatching {
                presetUseCase.playPreset(host = host, port = port, preset = preset, presetId = id)
                refreshNowPlayingOnce(host, port)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSendingPresetCommand = false,
                        currentRadioStation = preset?.toRadioStation(id) ?: it.currentRadioStation,
                        presetStatus = str(R.string.preset_started, id),
                        dlnaStreamStatus = str(R.string.preset_started, id)
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "playPreset() failed host=$host presetId=$id error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isSendingPresetCommand = false,
                        presetStatus = throwable.speakerErrorMessage(getApplication(), R.string.preset_start_failed)
                    )
                }
            }
        }
    }

    fun playBoseRecent(recent: BoseRecentItem) {
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isLoadingSnapshot || state.isSendingRecentCommand) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "playBoseRecent() host=$host port=$port source=${recent.source} location=${recent.location}")
        sessions.launch {
            _uiState.update {
                it.copy(
                    isSendingRecentCommand = true,
                    snapshotError = null,
                    recentStatus = null
                )
            }
            runSuspendCatching {
                presetUseCase.playRecent(host = host, port = port, recent = recent)
                refreshNowPlayingOnce(host, port)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSendingRecentCommand = false,
                        currentRadioStation = recent.toRadioStation() ?: it.currentRadioStation,
                        recentStatus = str(R.string.recent_started),
                        dlnaStreamStatus = str(R.string.recent_started)
                    )
                }
            }.onFailure { throwable ->
                Log.w(TAG, "playBoseRecent() failed host=$host error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isSendingRecentCommand = false,
                        recentStatus = throwable.speakerErrorMessage(getApplication(), R.string.recent_start_failed)
                    )
                }
            }
        }
    }

    fun saveCurrentAsPreset(presetId: Int) {
        val id = presetId.coerceIn(1, 6)
        val state = _uiState.value
        val host = state.selectedHost ?: return
        if (state.isLoadingSnapshot || state.isSendingPresetCommand) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "saveCurrentAsPreset() host=$host port=$port presetId=$id")
        sessions.launch {
            _uiState.update {
                it.copy(
                    isSendingPresetCommand = true,
                    snapshotError = null,
                    presetStatus = str(R.string.preset_saving, id)
                )
            }
            runSuspendCatching {
                presetUseCase.saveCurrentAsPreset(host = host, port = port, presetId = id)
                repository.loadSnapshot(host, port)
            }.onSuccess { snapshot ->
                _uiState.update {
                    it.copy(
                        isSendingPresetCommand = false,
                        presets = snapshot.presets,
                        boseRecents = snapshot.recents,
                        zoneState = snapshot.zoneState,
                        endpointResults = snapshot.endpointResults,
                        presetStatus = str(R.string.preset_saved, id)
                    )
                }
                refreshNowPlayingOnce(host, port)
            }.onFailure { throwable ->
                Log.w(TAG, "saveCurrentAsPreset() failed host=$host presetId=$id error=${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isSendingPresetCommand = false,
                        presetStatus = throwable.speakerErrorMessage(getApplication(), R.string.preset_save_failed)
                    )
                }
            }
        }
    }

    fun setZoneWithSpeaker(speaker: DiscoveredSpeaker) {
        runZoneCommand(
            actionLabel = str(R.string.zone_created),
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
            actionLabel = str(R.string.zone_slave_added),
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
            actionLabel = str(R.string.zone_slave_removed),
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
                _uiState.update { it.copy(zoneStatus = str(R.string.zone_master_device_id_missing)) }
                return
            }
        if (state.isLoadingSnapshot || state.isUpdatingZone) {
            return
        }
        val port = state.selectedPort

        Log.i(TAG, "runZoneCommand() host=$host port=$port master=$masterDeviceId action=$actionLabel")
        sessions.launch {
            _uiState.update {
                it.copy(
                    isUpdatingZone = true,
                    zoneStatus = null,
                    snapshotError = null
                )
            }
            runSuspendCatching {
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
                        zoneStatus = throwable.speakerErrorMessage(getApplication(), R.string.zone_update_failed)
                    )
                }
            }
        }
    }

    fun openDiscoveryScreen() {
        pendingRadioConfirmation = null
        activeRadioRequest = null
        Log.i(TAG, "openDiscoveryScreen()")
        sessions.close()
        stopNowPlayingRefresh()
        stopGabboUpdates(clearStatus = true)
        SoundForkMediaService.stop(getApplication())
        _uiState.update { it.disconnected().copy(devicePage = SoundForkDevicePage.RADIO) }
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

    fun openInfoPage() {
        _uiState.update { it.copy(devicePage = SoundForkDevicePage.INFO) }
    }

    fun refreshSelectedHost() {
        val host = _uiState.value.selectedHost ?: return
        val port = _uiState.value.selectedPort
        connectToHost(host, port)
    }

    private fun connectToLastSpeakerIfAvailable() {
        val speaker = settings.lastSpeaker() ?: return
        connectToHost(speaker.host, speaker.port, fallbackToDiscoveryOnFailure = true)
    }

    private fun saveLastSpeaker(host: String, port: Int) {
        settings.saveLastSpeaker(SavedSpeaker(host, port))
        _uiState.update { it.copy(lastSavedHost = host, lastSavedPort = port) }
    }

    private fun startGabboUpdates(host: String, port: Int) {
        val session = sessions.current ?: return
        stopGabboUpdates(clearStatus = false)
        gabboController.connect(
            host = host,
            port = port,
            statusTexts = GabboStatusTexts(
                connecting = str(R.string.live_updates_connecting),
                active = str(R.string.live_updates_active),
                disconnectedPolling = str(R.string.live_updates_disconnected_polling),
                pollingActive = { message -> str(R.string.polling_active_suffix, message) }
            ),
            onStatus = { status ->
                sessions.launch(session) {
                    _uiState.update { it.copy(realtimeStatus = status, realtimeError = null) }
                }
            },
            onUpdate = { update ->
                sessions.launch(session) { handleGabboUpdate(host, port, update) }
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
                    realtimeStatus = str(R.string.live_update_prefix, update.types.toDisplayText()),
                    realtimeError = if (GabboUpdateType.ERROR in update.types) {
                        update.errorMessage ?: str(R.string.speaker_reports_error)
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
            sessions.launch {
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
        sessions.launch {
            val volume = SoundTouchXml.parseVolume(update.rawXml)
                ?: runSuspendCatching {
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
        sessions.launch {
            val bass = SoundTouchXml.parseBass(update.rawXml)
                ?: runSuspendCatching {
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
        gabboSnapshotRefreshJob = sessions.launch {
            delay(GABBO_REFRESH_DEBOUNCE_MS)
            val snapshot = runSuspendCatching {
                repository.loadSnapshot(host, port)
            }.onFailure { throwable ->
                Log.d(TAG, "scheduleSnapshotRefreshFromGabbo() failed host=$host error=${throwable.message}")
            }.getOrNull() ?: return@launch

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
                        presets = snapshot.presets,
                        boseRecents = snapshot.recents,
                        sources = snapshot.sources,
                        zoneState = snapshot.zoneState,
                        zoneXml = snapshot.zoneXml,
                        endpointResults = snapshot.endpointResults
                    )
                } else {
                    it
                }
            }
            refreshNowPlayingOnce(host, port)
        }
    }

    private fun startNowPlayingRefresh(host: String, port: Int) {
        playbackRefresh.startPolling(host, port, NOW_PLAYING_REFRESH_INTERVAL_MS) { refreshNowPlayingOnce(host, port) }
    }

    private fun stopNowPlayingRefresh() = playbackRefresh.stopPolling()

    private suspend fun refreshNowPlayingOnce(host: String, port: Int) {
        playbackRefresh.refresh(host, port) { result ->
            val nowPlaying = result.getOrNull()
            if (nowPlaying == null) {
                markPlaybackUnavailable(result.exceptionOrNull()?.speakerErrorMessage(getApplication(), R.string.speaker_unreachable) ?: str(R.string.speaker_unreachable))
            } else {
                confirmPendingRadioStream(nowPlaying)
                val active = nowPlaying.hasActivePlayback()
                val station = radioStationFromNowPlaying(nowPlaying, _uiState.value.radioStations)
                _uiState.update {
                    it.copy(
                        nowPlaying = nowPlaying,
                        currentRadioStation = station,
                        currentSource = nowPlaying.source?.let { source -> SourceItem(source, nowPlaying.sourceAccount) },
                        dlnaStreamStatus = if (active) null else str(R.string.no_active_playback)
                    )
                }
                updateMediaNotification()
            }
        }
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
        if (!state.nowPlaying.hasActivePlayback()) {
            SoundForkMediaService.stop(getApplication())
            return
        }
        SoundForkMediaService.update(
            context = getApplication(),
            host = host,
            port = state.selectedPort,
            station = state.currentRadioStation,
            nowPlaying = state.nowPlaying,
            isPlaying = state.isPlaybackRunning()
        )
    }

    private fun recordRecentRadioStation(station: RadioStation) {
        val state = _uiState.value
        val updatedStations = settings.recordStation(
            station = station,
            recents = state.recentRadioStations,
            catalog = state.radioBrowser.stations + settings.savedRadioStations()
        )
        _uiState.update {
            it.copy(recentRadioStations = updatedStations,
                radioStations = localRadioLibrary(settings.savedRadioStations(), updatedStations, it.favoriteRadioStreamUrls))
        }
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
            name = name?.takeIf { it.isNotBlank() } ?: str(R.string.preset_name, presetId),
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
            name = name?.takeIf { it.isNotBlank() } ?: source?.takeIf { it.isNotBlank() } ?: str(R.string.recent),
            description = streamUrl,
            streamUrl = streamUrl
        )
    }

    private fun radioStationFromNowPlaying(nowPlaying: NowPlayingState?, stations: List<RadioStation>): RadioStation? {
        activeRadioRequest?.let { (url, station) ->
            if (nowPlaying.hasActivePlayback() && nowPlaying?.location == url) return station
        }
        val state = _uiState.value
        return playbackResolver.station(nowPlaying,
            stations + state.radioBrowser.stations + listOfNotNull(state.currentRadioStation) + state.recentRadioStations.map { it.asRadioStation() },
            str(R.string.active_on_speaker))
    }

    private fun speakerKey(): String = _uiState.value.let {
        it.selectedSummary?.deviceId?.takeIf(String::isNotBlank) ?: "${it.selectedHost}:${it.selectedPort}"
    }

    private fun confirmPendingRadioStream(nowPlaying: NowPlayingState) {
        val pending = pendingRadioConfirmation ?: return
        if (!sessions.isCurrent(pending.session) || android.os.SystemClock.elapsedRealtime() - pending.startedAt > 30_000) {
            pendingRadioConfirmation = null
            return
        }
        if (!nowPlaying.confirmsRadioStream(pending.acceptedUrl)) return
        val confirmed = settings.confirmStream(pending.speaker, pending.station.streamUrl)
        pendingRadioConfirmation = null
        _uiState.update { it.copy(confirmedRadioStreamUrls = confirmed) }
    }

    private fun Set<GabboUpdateType>.toDisplayText(): String {
        return joinToString(", ") { type ->
            when (type) {
                GabboUpdateType.NOW_PLAYING -> str(R.string.gabbo_now_playing)
                GabboUpdateType.VOLUME -> str(R.string.volume)
                GabboUpdateType.BASS -> str(R.string.bass)
                GabboUpdateType.PRESETS -> str(R.string.nav_presets)
                GabboUpdateType.SOURCES -> str(R.string.gabbo_sources)
                GabboUpdateType.ZONE -> str(R.string.nav_zones)
                GabboUpdateType.INFO -> str(R.string.gabbo_info)
                GabboUpdateType.CONNECTION -> str(R.string.gabbo_connection)
                GabboUpdateType.USER_ACTIVITY -> str(R.string.gabbo_user_activity)
                GabboUpdateType.ERROR -> str(R.string.error)
                GabboUpdateType.UNKNOWN -> str(R.string.unknown_lower)
            }
        }
    }

    override fun onCleared() {
        sessions.close()
        stopNowPlayingRefresh()
        stopGabboUpdates(clearStatus = false)
        super.onCleared()
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val MAX_DEVICE_NAME_LENGTH = 64
        const val NOW_PLAYING_REFRESH_INTERVAL_MS = 6_000L
        const val GABBO_REFRESH_DEBOUNCE_MS = 250L
        val DLNA_TRANSPORT_KEYS = setOf("PLAY", "PAUSE", "STOP")
    }
}
