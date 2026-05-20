package ninja.richter.soundfork.usecase

import ninja.richter.soundfork.data.GabboUpdate
import ninja.richter.soundfork.data.SoundTouchGabboClient
import okhttp3.WebSocket

class GabboController(
    private val client: SoundTouchGabboClient
) {
    private var webSocket: WebSocket? = null
    private var connectionToken: String? = null

    fun connect(
        host: String,
        port: Int,
        onStatus: (String) -> Unit,
        onUpdate: (GabboUpdate) -> Unit
    ) {
        stop()
        val token = "$host:$port:${System.nanoTime()}"
        connectionToken = token
        onStatus("Live-Updates werden verbunden...")
        webSocket = client.connect(
            host = host,
            listener = object : SoundTouchGabboClient.Listener {
                override fun onOpen() {
                    if (connectionToken == token) {
                        onStatus("Live-Updates aktiv")
                    }
                }

                override fun onUpdate(update: GabboUpdate) {
                    if (connectionToken == token) {
                        onUpdate(update)
                    }
                }

                override fun onClosed(reason: String) {
                    if (connectionToken == token) {
                        onStatus("Live-Updates getrennt, Polling aktiv")
                    }
                }

                override fun onFailure(message: String, throwable: Throwable?) {
                    if (connectionToken == token) {
                        onStatus("$message. Polling aktiv")
                    }
                }
            }
        )
    }

    fun stop() {
        connectionToken = null
        webSocket?.close(GABBO_NORMAL_CLOSE_CODE, "SoundFork disconnect")
        webSocket = null
    }
}

private const val GABBO_NORMAL_CLOSE_CODE = 1000
