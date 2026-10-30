package codes.offline.soundfork.usecase

import codes.offline.soundfork.data.BassState
import codes.offline.soundfork.data.DeviceSummary
import codes.offline.soundfork.data.SoundTouchRepository
import codes.offline.soundfork.data.ToneControlState
import codes.offline.soundfork.data.VolumeState

class DeviceControlUseCase(
    private val repository: SoundTouchRepository
) {
    suspend fun renameDevice(host: String, port: Int, name: String): DeviceSummary? {
        return repository.setDeviceName(host = host, port = port, name = name)
    }

    suspend fun setVolume(
        host: String,
        port: Int,
        targetVolume: Int,
        muteEnabled: Boolean?
    ): VolumeState {
        return repository.setVolume(
            host = host,
            port = port,
            targetVolume = targetVolume,
            muteEnabled = muteEnabled
        )
    }

    suspend fun setMute(
        host: String,
        port: Int,
        muteEnabled: Boolean,
        targetVolume: Int
    ): VolumeState {
        return repository.setMute(
            host = host,
            port = port,
            muteEnabled = muteEnabled,
            targetVolume = targetVolume
        )
    }

    suspend fun setBass(host: String, port: Int, targetBass: Int): BassState {
        return repository.setBass(host = host, port = port, targetBass = targetBass)
    }

    suspend fun setTreble(host: String, port: Int, targetTreble: Int): ToneControlState {
        return repository.setTreble(host = host, port = port, targetTreble = targetTreble)
    }
}
