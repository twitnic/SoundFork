package codes.offline.soundfork.usecase

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import codes.offline.soundfork.data.*
import codes.offline.soundfork.model.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionAndPlaybackTest {
    @Test fun lateConnectionCannotReplaceNewlyConnectedSpeakerOrSaveIt() = runTest {
        val sessions = DeviceSessionController(backgroundScope)
        val responseA = CompletableDeferred<SoundTouchSnapshot>()
        val controller = DeviceConnectionController(sessions) { host, _ ->
            if (host == "A") withContext(NonCancellable) { responseA.await() } else snapshot(host)
        }
        var connected: String? = null
        var failures = 0
        controller.connect("A", 8090, {}, { session, _ -> connected = session.host }, { failures++ })
        runCurrent()
        controller.connect("B", 8090, {}, { session, _ -> connected = session.host }, { failures++ })
        runCurrent()
        responseA.complete(snapshot("A"))
        runCurrent()
        assertEquals("B", connected)
        assertEquals(0, failures)
    }

    @Test fun failedConnectionDoesNotLeaveAnActiveCommandSession() = runTest {
        val sessions = DeviceSessionController(backgroundScope)
        val failure = java.io.IOException("No speaker")
        val controller = DeviceConnectionController(sessions) { _, _ -> throw failure }
        var reported: Throwable? = null
        controller.connect("A", 8090, {}, { _, _ -> fail("Failed connection reported success") }, { reported = it })
        runCurrent()
        assertSame(failure, reported)
        assertNull(sessions.current)
    }

    @Test fun pollingAndWebsocketBurstPublishOnlyTheNewestRead() = runTest {
        val sessions = DeviceSessionController(backgroundScope)
        sessions.open("A", 8090)
        val response = CompletableDeferred<NowPlayingState>()
        var reads = 0
        val controller = PlaybackRefreshController(sessions) { _, _ ->
            if (++reads == 1) response.await() else NowPlayingState(track = "Newest")
        }
        val published = mutableListOf<String?>()
        sessions.launch { controller.refresh("A", 8090) { published += it.getOrNull()?.track } }
        runCurrent()
        repeat(8) { sessions.launch { controller.refresh("A", 8090) { published += it.getOrNull()?.track } } }
        runCurrent()
        response.complete(NowPlayingState(track = "Outdated"))
        runCurrent()
        assertEquals(listOf("Newest"), published)
        assertEquals(2, reads)
    }

    @Test fun reconnectDoesNotWaitForNonCooperativeReadFromPreviousSession() = runTest {
        val sessions = DeviceSessionController(backgroundScope)
        val response = CompletableDeferred<NowPlayingState>()
        val controller = PlaybackRefreshController(sessions) { host, _ ->
            if (host == "A") withContext(NonCancellable) { response.await() } else NowPlayingState(track = "B")
        }
        val published = mutableListOf<String?>()
        sessions.open("A", 8090)
        sessions.launch { controller.refresh("A", 8090) { published += it.getOrNull()?.track } }
        runCurrent()
        sessions.open("B", 8090)
        sessions.launch { controller.refresh("B", 8090) { published += it.getOrNull()?.track } }
        runCurrent()
        assertEquals(listOf("B"), published)
        response.complete(NowPlayingState(track = "A"))
        runCurrent()
        assertEquals(listOf("B"), published)
    }

    @Test fun changingDeviceClearsRemoteDataAndBusyFlagsButKeepsLocalLibrary() {
        val station = RadioStation("Radio", "Music", "http://example/radio")
        val state = SoundForkUiState(currentRadioStation = station, radioStations = listOf(station),
            favoriteRadioStreamUrls = setOf(station.streamUrl), isApplyingVolume = true, isSendingPresetCommand = true,
            snapshotError = "Old error", sourceStatus = "Old status")
        val connecting = state.connectingTo("B", 8090, false, "Connecting")
        assertNull(connecting.currentRadioStation)
        assertNull(connecting.snapshotError)
        assertNull(connecting.sourceStatus)
        assertFalse(connecting.isApplyingVolume)
        assertFalse(connecting.isSendingPresetCommand)
        assertEquals(listOf(station), connecting.radioStations)
        assertEquals(setOf(station.streamUrl), connecting.favoriteRadioStreamUrls)
        assertTrue(connecting.isLoadingSnapshot)
    }

    private fun snapshot(host: String) = SoundTouchSnapshot(DeviceSummary(host), emptyList())
}
