package ninja.richter.soundfork.data

import android.content.Context
import android.content.SharedPreferences
import ninja.richter.soundfork.model.RadioStation
import ninja.richter.soundfork.model.RecentRadioStation

data class SavedSpeaker(val host: String, val port: Int)

interface SettingsRepository {
    fun lastSpeaker(): SavedSpeaker?
    fun saveLastSpeaker(speaker: SavedSpeaker)
    fun favoriteStreamUrls(): Set<String>
    fun saveFavoriteStreamUrls(urls: Set<String>)
    fun savedRadioStations(): List<RadioStation>
    fun saveRadioStations(stations: List<RadioStation>)
    fun confirmedStreams(speaker: String): Set<String>
    fun confirmStream(speaker: String, streamUrl: String): Set<String>
    fun recentStations(catalog: List<RadioStation>): List<RecentRadioStation>
    fun recordStation(station: RadioStation, recents: List<RecentRadioStation>, catalog: List<RadioStation>): List<RecentRadioStation>
}

class SharedPreferencesSettingsRepository(private val preferences: SharedPreferences) : SettingsRepository {
    constructor(context: Context) : this(context.getSharedPreferences("soundfork_preferences", Context.MODE_PRIVATE))

    private val recentRadioStore = RecentRadioStore(preferences)
    private val radioLibrary = RadioLibraryStore(preferences)

    override fun savedRadioStations() = radioLibrary.load()
    override fun saveRadioStations(stations: List<RadioStation>) = radioLibrary.save(stations)
    override fun confirmedStreams(speaker: String) = radioLibrary.confirmedStreams(speaker)
    override fun confirmStream(speaker: String, streamUrl: String) = radioLibrary.confirmStream(speaker, streamUrl)

    override fun lastSpeaker(): SavedSpeaker? {
        val host = preferences.getString("last_host", null)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return SavedSpeaker(host, preferences.getInt("last_port", SoundTouchRepository.DEFAULT_PORT))
    }

    override fun saveLastSpeaker(speaker: SavedSpeaker) {
        preferences.edit().putString("last_host", speaker.host).putInt("last_port", speaker.port).apply()
    }

    override fun favoriteStreamUrls(): Set<String> = preferences.getStringSet("favorite_radio_stream_urls", emptySet())
        .orEmpty().filter { it.isNotBlank() }.toSet()

    override fun saveFavoriteStreamUrls(urls: Set<String>) {
        preferences.edit().putStringSet("favorite_radio_stream_urls", urls.toSet()).apply()
    }

    override fun recentStations(catalog: List<RadioStation>): List<RecentRadioStation> = recentRadioStore.load(catalog)

    override fun recordStation(station: RadioStation, recents: List<RecentRadioStation>, catalog: List<RadioStation>): List<RecentRadioStation> =
        recentRadioStore.record(station, recents, catalog)
}
