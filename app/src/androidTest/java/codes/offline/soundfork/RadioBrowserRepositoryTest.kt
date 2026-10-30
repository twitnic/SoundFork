package codes.offline.soundfork

import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import kotlinx.coroutines.runBlocking
import codes.offline.soundfork.data.*
import codes.offline.soundfork.model.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RadioBrowserRepositoryTest {
    @Test fun searchUsesResolvedUrlsAndRejectsBrokenHlsAndNonMp3Streams() {
        val valid = stationJson("direct", "https://stream.test/live")
            .put("url", "https://stream.test/playlist.pls").put("country", "Germany").put("bitrate", 128)
        val json = JSONArray().put(valid)
            .put(stationJson("duplicate", "https://stream.test/live"))
            .put(stationJson("hls", "https://stream.test/hls").put("hls", 1))
            .put(stationJson("broken", "https://stream.test/broken").put("lastcheckok", 0))
            .put(stationJson("aac", "https://stream.test/aac").put("codec", "AAC"))
            .put(stationJson("invalid", "ftp://stream.test/live"))
            .put(stationJson("hls-extension", "https://stream.test/live.m3u8?token=abc"))
            .put(stationJson("fallback", "").put("url", "http://stream.test/fallback"))
        val page = RadioBrowserRepository.parsePage(json.toString(), 50)
        assertEquals(listOf("direct", "fallback"), page.stations.map { it.name })
        assertEquals("https://stream.test/live", page.stations.first().streamUrl)
        assertEquals("direct-id", page.stations.first().radioBrowserId)
        assertTrue(page.stations.first().description.contains("128 kbps"))
        assertEquals(58, page.nextOffset)
        assertFalse(page.hasMore)
    }

    @Test fun fullyFilteredPageStillAdvancesPagination() {
        val json = JSONArray()
        repeat(50) { json.put(stationJson("hls-$it", "https://stream.test/$it").put("hls", 1)) }
        val page = RadioBrowserRepository.parsePage(json.toString(), 100)
        assertTrue(page.stations.isEmpty())
        assertTrue(page.hasMore)
        assertEquals(150, page.nextOffset)
    }

    @Test fun searchRetriesAnotherMirrorAndIdentifiesTheApp() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            if (requests.size == 1) throw IOException("Mirror unavailable")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(JSONArray().put(stationJson("radio", "https://stream.test/live")).toString().toResponseBody()).build()
        }.build()
        try {
            val repository = RadioBrowserRepository(client) { listOf("one.api.radio-browser.info", "two.api.radio-browser.info") }
            val result = repository.search(RadioBrowserQuery(name = "Radio & Jazz"))
            assertEquals(1, result.stations.size)
            assertEquals(2, requests.size)
            assertNotEquals(requests[0].url.host, requests[1].url.host)
            assertTrue(requests.last().header("User-Agent")!!.startsWith("SoundFork/"))
            assertEquals("MP3", requests.last().url.queryParameter("codec"))
            assertEquals("Radio & Jazz", requests.last().url.queryParameter("name"))
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun directoryFavoritesAndSpeakerConfirmationsSurviveReloadAndHistoryEviction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("radio-browser-library-test", 0)
        preferences.edit().clear().commit()
        try {
            val station = RadioStation("Jazz", "MP3 · 128 kbps", "https://stream.test/jazz", "station-id")
            val settings = SharedPreferencesSettingsRepository(preferences)
            settings.saveRadioStations(listOf(station))
            settings.saveFavoriteStreamUrls(setOf(station.streamUrl))
            settings.confirmStream("speaker-A", station.streamUrl)
            var history = settings.recordStation(station, emptyList(), emptyList())
            assertEquals(station.radioBrowserId, history.single().asRadioStation().radioBrowserId)
            repeat(12) { index ->
                history = settings.recordStation(RadioStation("Other $index", "", "https://stream.test/$index"), history, emptyList())
            }
            val reloaded = SharedPreferencesSettingsRepository(preferences)
            assertFalse(reloaded.recentStations(emptyList()).any { it.streamUrl == station.streamUrl })
            assertEquals(station, reloaded.savedRadioStations().single())
            assertEquals(setOf(station.streamUrl), reloaded.favoriteStreamUrls())
            assertEquals(setOf(station.streamUrl), reloaded.confirmedStreams("speaker-A"))
            assertTrue(reloaded.confirmedStreams("speaker-B").isEmpty())
            reloaded.saveRadioStations(emptyList())
            reloaded.saveFavoriteStreamUrls(emptySet())
            assertTrue(SharedPreferencesSettingsRepository(preferences).savedRadioStations().isEmpty())
        } finally { preferences.edit().clear().commit() }
    }

    private fun stationJson(name: String, url: String) = JSONObject().put("name", name).put("stationuuid", "$name-id")
        .put("url_resolved", url).put("codec", "MP3").put("hls", 0).put("lastcheckok", 1)
}
