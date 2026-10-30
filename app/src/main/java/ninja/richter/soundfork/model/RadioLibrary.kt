package ninja.richter.soundfork.model

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Local playback never depends on the directory or on a bundled station catalogue. */
fun localRadioLibrary(
    savedFavorites: List<RadioStation>,
    recentStations: List<RecentRadioStation>,
    favoriteStreamUrls: Set<String>
): List<RadioStation> {
    val favorites = savedFavorites.filter { it.streamUrl in favoriteStreamUrls }
    val history = recentStations.map { it.asRadioStation() }
    // Older versions only saved favorite URLs. Recover their metadata from history when possible.
    val legacyFavorites = favoriteStreamUrls.filter { it.isNotBlank() }.sorted().map { url ->
        RadioStation(url.toHttpUrlOrNull()?.host ?: url, url, url)
    }
    return (favorites + history + legacyFavorites).filter { it.streamUrl.toHttpUrlOrNull() != null }.distinctBy { it.streamUrl }
}
