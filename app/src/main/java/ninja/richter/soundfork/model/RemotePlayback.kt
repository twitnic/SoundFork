package ninja.richter.soundfork.model

import java.util.Locale
import ninja.richter.soundfork.data.NowPlayingState

enum class PlaybackStatus { IDLE, PLAYING, BUFFERING, PAUSED, STOPPED, STANDBY }

fun NowPlayingState?.playbackStatus(): PlaybackStatus {
    if (this == null) return PlaybackStatus.IDLE
    val state = playStatus.orEmpty().uppercase(Locale.ROOT)
    return when {
        source.equals("STANDBY", true) -> PlaybackStatus.STANDBY
        state.contains("STOP") -> PlaybackStatus.STOPPED
        state.contains("PAUSE") -> PlaybackStatus.PAUSED
        state.contains("BUFFER") -> PlaybackStatus.BUFFERING
        state.contains("PLAY") -> PlaybackStatus.PLAYING
        source.equals("UPNP", true) && !location.isNullOrBlank() -> PlaybackStatus.PLAYING
        !stationName.isNullOrBlank() || !track.isNullOrBlank() -> PlaybackStatus.PLAYING
        else -> PlaybackStatus.IDLE
    }
}

fun NowPlayingState?.hasActivePlayback(): Boolean = playbackStatus() in setOf(
    PlaybackStatus.PLAYING, PlaybackStatus.PAUSED, PlaybackStatus.BUFFERING
)

fun SoundForkUiState.isPlaybackRunning(): Boolean = when (nowPlaying.playbackStatus()) {
    PlaybackStatus.PLAYING, PlaybackStatus.BUFFERING -> true
    PlaybackStatus.PAUSED, PlaybackStatus.STOPPED, PlaybackStatus.STANDBY -> false
    PlaybackStatus.IDLE -> isStartingDlnaStream || currentRadioStation != null
}

class PlaybackStateResolver {
    fun station(nowPlaying: NowPlayingState?, catalog: List<RadioStation>, fallbackDescription: String): RadioStation? {
        if (!nowPlaying.hasActivePlayback()) return null
        nowPlaying ?: return null
        val url = nowPlaying.location?.trim().orEmpty()
        catalog.firstOrNull { url.isNotBlank() && it.streamUrl.equals(url, true) }?.let { return it }
        val titles = listOfNotNull(nowPlaying.stationName, nowPlaying.track).filter { it.isUsefulPlaybackTitle() }
        catalog.firstOrNull { station -> titles.any { it.trim().equals(station.name.trim(), true) } }?.let { return it }
        val title = titles.firstOrNull() ?: return null
        val details = listOfNotNull(nowPlaying.artist, nowPlaying.album, nowPlaying.source)
            .filter { it.isUsefulPlaybackTitle() }.distinct().joinToString(" | ")
        return RadioStation(title, details.ifBlank { fallbackDescription }, url)
    }
}

private fun String.isUsefulPlaybackTitle(): Boolean = trim().replace('_', ' ').uppercase(Locale.ROOT)
    .let { it.isNotBlank() && it !in setOf("UPNP", "TRACK ONDEMAND", "TRACK ONDEMAND TRACK ONDEMAND", "ONDEMAND", "UNKNOWN") }
