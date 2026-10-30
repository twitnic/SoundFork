package ninja.richter.soundfork.data

import android.content.SharedPreferences
import androidx.core.content.edit
import ninja.richter.soundfork.model.RadioStation
import org.json.JSONArray
import org.json.JSONObject

/** Keep complete directory favorites, independently of transient search results and recents. */
internal class RadioLibraryStore(private val preferences: SharedPreferences) {
    fun load(): List<RadioStation> = runCatching {
        val array = JSONArray(preferences.getString("radio_browser_favorites", "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (url.isBlank()) continue
                add(RadioStation(item.optString("name"), item.optString("description"), url,
                    item.optString("id").takeIf { it.isNotBlank() }))
            }
        }.distinctBy { it.streamUrl }
    }.getOrDefault(emptyList())

    fun save(stations: List<RadioStation>) {
        val array = JSONArray()
        stations.distinctBy { it.streamUrl }.forEach {
            array.put(JSONObject().put("name", it.name).put("description", it.description)
                .put("url", it.streamUrl).put("id", it.radioBrowserId.orEmpty()))
        }
        preferences.edit { putString("radio_browser_favorites", array.toString()) }
    }

    fun confirmedStreams(speaker: String): Set<String> =
        preferences.getStringSet("confirmed_radio:$speaker", emptySet()).orEmpty().toSet()

    fun confirmStream(speaker: String, streamUrl: String): Set<String> {
        val updated = confirmedStreams(speaker) + streamUrl
        preferences.edit { putStringSet("confirmed_radio:$speaker", updated) }
        return updated
    }
}
