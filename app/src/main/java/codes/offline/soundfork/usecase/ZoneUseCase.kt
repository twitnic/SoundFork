package codes.offline.soundfork.usecase

import codes.offline.soundfork.data.SoundTouchRepository
import codes.offline.soundfork.data.ZoneMember
import codes.offline.soundfork.data.ZoneState

class ZoneUseCase(
    private val repository: SoundTouchRepository
) {
    suspend fun setZone(
        host: String,
        port: Int,
        masterDeviceId: String,
        masterIpAddress: String,
        members: List<ZoneMember>
    ): ZoneState? {
        return repository.setZone(
            host = host,
            port = port,
            masterDeviceId = masterDeviceId,
            masterIpAddress = masterIpAddress,
            members = members
        )
    }

    suspend fun addZoneSlave(
        host: String,
        port: Int,
        masterDeviceId: String,
        slave: ZoneMember
    ): ZoneState? {
        return repository.addZoneSlave(
            host = host,
            port = port,
            masterDeviceId = masterDeviceId,
            slave = slave
        )
    }

    suspend fun removeZoneSlave(
        host: String,
        port: Int,
        masterDeviceId: String,
        slave: ZoneMember
    ): ZoneState? {
        return repository.removeZoneSlave(
            host = host,
            port = port,
            masterDeviceId = masterDeviceId,
            slave = slave
        )
    }
}
