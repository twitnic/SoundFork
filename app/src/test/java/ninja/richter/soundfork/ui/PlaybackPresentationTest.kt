package ninja.richter.soundfork.ui

import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.isMiniPlayerPlaying
import ninja.richter.soundfork.model.RadioStation
import ninja.richter.soundfork.model.SoundForkUiState
import org.junit.Assert.*
import org.junit.Test

class PlaybackPresentationTest {
    private val station = RadioStation("Radio Chemnitz", "Radio", "http://radio.example/stream")

    @Test fun pauseAndStandbyOverrideStalePlayingStation() {
        for (status in listOf("PAUSE_STATE", "STOP_STATE")) {
            val state = SoundForkUiState(currentRadioStation = station,
                nowPlaying = NowPlayingState(source = "UPNP", location = station.streamUrl, playStatus = status))
            assertFalse(state.isMiniPlayerPlaying())
        }
        assertFalse(SoundForkUiState(currentRadioStation = station, nowPlaying = NowPlayingState(source = "STANDBY")).isMiniPlayerPlaying())
    }

    @Test fun playbackDoesNotDependOnTranslatedStatusMessages() {
        for (message in listOf("DLNA Stream wurde gestartet", "DLNA stream started", null)) {
            val state = SoundForkUiState(dlnaStreamStatus = message,
                nowPlaying = NowPlayingState(source = "UPNP", playStatus = "PLAY_STATE"))
            assertTrue(state.isMiniPlayerPlaying())
        }
        assertTrue(SoundForkUiState(nowPlaying = NowPlayingState(playStatus = "BUFFERING_STATE")).isMiniPlayerPlaying())
        assertFalse(SoundForkUiState().isMiniPlayerPlaying())
    }
}
