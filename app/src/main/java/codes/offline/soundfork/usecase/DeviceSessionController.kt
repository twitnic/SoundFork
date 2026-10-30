package codes.offline.soundfork.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DeviceSession internal constructor(
    val host: String,
    val port: Int,
    internal val scope: CoroutineScope
)

/** Each connection gets a new identity, including reconnects to the same address. */
class DeviceSessionController(private val parentScope: CoroutineScope) {
    @Volatile
    var current: DeviceSession? = null
        private set

    fun open(host: String, port: Int): DeviceSession {
        close()
        return DeviceSession(
            host,
            port,
            CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))
        ).also { current = it }
    }

    fun isCurrent(session: DeviceSession): Boolean = current === session

    fun launch(
        session: DeviceSession? = current,
        block: suspend CoroutineScope.() -> Unit
    ): Job? {
        if (session == null || !isCurrent(session)) return null
        return session.scope.launch {
            if (isCurrent(session)) block()
        }
    }

    fun close() {
        val previous = current
        current = null
        previous?.scope?.cancel()
    }
}
