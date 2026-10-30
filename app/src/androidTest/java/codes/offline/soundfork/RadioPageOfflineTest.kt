package codes.offline.soundfork

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import codes.offline.soundfork.model.*
import codes.offline.soundfork.ui.theme.SoundForkTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RadioPageOfflineTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val station = RadioStation("Cached Jazz", "MP3 · 128 kbps", "https://radio.test/live", "station-id")
    private val recent = RecentRadioStation(station.name, station.description, station.streamUrl, 123, radioBrowserId = station.radioBrowserId)
    private val state = mutableStateOf(SoundForkUiState())
    private var played: RadioStation? = null

    private fun mount() {
        compose.setContent {
            SoundForkTheme {
                LazyColumn(Modifier.fillMaxSize().testTag("radio-page")) {
                    radioPage(state.value,
                        onRadioBrowserOpen = { state.value = state.value.copy(isRadioBrowserOpen = it) },
                        onRadioBrowserQueryChanged = {},
                        onSearchRadioBrowser = { fail("Offline replay must not query the directory") },
                        onRadioSearchQueryChanged = { state.value = state.value.copy(radioSearchQuery = it) },
                        onToggleFavorite = { state.value = state.value.copy(favoriteRadioStreamUrls = setOf(it.streamUrl)) },
                        onPlayStation = { played = it })
                }
            }
        }
    }

    @Test fun directoryFailureOffersLocalLibraryAndHistoryCanBeReplayedAndFavorited() {
        state.value = SoundForkUiState(
            radioStations = localRadioLibrary(emptyList(), listOf(recent), emptySet()),
            recentRadioStations = listOf(recent),
            radioBrowser = RadioBrowserSearchState(error = context.getString(R.string.radio_browser_error)))
        mount()
        compose.onNodeWithTag("radio-page").performScrollToNode(hasText(context.getString(R.string.radio_browser_open_library)))
        compose.onNodeWithText(context.getString(R.string.radio_browser_open_library)).performClick()
        compose.onNodeWithTag("radio-page").performScrollToNode(hasTestTag("radio-library"))
        compose.onNode(hasText(station.name) and hasAnyAncestor(hasTestTag("radio-library"))).performClick()
        compose.runOnIdle { assertEquals(station, played) }
        compose.onNodeWithContentDescription(context.getString(R.string.add_favorite)).performClick()
        compose.onNodeWithText(context.getString(R.string.favorites)).performClick()
        compose.onNode(hasText(station.name) and hasAnyAncestor(hasTestTag("radio-library"))).assertIsDisplayed()
    }

    @Test fun aFreshLibraryExplainsHowToAddStationsInsteadOfUsingAFallbackStation() {
        state.value = SoundForkUiState(isRadioBrowserOpen = false)
        mount()
        compose.onNodeWithText(context.getString(R.string.radio_library_empty)).assertExists()
        compose.onAllNodesWithContentDescription(context.getString(R.string.add_favorite)).assertCountEquals(0)
        compose.onNodeWithText(context.getString(R.string.radio_browser_title)).performClick()
        compose.onNodeWithTag("radio-page").performScrollToNode(hasText(context.getString(R.string.search_station)))
        compose.onNodeWithText(context.getString(R.string.search_station)).performImeAction()
        compose.onNodeWithText(context.getString(R.string.search_station)).assertIsNotFocused()
    }
}
