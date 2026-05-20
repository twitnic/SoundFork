package ninja.richter.soundfork.usecase

import ninja.richter.soundfork.data.NowPlayingState
import ninja.richter.soundfork.data.SoundTouchRepository

class PlaybackUseCase(
    private val repository: SoundTouchRepository
) {
    suspend fun playDlnaStream(host: String, streamUrl: String) {
        repository.playDlnaStream(host = host, streamUrl = streamUrl)
    }

    suspend fun sendTransportKey(host: String, port: Int, key: String) {
        repository.sendKey(host = host, port = port, key = key)
    }

    suspend fun readNowPlaying(host: String, port: Int): NowPlayingState? {
        return repository.readNowPlayingStateOrNull(host = host, port = port)
    }
}
