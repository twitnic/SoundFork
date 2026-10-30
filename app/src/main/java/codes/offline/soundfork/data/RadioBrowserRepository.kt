package codes.offline.soundfork.data

import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import codes.offline.soundfork.core.runSuspendCatching
import codes.offline.soundfork.model.RadioBrowserQuery
import codes.offline.soundfork.model.RadioStation
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

data class RadioBrowserPage(val stations: List<RadioStation>, val nextOffset: Int, val hasMore: Boolean)

/** The directory supplies URLs; audio is fetched directly by the speaker. */
class RadioBrowserRepository(
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS).build(),
    private val discoverServers: suspend () -> List<String> = { discoverRadioBrowserServers() }
) {
    private val transport = SoundTouchHttpTransport(client)
    private val serverLock = Mutex()
    private var servers: List<String> = emptyList()
    private var serversLoadedAt = 0L

    suspend fun search(query: RadioBrowserQuery, offset: Int = 0): RadioBrowserPage {
        require(!query.isEmpty())
        return parsePage(request { base -> searchUrl(base, query.normalized(), offset) }, offset)
    }

    suspend fun countClick(stationId: String) {
        request { base ->
            base.toHttpUrl().newBuilder().addPathSegments("json/url").addPathSegment(stationId).build()
        }
    }

    private suspend fun request(url: (String) -> HttpUrl): String {
        var failure: Exception = IOException("Radio Browser unavailable")
        for (server in availableServers().take(3)) {
            currentCoroutineContext().ensureActive()
            val result = transport.execute(Request.Builder().url(url(server))
                .header("User-Agent", "SoundFork/1.0 (Android)")
                .header("Accept", "application/json").build())
            if (result.code in 200..299 && result.body != null) return result.body
            failure = result.exception ?: IOException("Radio Browser HTTP ${result.code}")
        }
        throw failure
    }

    private suspend fun availableServers(): List<String> = serverLock.withLock {
        if (servers.isEmpty() || System.currentTimeMillis() - serversLoadedAt > TimeUnit.HOURS.toMillis(1)) {
            servers = runSuspendCatching { discoverServers() }.getOrDefault(emptyList())
                .filter { it.endsWith(".api.radio-browser.info") }.distinct().shuffled()
                .map { "https://$it/" }
                .ifEmpty { listOf("https://de1.api.radio-browser.info/", "https://nl1.api.radio-browser.info/") }
            serversLoadedAt = System.currentTimeMillis()
        }
        servers
    }

    companion object {
        const val PAGE_SIZE = 50

        internal fun searchUrl(base: String, query: RadioBrowserQuery, offset: Int): HttpUrl =
            base.toHttpUrl().newBuilder().addPathSegments("json/stations/search")
                .apply {
                    listOf("name" to query.name, "country" to query.country,
                        "language" to query.language, "tag" to query.tag)
                        .filter { it.second.isNotBlank() }.forEach { (key, value) -> addQueryParameter(key, value) }
                }
                .addQueryParameter("codec", "MP3").addQueryParameter("hidebroken", "true")
                .addQueryParameter("order", "clickcount").addQueryParameter("reverse", "true")
                .addQueryParameter("limit", PAGE_SIZE.toString()).addQueryParameter("offset", offset.toString()).build()

        internal fun parsePage(body: String, offset: Int): RadioBrowserPage {
            val array = JSONArray(body)
            val stations = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    // Do not equate directory reachability with SoundTouch compatibility.
                    if (item.optInt("lastcheckok") != 1 || item.optInt("hls") != 0 ||
                        !item.optString("codec").equals("MP3", true)) continue
                    val name = item.optString("name").trim()
                    val url = item.optString("url_resolved").trim().ifBlank { item.optString("url").trim() }
                    val parsedUrl = runCatching { url.toHttpUrl() }.getOrNull() ?: continue
                    if (name.isBlank() || parsedUrl.encodedPath.endsWith(".m3u8", true)) continue
                    val bitrate = item.optInt("bitrate")
                    val details = listOf(item.optString("country"), item.optString("language"),
                        "MP3", if (bitrate > 0) "$bitrate kbps" else "", item.optString("tags"))
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    add(RadioStation(name, details, url, item.optString("stationuuid").takeIf { it.isNotBlank() }))
                }
            }.distinctBy { it.streamUrl }
            return RadioBrowserPage(stations, offset + array.length(), array.length() >= PAGE_SIZE)
        }
    }
}

private suspend fun discoverRadioBrowserServers(): List<String> = withContext(Dispatchers.IO) {
    InetAddress.getAllByName("all.api.radio-browser.info").map { it.canonicalHostName }
}
