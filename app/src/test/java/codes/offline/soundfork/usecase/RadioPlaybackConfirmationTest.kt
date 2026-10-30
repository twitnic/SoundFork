package codes.offline.soundfork.usecase

import codes.offline.soundfork.data.NowPlayingState
import codes.offline.soundfork.model.confirmsRadioStream
import org.junit.Assert.*
import org.junit.Test

class RadioPlaybackConfirmationTest {
    private val url = "https://radio.test/live"
    private val playing = NowPlayingState(source = "UPNP", playStatus = "PLAY_STATE", location = url)

    @Test fun onlyAnExplicitPlayingStateAtTheAcceptedUrlConfirmsPlayback() {
        assertTrue(playing.confirmsRadioStream(url))
        assertTrue(playing.copy(playStatus = "PLAYING").confirmsRadioStream(url))
        listOf(null, "", "BUFFERING_STATE", "STOP_STATE", "PAUSE_STATE", "ERROR", "NOT_PLAYING")
            .forEach { status -> assertFalse("Unexpected confirmation for $status", playing.copy(playStatus = status).confirmsRadioStream(url)) }
        assertFalse(playing.copy(location = "https://radio.test/other").confirmsRadioStream(url))
        assertFalse(playing.copy(source = "BLUETOOTH").confirmsRadioStream(url))
        assertFalse(playing.copy(location = null, stationName = "Matching name").confirmsRadioStream(url))
    }

    @Test fun httpsAndHttpUrlsMustNotBeConfused() {
        val fallbackUrl = "http://radio.test/live"
        assertFalse(playing.copy(location = fallbackUrl).confirmsRadioStream(url))
        assertTrue(playing.copy(location = fallbackUrl).confirmsRadioStream(fallbackUrl))
    }
}
