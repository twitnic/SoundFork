package ninja.richter.soundfork

import androidx.test.platform.app.InstrumentationRegistry
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import ninja.richter.soundfork.data.*
import ninja.richter.soundfork.model.RadioStation
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class RepositoryReliabilityTest {
    @Test fun existingSavedSpeakerFavoritesAndRecentHistoryRemainCompatible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("settings-migration-test", 0)
        preferences.edit().clear().putString("last_host", "192.0.2.42").putInt("last_port", 8099)
            .putStringSet("favorite_radio_stream_urls", setOf("http://radio.test/live", ""))
            .putString("recent_radios", """[{"name":"Old name","streamUrl":"http://radio.test/live","lastPlayedAt":1,"playCount":3}]""")
            .commit()
        try {
            val settings = SharedPreferencesSettingsRepository(preferences)
            val station = RadioStation("Radio Chemnitz", "Lokal", "http://radio.test/live")
            assertEquals(SavedSpeaker("192.0.2.42", 8099), settings.lastSpeaker())
            assertEquals(setOf(station.streamUrl), settings.favoriteStreamUrls())
            assertEquals("Radio Chemnitz", settings.recentStations(listOf(station)).single().name)
            val history = settings.recordStation(station, settings.recentStations(listOf(station)), listOf(station))
            assertEquals(4, history.single().playCount)
            assertEquals(history, settings.recentStations(listOf(station)))
            settings.saveLastSpeaker(SavedSpeaker("192.0.2.99", 8090))
            assertEquals("192.0.2.99", preferences.getString("last_host", null))
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun invalidInfoCannotProduceASuccessfulSnapshot() = runBlocking {
        withRepository(info = "<html>Not a SoundTouch</html>") { repository ->
            try {
                repository.loadSnapshot("soundtouch.test")
                fail("Invalid /info must reject the connection")
            } catch (error: SpeakerRequestException) {
                assertEquals(SpeakerFailure.INVALID_RESPONSE, error.failure)
            }
        }
    }

    @Test fun optionalEndpointsMayFailWithoutRejectingAValidSpeaker() = runBlocking {
        withRepository(info = """<info deviceID="ABC"><name>Test</name><type>SoundTouch 10</type></info>""") { repository ->
            val snapshot = repository.loadSnapshot("soundtouch.test")
            assertEquals("Test", snapshot.summary?.name)
            assertTrue(snapshot.endpointResults.any { it.resolvedPath == "/bass" && !it.isError && it.httpCode == 404 })
            assertTrue(snapshot.endpointResults.any { it.resolvedPath == "/volume" && it.isError })
        }
    }

    @Test fun infoTimeoutIsReportedWithItsActualFailureType() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { throw SocketTimeoutException("Timeout") }.build()
        try {
            try {
                SoundTouchRepository(client).loadSnapshot("soundtouch.test")
                fail("Timeout must not return an empty successful snapshot")
            } catch (error: SpeakerRequestException) {
                assertEquals(SpeakerFailure.TIMEOUT, error.failure)
            }
        } finally { client.dispatcher.executorService.shutdown() }
    }

    private suspend fun withRepository(info: String, block: suspend (SoundTouchRepository) -> Unit) {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            val code = if (path == "/info" || path == "/capabilities") 200 else 404
            val body = if (path == "/info") info else if (path == "/capabilities") "<capabilities/>" else "<error/>"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                .body(body.toResponseBody()).build()
        }.build()
        try { block(SoundTouchRepository(client)) } finally { client.dispatcher.executorService.shutdown() }
    }
}
