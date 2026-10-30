package codes.offline.soundfork.usecase

import codes.offline.soundfork.core.runSuspendCatching

import codes.offline.soundfork.data.BoseRecentItem
import codes.offline.soundfork.data.PresetItem
import codes.offline.soundfork.data.SoundTouchRepository

class PresetUseCase(
    private val repository: SoundTouchRepository
) {
    suspend fun playPreset(host: String, port: Int, preset: PresetItem?, presetId: Int) {
        val id = preset?.id?.coerceIn(1, 6) ?: presetId.coerceIn(1, 6)
        when {
            preset?.shouldPlayViaDlna() == true -> {
                repository.playDlnaStream(host = host, streamUrl = preset.location.orEmpty())
            }
            preset?.source?.isNotBlank() == true -> {
                runSuspendCatching {
                    repository.selectPreset(host = host, port = port, preset = preset)
                }.getOrElse {
                    repository.playPreset(host = host, port = port, presetId = id)
                }
            }
            else -> repository.playPreset(host = host, port = port, presetId = id)
        }
    }

    suspend fun playRecent(host: String, port: Int, recent: BoseRecentItem) {
        if (recent.shouldPlayViaDlna()) {
            repository.playDlnaStream(host = host, streamUrl = recent.location.orEmpty())
        } else {
            repository.selectRecent(host = host, port = port, recent = recent)
        }
    }

    suspend fun saveCurrentAsPreset(host: String, port: Int, presetId: Int) {
        repository.saveCurrentAsPreset(host = host, port = port, presetId = presetId)
    }

    private fun PresetItem.shouldPlayViaDlna(): Boolean {
        return source.equals("UPNP", ignoreCase = true) && !location.isNullOrBlank()
    }

    private fun BoseRecentItem.shouldPlayViaDlna(): Boolean {
        return source.equals("UPNP", ignoreCase = true) && !location.isNullOrBlank()
    }
}
