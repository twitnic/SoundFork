package codes.offline.soundfork.usecase

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import codes.offline.soundfork.data.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresetCancellationTest {
    @Test fun switchingDevicesReleasesThePresetButtonOnTheOriginalSpeaker() = runTest {
        val requests = mutableListOf<Pair<String, String?>>()
        val api = SoundTouchKeyApi(SoundTouchRequestExecutor { host, _, _, _, body ->
            requests += host to body
            NetworkResult(200, "<status/>", null)
        })
        val sessions = DeviceSessionController(backgroundScope)
        sessions.open("A", 8090)
        val save = sessions.launch { api.saveCurrentAsPreset("A", 8090, 1, 2500) }!!
        runCurrent()
        assertTrue(requests.single().second!!.contains("press"))
        sessions.open("B", 8090)
        runCurrent()
        assertTrue(save.isCancelled)
        assertEquals(2, requests.size)
        assertEquals("A", requests.last().first)
        assertTrue(requests.last().second!!.contains("release"))
    }
}
