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

class RadioBrowserUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val station = RadioStation("Test Jazz Radio", "Germany · MP3 · 128 kbps", "https://stream.test/jazz", "jazz-id")
    private val state = mutableStateOf(RadioBrowserSearchState())
    private val favorites = mutableStateOf(emptySet<String>())
    private val confirmed = mutableStateOf(emptySet<String>())
    private var played: RadioStation? = null
    private var searched: RadioBrowserQuery? = null

    private fun mount() {
        compose.setContent {
            SoundForkTheme {
                LazyColumn(Modifier.fillMaxSize().testTag("discovery-list")) {
                    radioBrowserDiscovery(state.value, null, favorites.value, confirmed.value, false,
                        onQueryChanged = { state.value = RadioBrowserSearchState(query = it) },
                        onSearch = { searched = state.value.query; state.value = state.value.copy(stations = listOf(station), hasSearched = true) },
                        onToggleFavorite = { favorites.value = favorites.value + it.streamUrl },
                        onPlayStation = { played = it })
                }
            }
        }
    }

    @Test fun searchCanPlayAndFavoriteResultsAndReflectSpeakerConfirmation() {
        mount()
        compose.onNodeWithText(context.getString(R.string.search_station)).performImeAction()
        compose.runOnIdle { assertNull(searched) }
        compose.onNodeWithText(context.getString(R.string.search_station)).performTextInput("Jazz")
        compose.onNodeWithText(context.getString(R.string.search_station)).performImeAction()
        compose.onNodeWithText(context.getString(R.string.search_station)).assertIsNotFocused()
        compose.runOnIdle { assertEquals("Jazz", searched?.name) }
        compose.onNodeWithTag("discovery-list").performScrollToNode(hasText(station.name))
        compose.onNodeWithText(context.getString(R.string.radio_untested)).assertIsDisplayed()
        compose.onNodeWithText(station.name).performClick()
        compose.runOnIdle { assertEquals(station, played) }
        compose.onNodeWithContentDescription(context.getString(R.string.add_favorite)).performClick()
        compose.runOnIdle { assertEquals(setOf(station.streamUrl), favorites.value); confirmed.value = setOf(station.streamUrl) }
        compose.onNodeWithContentDescription(context.getString(R.string.remove_favorite)).assertExists()
        compose.onNodeWithText(context.getString(R.string.radio_confirmed)).assertIsDisplayed()
        compose.runOnIdle { confirmed.value = emptySet() }
        compose.onNodeWithText(context.getString(R.string.radio_untested)).assertIsDisplayed()
    }

    @Test fun genreOnlySearchIsPossibleAndAnOfflineErrorCanBeRetried() {
        mount()
        compose.onNodeWithText(context.getString(R.string.radio_browser_filters)).performClick()
        compose.onNodeWithTag("discovery-list").performScrollToNode(hasText(context.getString(R.string.radio_browser_genre)))
        compose.onNodeWithText(context.getString(R.string.radio_browser_genre)).performTextInput("jazz")
        compose.onNodeWithText(context.getString(R.string.radio_browser_genre)).performImeAction()
        compose.onNodeWithText(context.getString(R.string.radio_browser_genre)).assertIsNotFocused()
        compose.runOnIdle { assertEquals("jazz", searched?.tag) }
        compose.runOnIdle { state.value = state.value.copy(error = context.getString(R.string.radio_browser_error)) }
        compose.onNodeWithTag("discovery-list").performScrollToNode(hasText(context.getString(R.string.radio_browser_retry)))
        compose.onNodeWithText(context.getString(R.string.radio_browser_retry)).performClick()
        compose.runOnIdle { assertEquals("jazz", searched?.tag) }
    }
}
