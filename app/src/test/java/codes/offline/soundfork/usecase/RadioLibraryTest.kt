package codes.offline.soundfork.usecase

import codes.offline.soundfork.model.*
import org.junit.Assert.*
import org.junit.Test

class RadioLibraryTest {
    private val station = RadioStation("Jazz", "MP3", "https://radio.test/jazz", "jazz-id")
    private val recent = RecentRadioStation(station.name, station.description, station.streamUrl, 123, radioBrowserId = station.radioBrowserId)

    @Test fun aFreshInstallStartsWithDiscoveryAndNoBundledStations() {
        assertTrue(SoundForkUiState().isRadioBrowserOpen)
        assertTrue(localRadioLibrary(emptyList(), emptyList(), emptySet()).isEmpty())
    }

    @Test fun historyRemainsSelectableWithoutDirectoryResultsOrFavorites() {
        assertEquals(listOf(station), localRadioLibrary(emptyList(), listOf(recent), emptySet()))
    }

    @Test fun unFavoritingRetainsHistoryButDropsAnUnplayedFavorite() {
        assertEquals(listOf(station), localRadioLibrary(listOf(station), listOf(recent), emptySet()))
        assertTrue(localRadioLibrary(listOf(station), emptyList(), emptySet()).isEmpty())
    }

    @Test fun favoritesAreKeptAfterHistoryEvictionAndDoNotProduceDuplicates() {
        val favorites = setOf(station.streamUrl)
        assertEquals(listOf(station), localRadioLibrary(listOf(station), listOf(recent), favorites))
        assertEquals(listOf(station), localRadioLibrary(listOf(station), emptyList(), favorites))
    }

    @Test fun legacyFavoriteUrlsRecoverNamesFromHistoryWithoutAnXmlCatalogue() {
        val unknown = "https://other.test/live"
        val library = localRadioLibrary(emptyList(), listOf(recent), setOf(station.streamUrl, unknown))
        assertEquals(station, library.first())
        assertEquals("other.test", library.last().name)
        assertEquals(unknown, library.last().streamUrl)
    }

    @Test fun serviceSpecificLocationsAreNotOfferedAsDirectRadioStreams() {
        val library = localRadioLibrary(emptyList(), listOf(recent.copy(streamUrl = "spotify:track:123")),
            setOf("", "spotify:track:123", "ftp://radio.test/live"))
        assertTrue(library.isEmpty())
    }
}
