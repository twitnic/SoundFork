package codes.offline.soundfork

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import codes.offline.soundfork.model.RadioBrowserQuery
import codes.offline.soundfork.model.RadioBrowserSearchState
import codes.offline.soundfork.model.RadioStation

/** Result rows belong to the page's LazyColumn, including when additional pages are loaded. */
fun LazyListScope.radioBrowserDiscovery(
    state: RadioBrowserSearchState,
    activeStation: RadioStation?,
    favoriteStreamUrls: Set<String>,
    confirmedStreamUrls: Set<String>,
    isStarting: Boolean,
    onQueryChanged: (RadioBrowserQuery) -> Unit,
    onSearch: (Boolean) -> Unit,
    onToggleFavorite: (RadioStation) -> Unit,
    onPlayStation: (RadioStation) -> Unit,
    onOpenLibrary: (() -> Unit)? = null
) {
    item(key = "radio-browser-search") {
        RadioBrowserSearchCard(state, onQueryChanged) { onSearch(false) }
    }
    items(state.stations, key = { "radio-browser:${it.streamUrl}" }) { station ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            RadioStationRow(station, station.matches(activeStation), station.streamUrl in favoriteStreamUrls,
                !isStarting, { onToggleFavorite(station) }, { onPlayStation(station) },
                radioCompatibilityLabel(station, confirmedStreamUrls))
        }
    }
    item(key = "radio-browser-status") {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.isSearching) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.radio_browser_searching))
                }
            } else if (state.error != null) {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                onOpenLibrary?.let { open ->
                    TextButton(open) { Text(stringResource(R.string.radio_browser_open_library)) }
                }
                OutlinedButton({ onSearch(state.hasSearched && state.hasMore) }) { Text(stringResource(R.string.radio_browser_retry)) }
            } else if (state.hasSearched) {
                if (state.stations.isEmpty()) Text(stringResource(R.string.radio_browser_no_results))
                if (state.hasMore) OutlinedButton({ onSearch(true) }) { Text(stringResource(R.string.radio_browser_more)) }
            }
        }
    }
}

@Composable
private fun RadioBrowserSearchCard(state: RadioBrowserSearchState, onQueryChanged: (RadioBrowserQuery) -> Unit, onSearch: () -> Unit) {
    var showFilters by rememberSaveable { mutableStateOf(listOf(state.query.country, state.query.language, state.query.tag).any { it.isNotBlank() }) }
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val query = state.query
    val submitSearch = {
        keyboard?.hide()
        focusManager.clearFocus()
        if (!query.isEmpty() && !state.isSearching) onSearch()
    }
    val searchKeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
    val searchKeyboardActions = KeyboardActions(onSearch = { submitSearch() })
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.radio_browser_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.radio_browser_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(query.name, { onQueryChanged(query.copy(name = it)) }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_station)) }, singleLine = true,
            keyboardOptions = searchKeyboardOptions,
            keyboardActions = searchKeyboardActions)
        OutlinedButton({ showFilters = !showFilters }) {
            Text(stringResource(if (showFilters) R.string.radio_browser_hide_filters else R.string.radio_browser_filters))
        }
        if (showFilters) {
            OutlinedTextField(query.country, { onQueryChanged(query.copy(country = it)) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.radio_browser_country)) }, singleLine = true,
                keyboardOptions = searchKeyboardOptions, keyboardActions = searchKeyboardActions)
            OutlinedTextField(query.language, { onQueryChanged(query.copy(language = it)) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.radio_browser_language)) }, singleLine = true,
                keyboardOptions = searchKeyboardOptions, keyboardActions = searchKeyboardActions)
            OutlinedTextField(query.tag, { onQueryChanged(query.copy(tag = it)) }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.radio_browser_genre)) }, singleLine = true,
                keyboardOptions = searchKeyboardOptions, keyboardActions = searchKeyboardActions)
        }
        TextButton({ uriHandler.openUri("https://www.radio-browser.info/") }) {
            Text(stringResource(R.string.radio_browser_attribution), style = MaterialTheme.typography.labelSmall)
        }
    }
}
