package ninja.richter.soundfork

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import ninja.richter.soundfork.data.*
import ninja.richter.soundfork.model.*
import ninja.richter.soundfork.ui.theme.SoundForkTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Run on compact, portrait tablet and landscape tablet windows; measures actual layout constraints. */
class SoundTouchAdaptiveLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val stations = listOf(
        RadioStation("Radio Chemnitz", "Der beste Musikmix", "https://example.test/chemnitz"),
        RadioStation("ENERGY Sachsen", "Hit Music Only", "https://example.test/energy"),
        RadioStation("Deutschlandfunk Kultur", "Kultur, Musik und Gesellschaft", "https://example.test/kultur")
    )
    private val ui = mutableStateOf(SoundForkUiState(
        screenMode = SoundForkScreenMode.DEVICE,
        selectedHost = "192.0.2.1",
        selectedSummary = DeviceSummary("192.0.2.1", name = "Wohnzimmer", type = "SoundTouch 10"),
        currentRadioStation = stations.first(), radioStations = stations,
        nowPlaying = NowPlayingState(source = "UPNP", playStatus = "PLAY_STATE"),
        recentRadioStations = stations.map { RecentRadioStation(it.name, it.description, it.streamUrl, 1780000000000L) },
        volumeDraft = 35f, selectedVolume = VolumeState(35, 35, false),
        currentSource = SourceItem("UPNP"), sources = listOf(SourceItem("UPNP"), SourceItem("AUX"))
    ))
    private val dark = mutableStateOf(false)
    private val fontScale = mutableStateOf(1f)
    private val windowWidth = mutableStateOf<Dp?>(null)
    private var lastKey: String? = null
    private var volumeApplied = false
    private var muteToggled = false

    private fun mount() {
        compose.setContent {
            val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) }
            val localizedContext = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) {
                SoundForkTheme(darkTheme = dark.value) {
                    val state = ui.value
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.widthIn(max = windowWidth.value ?: Dp.Infinity).fillMaxSize()) {
                            SoundTouchScaffold(state,
                                onPage = { ui.value = ui.value.copy(devicePage = it) },
                                onDiscovery = { ui.value = ui.value.copy(screenMode = SoundForkScreenMode.DISCOVERY) },
                                onKey = {
                                    lastKey = it
                                    ui.value = ui.value.copy(nowPlaying = ui.value.nowPlaying?.copy(
                                        playStatus = if (it == "PAUSE") "PAUSE_STATE" else "PLAY_STATE"))
                                },
                                onVolumeChanged = { ui.value = ui.value.copy(volumeDraft = it) },
                                onApplyVolume = { volumeApplied = true }, onToggleMute = { muteToggled = true },
                                onSelectSource = { ui.value = ui.value.copy(currentSource = it) }
                            ) { padding ->
                                LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    item { Spacer(Modifier.height(8.dp)) }
                                    if (state.screenMode == SoundForkScreenMode.DISCOVERY) {
                                        item { Text("Lautsprecher verbinden", Modifier.testTag("discovery_content")) }
                                    } else when (state.devicePage) {
                                        SoundForkDevicePage.RADIO -> {
                                            item { NowPlayingCard(state.nowPlaying, state.currentRadioStation) }
                                            item { RecentRadioCarousel(state.recentRadioStations, state.currentRadioStation, false, {}) }
                                            item { RadioStationListCard(stations, state.currentRadioStation, "", emptySet(), null, false, {}, {}, {}) }
                                        }
                                        SoundForkDevicePage.PRESETS -> item {
                                            PresetListCard(emptyList(), null, true, { _, _ -> }, {}, stations)
                                        }
                                        else -> item { Text(state.devicePage.name, Modifier.testTag("selected_page")) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun layoutAndNavigationFollowAvailableWidth() {
        mount()
        val width = compose.onRoot().getUnclippedBoundsInRoot().width
        if (width >= 600.dp) {
            compose.onNodeWithTag("persistent_navigation").assertIsDisplayed()
            compose.onNodeWithContentDescription("Menü öffnen").assertDoesNotExist()
        } else {
            compose.onNodeWithTag("persistent_navigation").assertDoesNotExist()
            compose.onNodeWithContentDescription("Menü öffnen").assertIsDisplayed()
        }
        if (width >= 1000.dp) {
            compose.onNodeWithTag("persistent_player").assertIsDisplayed()
            compose.onNodeWithTag("mini_player_open").assertDoesNotExist()
            val navigation = compose.onNodeWithTag("persistent_navigation").getUnclippedBoundsInRoot()
            val content = compose.onNodeWithTag("page_content").getUnclippedBoundsInRoot()
            val player = compose.onNodeWithTag("persistent_player").getUnclippedBoundsInRoot()
            assertTrue(navigation.right <= content.left)
            assertTrue(content.right <= player.left)
            assertTrue(content.width >= 400.dp)
            compose.onNodeWithText("Lautstärke").assertIsDisplayed()
        } else {
            compose.onNodeWithTag("persistent_player").assertDoesNotExist()
            compose.onNodeWithTag("mini_player_open").assertIsDisplayed()
        }
        snapshot("radio")
        openNavigation(width.value)
        compose.onNodeWithText("Presets").performScrollTo().performClick()
        compose.onNodeWithText("Gespeicherte Presets").assertIsDisplayed()
        snapshot("presets")
        openNavigation(width.value)
        compose.onNodeWithText("Debug").performScrollTo().performClick()
        compose.onNodeWithTag("selected_page").assertTextEquals("DEBUG")
        openNavigation(width.value)
        compose.onNodeWithText("Lautsprecher suchen").performScrollTo().performClick()
        compose.onNodeWithTag("discovery_content").assertIsDisplayed()
        compose.onNodeWithTag("persistent_player").assertDoesNotExist()
        compose.onNodeWithTag("persistent_navigation").assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("page_content").getUnclippedBoundsInRoot().width <= 680.dp)
    }

    @Test fun playbackVolumeAndSourcesWorkAcrossPages() {
        mount()
        val width = compose.onRoot().getUnclippedBoundsInRoot().width
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.runOnIdle { assertEquals("PAUSE", lastKey) }
        compose.onNodeWithContentDescription("Play").assertIsDisplayed()
        if (width < 1000.dp) compose.onNodeWithTag("speaker_controls_open").performClick()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(62f) }
        compose.runOnIdle {
            assertEquals(62f, ui.value.volumeDraft, 0.01f)
            assertTrue(volumeApplied)
        }
        compose.onNodeWithContentDescription(controlText(R.string.enable_mute)).performScrollTo().performClick()
        compose.runOnIdle { assertTrue(muteToggled) }
        compose.onNodeWithText("AUX").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("AUX", ui.value.currentSource?.source) }
        compose.onNodeWithText(controlText(R.string.device_settings)).performScrollTo().performClick()
        compose.onNodeWithTag("selected_page").assertTextEquals("DEVICE")
        if (width >= 1000.dp) {
            compose.onNodeWithContentDescription("Stop").performScrollTo().performClick()
            compose.runOnIdle { assertEquals("STOP", lastKey) }
        } else compose.onNodeWithTag("mini_player_open").assertIsDisplayed()
    }

    @Test fun largeTextAndDarkThemeRemainScrollable() {
        ui.value = ui.value.copy(currentRadioStation = stations.first().copy(
            name = "Ein sehr langer Sendername mit Musik aus Sachsen und der ganzen Welt"))
        dark.value = true
        fontScale.value = 1.5f
        mount()
        if (compose.onRoot().getUnclippedBoundsInRoot().width < 1000.dp) {
            compose.onNodeWithTag("speaker_controls_open").performClick()
        }
        compose.onNodeWithText("AUX").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(controlText(R.string.device_settings)).performScrollTo().assertIsDisplayed()
        snapshot("dark")
        if (compose.onAllNodesWithTag("persistent_navigation").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Lautsprecher suchen").performScrollTo().assertIsDisplayed()
        }
    }

    @Test fun resizingDismissesOverlaysAndPreservesDeviceState() {
        mount()
        // Only a real wide window can exercise both ends of the transition.
        assumeTrue(compose.onRoot().getUnclippedBoundsInRoot().width >= 1000.dp)
        compose.runOnIdle { windowWidth.value = 411.dp }
        compose.onNodeWithContentDescription("Menü öffnen").performClick()
        compose.onNodeWithText("Presets").assertIsDisplayed()
        compose.runOnIdle { windowWidth.value = null }
        compose.onNodeWithTag("persistent_player").assertIsDisplayed()
        compose.onNodeWithContentDescription("Menü öffnen").assertDoesNotExist()
        compose.runOnIdle { windowWidth.value = 411.dp }
        compose.onNodeWithTag("mini_player_open").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.close_player)).assertIsDisplayed()
        compose.runOnIdle {
            windowWidth.value = null
            ui.value = ui.value.copy(devicePage = SoundForkDevicePage.PRESETS, volumeDraft = 62f)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.close_player)).assertDoesNotExist()
        compose.onNodeWithText("Gespeicherte Presets").assertIsDisplayed()
        compose.onNodeWithText("62").assertIsDisplayed()
        compose.runOnIdle { windowWidth.value = 411.dp }
        compose.onNodeWithTag("speaker_controls_open").performClick()
        compose.onNodeWithText(context.getString(R.string.device_settings)).assertIsDisplayed()
        compose.runOnIdle { windowWidth.value = null }
        compose.onNodeWithTag("persistent_player").assertIsDisplayed()
        compose.runOnIdle { windowWidth.value = 411.dp }
        compose.onNodeWithTag("mini_player_open").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.device_settings)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(SoundForkDevicePage.PRESETS, ui.value.devicePage)
            assertEquals(62f, ui.value.volumeDraft, 0.01f)
        }
    }

    private fun openNavigation(width: Float) {
        if (width < 600f) compose.onNodeWithContentDescription("Menü öffnen").performClick()
    }

    private fun controlText(resource: Int): String {
        // Native sheet windows use the activity's locale, while the inline preview is German.
        if (compose.onAllNodesWithTag("persistent_player").fetchSemanticsNodes().isEmpty()) {
            return context.getString(resource)
        }
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) }
        return context.createConfigurationContext(configuration).getString(resource)
    }

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val configuration = context.resources.configuration
        val fileName = "adaptive-${configuration.screenWidthDp}-${configuration.screenHeightDp}-$name.png"
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        context.openFileOutput(fileName, android.content.Context.MODE_PRIVATE).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}

private val DpRect.width get() = right - left
