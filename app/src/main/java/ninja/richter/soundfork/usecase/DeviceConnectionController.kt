package ninja.richter.soundfork.usecase

import ninja.richter.soundfork.core.runSuspendCatching

import ninja.richter.soundfork.data.SoundTouchSnapshot

class DeviceConnectionController(
    private val sessions: DeviceSessionController,
    private val loadSnapshot: suspend (String, Int) -> SoundTouchSnapshot
) {
    fun connect(
        host: String,
        port: Int,
        onConnecting: (DeviceSession) -> Unit,
        onConnected: (DeviceSession, SoundTouchSnapshot) -> Unit,
        onFailure: (Throwable) -> Unit
    ) {
        val session = sessions.open(host, port)
        onConnecting(session)
        sessions.launch(session) {
            runSuspendCatching { loadSnapshot(host, port) }
                .onSuccess { onConnected(session, it) }
                .onFailure {
                    sessions.close()
                    onFailure(it)
                }
        }
    }
}
