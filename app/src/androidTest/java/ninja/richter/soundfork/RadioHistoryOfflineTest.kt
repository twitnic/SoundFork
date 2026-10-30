package ninja.richter.soundfork

import androidx.test.platform.app.InstrumentationRegistry
import ninja.richter.soundfork.data.SharedPreferencesSettingsRepository
import ninja.richter.soundfork.model.*
import org.junit.Assert.*
import org.junit.Test

class RadioHistoryOfflineTest {
    @Test fun nonRadioSourcesDoNotPolluteOrReplaceTheReplayableHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("radio-history-sources-test", 0)
        preferences.edit().clear().putString("recent_radios", """[
            {"name":"Service track","streamUrl":"spotify:track:123","lastPlayedAt":2},
            {"name":"Radio","streamUrl":"https://radio.test/live","lastPlayedAt":1}
        ]""").commit()
        try {
            val settings = SharedPreferencesSettingsRepository(preferences)
            val history = settings.recentStations(emptyList())
            assertEquals("Radio", history.single().name)
            assertEquals(history, settings.recordStation(RadioStation("Service track", "", "spotify:track:456"), history, emptyList()))
            assertEquals(history, settings.recentStations(emptyList()))
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun historyAndLegacyFavoritesRemainPlayableAfterReloadWithoutADirectoryCatalogue() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("radio-history-offline-test", 0)
        preferences.edit().clear().commit()
        try {
            val first = RadioStation("First station", "Germany · MP3", "https://first.test/live", "first-id")
            val second = RadioStation("Second station", "Jazz · MP3", "https://second.test/live", "second-id")
            var settings = SharedPreferencesSettingsRepository(preferences)
            var history = settings.recordStation(first, emptyList(), emptyList())
            history = settings.recordStation(second, history, emptyList())
            settings.saveFavoriteStreamUrls(setOf(first.streamUrl)) // Older URL-only favorites.
            settings = SharedPreferencesSettingsRepository(preferences)
            val reloaded = settings.recentStations(emptyList())
            val library = localRadioLibrary(settings.savedRadioStations(), reloaded, settings.favoriteStreamUrls())
            assertEquals(setOf(first, second), library.toSet())
            assertEquals(1, reloaded.first { it.streamUrl == first.streamUrl }.playCount)
            history = settings.recordStation(first, reloaded, emptyList())
            assertEquals(first, history.first().asRadioStation())
            assertEquals(2, history.first().playCount)
            assertEquals(first, settings.recentStations(emptyList()).first().asRadioStation())
        } finally { preferences.edit().clear().commit() }
    }

    @Test fun onlyTheLatestTenStationsRemainInHistoryWhileFavoritesStayAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("radio-history-limit-test", 0)
        preferences.edit().clear().commit()
        try {
            val settings = SharedPreferencesSettingsRepository(preferences)
            val favorite = RadioStation("Old favorite", "MP3", "https://favorite.test/live")
            settings.saveRadioStations(listOf(favorite))
            settings.saveFavoriteStreamUrls(setOf(favorite.streamUrl))
            var history = settings.recordStation(favorite, emptyList(), emptyList())
            repeat(12) { index ->
                history = settings.recordStation(RadioStation("Station $index", "MP3", "https://radio.test/$index"), history, emptyList())
            }
            val reloaded = SharedPreferencesSettingsRepository(preferences)
            history = reloaded.recentStations(emptyList())
            assertEquals(10, history.size)
            assertFalse(history.any { it.streamUrl == favorite.streamUrl })
            assertFalse(history.any { it.streamUrl == "https://radio.test/0" })
            val library = localRadioLibrary(reloaded.savedRadioStations(), history, reloaded.favoriteStreamUrls())
            assertEquals(11, library.size)
            assertTrue(library.contains(favorite))
        } finally { preferences.edit().clear().commit() }
    }
}
