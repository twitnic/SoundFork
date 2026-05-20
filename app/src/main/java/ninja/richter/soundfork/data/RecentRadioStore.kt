package ninja.richter.soundfork.data

import android.content.SharedPreferences
import android.util.Log
import ninja.richter.soundfork.model.RadioStation
import ninja.richter.soundfork.model.RecentRadioStation
import org.json.JSONArray
import org.json.JSONObject

class RecentRadioStore(
    private val preferences: SharedPreferences
) {
    fun load(catalogStations: List<RadioStation>): List<RecentRadioStation> {
        val raw = preferences.getString(KEY_RECENT_RADIOS, null)?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(raw)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val streamUrl = item.optString("streamUrl").trim()
                    if (streamUrl.isBlank()) {
                        continue
                    }
                    add(
                        RecentRadioStation(
                            name = item.optString("name").trim().ifBlank { "Unbekannter Sender" },
                            description = item.optString("description").trim().ifBlank { "Internet Radio" },
                            streamUrl = streamUrl,
                            lastPlayedAt = item.optLong("lastPlayedAt", 0L),
                            playCount = item.optInt("playCount", 1).coerceAtLeast(1)
                        ).withCatalogData(catalogStations)
                    )
                }
            }
                .distinctBy { it.streamUrl }
                .sortedByDescending { it.lastPlayedAt }
                .take(MAX_RECENT_RADIO_STATIONS)
        }.onFailure { throwable ->
            Log.w(TAG, "load() failed error=${throwable.message}", throwable)
        }.getOrElse {
            emptyList()
        }
    }

    fun record(
        station: RadioStation,
        currentStations: List<RecentRadioStation>,
        catalogStations: List<RadioStation>
    ): List<RecentRadioStation> {
        if (station.streamUrl.isBlank()) {
            return currentStations
        }

        val existing = currentStations.firstOrNull { it.streamUrl == station.streamUrl }
        val updatedStation = RecentRadioStation(
            name = station.name,
            description = station.description,
            streamUrl = station.streamUrl,
            lastPlayedAt = System.currentTimeMillis(),
            playCount = (existing?.playCount ?: 0) + 1
        ).withCatalogData(catalogStations)
        val updatedStations = (listOf(updatedStation) + currentStations.filterNot {
            it.streamUrl == station.streamUrl
        })
            .take(MAX_RECENT_RADIO_STATIONS)

        persist(updatedStations)
        return updatedStations
    }

    private fun persist(stations: List<RecentRadioStation>) {
        val jsonArray = JSONArray()
        stations.forEach { station ->
            jsonArray.put(
                JSONObject()
                    .put("name", station.name)
                    .put("description", station.description)
                    .put("streamUrl", station.streamUrl)
                    .put("lastPlayedAt", station.lastPlayedAt)
                    .put("playCount", station.playCount)
            )
        }
        preferences.edit()
            .putString(KEY_RECENT_RADIOS, jsonArray.toString())
            .apply()
    }

    private fun RecentRadioStation.withCatalogData(
        catalogStations: List<RadioStation>
    ): RecentRadioStation {
        val catalogStation = catalogStations.firstOrNull { it.streamUrl == streamUrl } ?: return this
        return copy(
            name = catalogStation.name,
            description = catalogStation.description
        )
    }

    private companion object {
        const val TAG = "RecentRadioStore"
        const val KEY_RECENT_RADIOS = "recent_radios"
        const val MAX_RECENT_RADIO_STATIONS = 10
    }
}
