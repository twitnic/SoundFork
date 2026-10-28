package ninja.richter.soundfork.ui

import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.model.*
import org.junit.Assert.*
import org.junit.Test

class PlaybackStateResolverTest {
    private val radio = RadioStation("Radio Chemnitz", "Lokal", "http://radio.example/stream")
    private val resolver = PlaybackStateResolver()

    @Test fun streamUrlRestoresCatalogNameEvenWithGenericUpnpMetadata() {
        val actual = resolver.station(NowPlayingState(source = "UPNP", location = radio.streamUrl,
            track = "TRACK_ONDEMAND", playStatus = "PLAY_STATE"), listOf(radio), "Active")
        assertEquals(radio, actual)
    }

    @Test fun standbyAndStoppedPlaybackNeverKeepARadioStation() {
        for (playing in listOf(NowPlayingState(source = "STANDBY", location = radio.streamUrl),
            NowPlayingState(source = "UPNP", location = radio.streamUrl, playStatus = "STOP_STATE"))) {
            assertNull(resolver.station(playing, listOf(radio), "Active"))
            assertFalse(playing.hasActivePlayback())
        }
    }

    @Test fun pauseKeepsTheStationButDoesNotMeanPlaying() {
        val playing = NowPlayingState(source = "UPNP", location = radio.streamUrl, playStatus = "PAUSE_STATE")
        assertTrue(playing.hasActivePlayback())
        assertEquals(PlaybackStatus.PAUSED, playing.playbackStatus())
        assertFalse(SoundForkUiState(nowPlaying = playing, currentRadioStation = radio).isPlaybackRunning())
        assertEquals(radio, resolver.station(playing, listOf(radio), "Active"))
    }

    @Test fun unrelatedTrackCannotMatchStationBySubstring() {
        assertNotEquals(radio, resolver.station(NowPlayingState(source = "BLUETOOTH", track = "Radio Chemnitz live recording"), listOf(radio), "Active"))
    }
}
