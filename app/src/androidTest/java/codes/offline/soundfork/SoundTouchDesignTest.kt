package codes.offline.soundfork

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import codes.offline.soundfork.data.*
import codes.offline.soundfork.model.*
import codes.offline.soundfork.ui.theme.SoundForkTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SoundTouchDesignTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val station = RadioStation("Radio Chemnitz", "Lokales Radio Chemnitz", "http://radio.example/chemnitz")
    private val stations = listOf(station, RadioStation("ENERGY Sachsen", "Sachsen Livestream", "http://radio.example/energy"),
        RadioStation("Deutschlandfunk Kultur", "Kultur, Musik und Gesellschaft", "http://radio.example/dlf"))
    private val ui = mutableStateOf(SoundForkUiState(screenMode = SoundForkScreenMode.DEVICE,
        selectedHost = "192.0.2.1", selectedSummary = DeviceSummary("192.0.2.1", name = "Wohnzimmer", type = "SoundTouch 10"),
        currentRadioStation = station, radioStations = stations,
        recentRadioStations = stations.mapIndexed { i, s -> RecentRadioStation(s.name, s.description, s.streamUrl, 1780000000000L - i * 60000) },
        nowPlaying = NowPlayingState(source = "UPNP", location = station.streamUrl, playStatus = "PLAY_STATE"),
        volumeDraft = 35f, selectedVolume = VolumeState(35, 35, false),
        currentSource = SourceItem("UPNP"), sources = listOf(SourceItem("UPNP"), SourceItem("AUX")),
        presets = listOf(PresetItem(1, source = "UPNP", location = station.streamUrl))))
    private val dark = mutableStateOf(false)
    private val language = mutableStateOf("en")
    private var lastKey: String? = null
    private var savedSlot: Int? = null
    private var muteToggled = false

    private fun mount() {
        compose.setContent {
            val state = ui.value
            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(language.value))
            }
            val localizedContext = context.createConfigurationContext(configuration)
            CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration) {
            SoundForkTheme(darkTheme = dark.value) {
                SoundTouchScaffold(state,
                    onPage = { ui.value = ui.value.copy(devicePage = it) }, onDiscovery = {},
                    onKey = { key ->
                        lastKey = key
                        ui.value = ui.value.copy(nowPlaying = ui.value.nowPlaying?.copy(playStatus = if (key == "PAUSE") "PAUSE_STATE" else "PLAY_STATE"))
                    }, onVolumeChanged = { ui.value = ui.value.copy(volumeDraft = it) }, onApplyVolume = {},
                    onToggleMute = { muteToggled = true }, onSelectSource = { ui.value = ui.value.copy(currentSource = it) }
                ) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        item { Spacer(Modifier.height(8.dp)) }
                        when (state.devicePage) {
                            SoundForkDevicePage.RADIO -> {
                                item { NowPlayingCard(state.nowPlaying, state.currentRadioStation) }
                                item { RecentRadioCarousel(state.recentRadioStations, state.currentRadioStation, false, {}) }
                                item { RadioStationListCard(stations, state.currentRadioStation, "", emptySet(), null, false, {}, {}, {}) }
                            }
                            SoundForkDevicePage.PRESETS -> item {
                                PresetListCard(state.presets, null, true, { _, _ -> }, { savedSlot = it }, stations)
                            }
                            SoundForkDevicePage.DEVICE -> item {
                                SelectedDeviceCard("192.0.2.1", 8090, "Wohnzimmer", "SoundTouch 10", null, "Wohnzimmer",
                                    null, null, false, false, {}, {}, {})
                                VolumeControlCard(35f, state.selectedVolume, false, {}, {}, {})
                            }
                            else -> item { Text("Page") }
                        }
                    }
                }
            }
            }
        }
    }

    @Test fun interfaceCanSwitchBetweenEnglishAndGerman() {
        ui.value = ui.value.copy(devicePage = SoundForkDevicePage.PRESETS)
        mount()
        compose.onNodeWithText("Saved presets").assertIsDisplayed()
        compose.runOnIdle { language.value = "de" }
        compose.onNodeWithText("Gespeicherte Presets").assertIsDisplayed()
        snapshot("presets-de")
        compose.runOnIdle { ui.value = ui.value.copy(devicePage = SoundForkDevicePage.RADIO) }
        compose.onNodeWithText("Gespeicherte").assertIsDisplayed()
        snapshot("radio-de")
    }

    @Test fun playerAndSpeakerControlsRemainAccessibleAcrossPages() {
        mount()
        snapshot("radio-light")
        compose.onNodeWithContentDescription(context.getString(R.string.pause)).performClick()
        compose.runOnIdle { assertEquals("PAUSE", lastKey) }
        compose.onNodeWithContentDescription(context.getString(R.string.play)).assertIsDisplayed()
        val persistentPlayer = compose.onAllNodesWithTag("persistent_player").fetchSemanticsNodes().isNotEmpty()
        if (!persistentPlayer) {
            compose.onNodeWithTag("mini_player_open").performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.close_player)).assertIsDisplayed()
            snapshot("player-light")
            compose.onNodeWithContentDescription(context.getString(R.string.close_player)).performClick()
            compose.onNodeWithTag("speaker_controls_open").performClick()
        }
        compose.onNodeWithText(context.getString(R.string.volume)).assertIsDisplayed()
        snapshot("speaker-light")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(62f) }
        compose.runOnIdle { assertEquals(62f, ui.value.volumeDraft, 0.01f) }
        compose.onNodeWithContentDescription(context.getString(R.string.enable_mute)).performClick()
        compose.runOnIdle { assertTrue(muteToggled) }
        compose.onNodeWithText("AUX").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("AUX", ui.value.currentSource?.source) }
        compose.onNodeWithText(context.getString(R.string.device_settings)).performScrollTo().performClick()
        compose.onNode(hasText("SoundTouch 10") and hasAnyAncestor(hasTestTag("page_content"))).assertIsDisplayed()
        snapshot("device-light")
        if (compose.onAllNodesWithTag("persistent_navigation").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription(context.getString(R.string.open_menu)).performClick()
        }
        snapshot("menu-light")
        compose.onNodeWithText(context.getString(R.string.nav_presets)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.saved_presets)).assertIsDisplayed()
        snapshot("presets-light")
    }

    @Test fun presetAssignmentRequiresConfirmationAndHandlesLongNames() {
        ui.value = ui.value.copy(devicePage = SoundForkDevicePage.PRESETS,
            presets = listOf(PresetItem(1, name = "Ein sehr langer Radiosender mit viel Musik aus Sachsen und der ganzen Welt", source = "UPNP")))
        mount()
        compose.onNodeWithContentDescription(context.getString(R.string.save_to_preset, 1)).performClick()
        compose.runOnIdle { assertNull(savedSlot) }
        compose.onNodeWithText(context.getString(R.string.save)).performClick()
        compose.runOnIdle { assertEquals(1, savedSlot) }
        compose.onNodeWithContentDescription(context.getString(R.string.save_to_preset, 6)).performScrollTo().assertIsDisplayed()
        snapshot("presets-long-name")
        compose.runOnIdle { dark.value = true }
        snapshot("presets-dark")
    }

    private fun snapshot(name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // System bar changes are applied by Android after Compose finishes its frame.
        Thread.sleep(200)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SoundFork-Design")
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        requireNotNull(context.contentResolver.openOutputStream(uri)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
