package ninja.richter.soundfork.data

import android.util.Log
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class GabboUpdateType {
    NOW_PLAYING,
    VOLUME,
    BASS,
    PRESETS,
    SOURCES,
    ZONE,
    INFO,
    CONNECTION,
    USER_ACTIVITY,
    ERROR,
    UNKNOWN
}

data class GabboUpdate(
    val rawXml: String,
    val types: Set<GabboUpdateType>,
    val errorMessage: String? = null
)

object SoundTouchGabboParser {
    fun parse(rawXml: String): GabboUpdate {
        val normalized = rawXml.lowercase(Locale.US)
        val types = buildSet {
            if (
                "<nowplaying" in normalized ||
                "<playstatus" in normalized ||
                "<contentitem" in normalized ||
                "<track>" in normalized ||
                "<stationname>" in normalized
            ) {
                add(GabboUpdateType.NOW_PLAYING)
            }
            if ("<volume" in normalized || "<targetvolume" in normalized || "<actualvolume" in normalized) {
                add(GabboUpdateType.VOLUME)
            }
            if ("<bass" in normalized) {
                add(GabboUpdateType.BASS)
            }
            if ("<presetsupdated" in normalized || "<presets" in normalized || "<preset " in normalized) {
                add(GabboUpdateType.PRESETS)
            }
            if (
                "<sourceupdated" in normalized ||
                "<sourcesupdated" in normalized ||
                "<sourcelistupdated" in normalized ||
                "<sources>" in normalized
            ) {
                add(GabboUpdateType.SOURCES)
            }
            if ("<zone" in normalized || "<zoneupdated" in normalized || "<groupupdated" in normalized) {
                add(GabboUpdateType.ZONE)
            }
            if ("<info" in normalized) {
                add(GabboUpdateType.INFO)
            }
            if ("<connectionstateupdated" in normalized) {
                add(GabboUpdateType.CONNECTION)
            }
            if ("<useractivityupdate" in normalized) {
                add(GabboUpdateType.USER_ACTIVITY)
            }
            if ("<errorupdate" in normalized || "<error " in normalized || "<error>" in normalized) {
                add(GabboUpdateType.ERROR)
            }
        }
        return GabboUpdate(
            rawXml = rawXml,
            types = types.ifEmpty { setOf(GabboUpdateType.UNKNOWN) },
            errorMessage = parseErrorMessage(rawXml)
        )
    }

    private fun parseErrorMessage(rawXml: String): String? {
        val match = ERROR_TAG_REGEX.find(rawXml) ?: return null
        val attributes = match.groupValues.getOrNull(1).orEmpty()
        val message = match.groupValues.getOrNull(2)
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.takeIf { it.isNotBlank() }
        val name = XML_ATTRIBUTE_REGEX("name").find(attributes)
            ?.let { it.groupValues.getOrNull(1)?.ifBlank { it.groupValues.getOrNull(2).orEmpty() } }
            ?.takeIf { it.isNotBlank() }
        val value = XML_ATTRIBUTE_REGEX("value").find(attributes)
            ?.let { it.groupValues.getOrNull(1)?.ifBlank { it.groupValues.getOrNull(2).orEmpty() } }
            ?.takeIf { it.isNotBlank() }
        return listOfNotNull(name, value?.let { "Code $it" }, message)
            .joinToString(": ")
            .takeIf { it.isNotBlank() }
    }

    private val ERROR_TAG_REGEX = Regex("(?is)<error\\b\\s*([^>]*)>(.*?)</error>")

    private fun XML_ATTRIBUTE_REGEX(attributeName: String): Regex {
        return Regex("(?i)\\b$attributeName\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    }
}

class SoundTouchGabboClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2_000, TimeUnit.MILLISECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(2_000, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
) {
    interface Listener {
        fun onOpen()
        fun onUpdate(update: GabboUpdate)
        fun onClosed(reason: String)
        fun onFailure(message: String, throwable: Throwable?)
    }

    fun connect(
        host: String,
        listener: Listener
    ): WebSocket {
        val request = Request.Builder()
            .url("ws://$host:$GABBO_PORT/")
            .header("Sec-WebSocket-Protocol", GABBO_PROTOCOL)
            .build()

        Log.i(TAG, "connect(): host=$host port=$GABBO_PORT protocol=$GABBO_PROTOCOL")
        return client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "onOpen(): host=$host code=${response.code}")
                    listener.onOpen()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.v(TAG, "onMessage(): host=$host xml=${text.take(MAX_LOG_XML_LENGTH)}")
                    listener.onUpdate(SoundTouchGabboParser.parse(text))
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "onClosing(): host=$host code=$code reason=$reason")
                    webSocket.close(code, reason)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "onClosed(): host=$host code=$code reason=$reason")
                    listener.onClosed(reason.ifBlank { "Gabbo geschlossen" })
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val message = "Gabbo nicht verbunden: ${t.message ?: response?.message ?: "unbekannter Fehler"}"
                    Log.w(TAG, "onFailure(): host=$host code=${response?.code} error=${t.message}", t)
                    listener.onFailure(message, t)
                }
            }
        )
    }

    companion object {
        const val GABBO_PORT = 8080
        private const val GABBO_PROTOCOL = "gabbo"
        private const val MAX_LOG_XML_LENGTH = 500
        private const val TAG = "SoundTouchGabbo"
    }
}
