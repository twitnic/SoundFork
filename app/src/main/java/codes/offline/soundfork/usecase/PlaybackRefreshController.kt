package codes.offline.soundfork.usecase

import codes.offline.soundfork.core.runSuspendCatching

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import codes.offline.soundfork.data.NowPlayingState

/** Serialize reads and coalesce bursts; only the latest requested result is published. */
class PlaybackRefreshController(
    private val sessions: DeviceSessionController,
    private val readNowPlaying: suspend (String, Int) -> NowPlayingState?
) {
    private class Reads(val session: DeviceSession) {
        val mutex = Mutex()
        var revision = 0L
    }
    private var reads: Reads? = null
    private var polling: Job? = null

    suspend fun refresh(host: String, port: Int, onResult: (Result<NowPlayingState?>) -> Unit) {
        val session = sessions.current ?: return
        if (session.host != host || session.port != port) return
        val current = reads?.takeIf { it.session === session } ?: Reads(session).also { reads = it }
        val revision = ++current.revision
        current.mutex.withLock {
            currentCoroutineContext().ensureActive()
            if (current.revision != revision || !sessions.isCurrent(session)) return
            val result = runSuspendCatching { readNowPlaying(host, port) }
            if (current.revision == revision && sessions.isCurrent(session)) onResult(result)
        }
    }

    fun startPolling(host: String, port: Int, intervalMs: Long, refresh: suspend () -> Unit) {
        stopPolling()
        polling = sessions.launch {
            while (true) {
                delay(intervalMs)
                val session = sessions.current ?: return@launch
                if (session.host != host || session.port != port) return@launch
                refresh()
            }
        }
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
    }
}
