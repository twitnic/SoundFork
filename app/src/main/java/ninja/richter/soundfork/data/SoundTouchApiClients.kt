package ninja.richter.soundfork.data

import java.io.IOException

internal data class NetworkResult(
    val code: Int?,
    val body: String?,
    val exception: IOException?
)

internal fun interface SoundTouchRequestExecutor {
    suspend fun request(
        host: String,
        port: Int,
        path: String,
        method: String,
        body: String?
    ): NetworkResult
}

internal class SoundTouchDeviceApi(
    private val requestExecutor: SoundTouchRequestExecutor
) {
    suspend fun setVolume(
        host: String,
        port: Int,
        targetVolume: Int,
        muteEnabled: Boolean? = null
    ): VolumeState {
        val clampedVolume = targetVolume.coerceIn(0, 100)
        val postBody = buildString {
            append("<volume>")
            append(clampedVolume)
            if (muteEnabled != null) {
                append("<muteenabled>")
                append(muteEnabled)
                append("</muteenabled>")
            }
            append("</volume>")
        }

        val postResult = post(host, port, "/volume", postBody)
        val postCode = postResult.code
        if (postCode == null || postCode !in 200..299) {
            throw IOException("Volume konnte nicht gesetzt werden (HTTP ${postCode ?: "n/a"})")
        }

        val parsedFromPost = postResult.body?.let(SoundTouchXml::parseVolume)
        val refreshed = readVolumeStateOrNull(host, port)
        return refreshed ?: parsedFromPost ?: VolumeState(
            targetVolume = clampedVolume,
            muteEnabled = muteEnabled
        )
    }

    suspend fun setMute(
        host: String,
        port: Int,
        muteEnabled: Boolean,
        targetVolume: Int? = null
    ): VolumeState {
        val current = readVolumeStateOrNull(host, port)
        val effectiveTarget = targetVolume
            ?: current?.targetVolume
            ?: current?.actualVolume
            ?: 0
        return setVolume(
            host = host,
            port = port,
            targetVolume = effectiveTarget,
            muteEnabled = muteEnabled
        )
    }

    suspend fun readVolumeStateOrNull(host: String, port: Int): VolumeState? {
        val result = get(host, port, "/volume")
        if (result.code !in 200..299 || result.body.isNullOrBlank()) {
            return null
        }
        return SoundTouchXml.parseVolume(result.body)
    }

    suspend fun setBass(host: String, port: Int, targetBass: Int): BassState {
        val clampedBass = targetBass.coerceIn(-9, 9)
        val postResult = post(host, port, "/bass", "<bass>$clampedBass</bass>")
        val postCode = postResult.code
        if (postCode == null || postCode !in 200..299) {
            throw IOException("Bass konnte nicht gesetzt werden (HTTP ${postCode ?: "n/a"})")
        }

        val parsedFromPost = postResult.body?.let(SoundTouchXml::parseBass)
        val refreshed = readBassStateOrNull(host, port)
        return refreshed ?: parsedFromPost ?: BassState(targetBass = clampedBass)
    }

    suspend fun readBassStateOrNull(host: String, port: Int): BassState? {
        val result = get(host, port, "/bass")
        if (result.code !in 200..299 || result.body.isNullOrBlank()) {
            return null
        }
        return SoundTouchXml.parseBass(result.body)
    }

    suspend fun readToneControlsOrNull(host: String, port: Int): ToneControlState? {
        val result = get(host, port, "/audioproducttonecontrols")
        if (result.code !in 200..299 || result.body.isNullOrBlank()) {
            return null
        }
        return SoundTouchXml.parseToneControls(result.body)
    }

    suspend fun setTreble(host: String, port: Int, targetTreble: Int): ToneControlState {
        val current = readToneControlsOrNull(host, port)
        val trebleInfo = current?.treble
        val min = trebleInfo?.minValue ?: -10
        val max = trebleInfo?.maxValue ?: 10
        val clampedTreble = targetTreble.coerceIn(min, max)
        val body = """
            <audioproducttonecontrols>
              <treble value="$clampedTreble" />
            </audioproducttonecontrols>
        """.trimIndent()
        val result = post(host, port, "/audioproducttonecontrols", body)
        val code = result.code
        if (code == null || code !in 200..299) {
            throw IOException("Treble konnte nicht gesetzt werden (HTTP ${code ?: "n/a"}): ${result.responsePreview()}")
        }

        return readToneControlsOrNull(host, port)
            ?: result.body?.let(SoundTouchXml::parseToneControls)
            ?: ToneControlState(treble = AudioControlValue(value = clampedTreble, minValue = min, maxValue = max))
    }

    suspend fun setDeviceName(host: String, port: Int, name: String): DeviceSummary? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            throw IOException("Gerätename fehlt")
        }
        val result = post(host, port, "/name", "<name>${cleanName.xmlEscape()}</name>")
        val code = result.code
        if (code == null || code !in 200..299) {
            throw IOException("Gerätename wurde nicht akzeptiert (HTTP ${code ?: "n/a"}): ${result.responsePreview()}")
        }
        return readDeviceSummaryOrNull(host, port)
    }

    suspend fun readDeviceSummaryOrNull(host: String, port: Int): DeviceSummary? {
        val result = get(host, port, "/info")
        if (result.code !in 200..299 || result.body.isNullOrBlank()) {
            return null
        }
        return SoundTouchXml.parseSummary(host, result.body)
    }

    private suspend fun get(host: String, port: Int, path: String): NetworkResult {
        return requestExecutor.request(host, port, path, "GET", null)
    }

    private suspend fun post(host: String, port: Int, path: String, body: String): NetworkResult {
        return requestExecutor.request(host, port, path, "POST", body)
    }
}

internal class SoundTouchSelectionApi(
    private val requestExecutor: SoundTouchRequestExecutor
) {
    suspend fun selectSource(host: String, port: Int, source: SourceItem) {
        val sourceAccountAttribute = source.sourceAccount
            ?.takeIf { it.isNotBlank() }
            ?.let { " sourceAccount=\"${it.xmlEscape()}\"" }
            .orEmpty()
        val body = """<ContentItem source="${source.source.xmlEscape()}"$sourceAccountAttribute />"""
        postSelect(host, port, body, "Quelle")
    }

    suspend fun selectPreset(host: String, port: Int, preset: PresetItem) {
        val source = preset.source?.takeIf { it.isNotBlank() }
            ?: throw IOException("Preset ${preset.id ?: ""} enthält keine Quelle")
        postContentItem(
            host = host,
            port = port,
            source = source,
            sourceAccount = preset.sourceAccount,
            location = preset.location,
            label = "Preset"
        )
    }

    suspend fun selectRecent(host: String, port: Int, recent: BoseRecentItem) {
        val source = recent.source?.takeIf { it.isNotBlank() }
            ?: throw IOException("Recent ${recent.id ?: ""} enthält keine Quelle")
        postContentItem(
            host = host,
            port = port,
            source = source,
            sourceAccount = recent.sourceAccount,
            location = recent.location,
            label = "Recent"
        )
    }

    private suspend fun postContentItem(
        host: String,
        port: Int,
        source: String,
        sourceAccount: String?,
        location: String?,
        label: String
    ) {
        val sourceAccountAttribute = sourceAccount
            ?.takeIf { it.isNotBlank() }
            ?.let { " sourceAccount=\"${it.xmlEscape()}\"" }
            .orEmpty()
        val locationAttribute = location
            ?.takeIf { it.isNotBlank() }
            ?.let { " location=\"${it.xmlEscape()}\"" }
            .orEmpty()
        val body = """<ContentItem source="${source.xmlEscape()}"$sourceAccountAttribute$locationAttribute />"""
        postSelect(host, port, body, label)
    }

    private suspend fun postSelect(host: String, port: Int, body: String, label: String) {
        val result = requestExecutor.request(host, port, "/select", "POST", body)
        val code = result.code
        if (code == null || code !in 200..299) {
            throw IOException("$label wurde nicht akzeptiert (HTTP ${code ?: "n/a"}): ${result.responsePreview()}")
        }
    }
}

internal class SoundTouchZoneApi(
    private val requestExecutor: SoundTouchRequestExecutor
) {
    suspend fun setZone(
        host: String,
        port: Int,
        masterDeviceId: String,
        masterIpAddress: String,
        members: List<ZoneMember>
    ): ZoneState? {
        postZoneCommand(
            host = host,
            port = port,
            path = "/setZone",
            body = buildZoneBody(
                masterDeviceId = masterDeviceId,
                senderIpAddress = masterIpAddress,
                members = listOf(
                    ZoneMember(deviceId = masterDeviceId, ipAddress = masterIpAddress, role = "master")
                ) + members
            )
        )
        return readZoneStateOrNull(host, port)
    }

    suspend fun addZoneSlave(host: String, port: Int, masterDeviceId: String, slave: ZoneMember): ZoneState? {
        postZoneCommand(
            host = host,
            port = port,
            path = "/addZoneSlave",
            body = buildZoneBody(
                masterDeviceId = masterDeviceId,
                senderIpAddress = null,
                members = listOf(slave)
            )
        )
        return readZoneStateOrNull(host, port)
    }

    suspend fun removeZoneSlave(host: String, port: Int, masterDeviceId: String, slave: ZoneMember): ZoneState? {
        postZoneCommand(
            host = host,
            port = port,
            path = "/removeZoneSlave",
            body = buildZoneBody(
                masterDeviceId = masterDeviceId,
                senderIpAddress = null,
                members = listOf(slave)
            )
        )
        return readZoneStateOrNull(host, port)
    }

    suspend fun readZoneStateOrNull(host: String, port: Int): ZoneState? {
        val result = requestExecutor.request(host, port, "/getZone", "GET", null)
        if (result.code !in 200..299 || result.body.isNullOrBlank()) {
            return null
        }
        return SoundTouchXml.parseZone(result.body)
    }

    private suspend fun postZoneCommand(host: String, port: Int, path: String, body: String) {
        val result = requestExecutor.request(host, port, path, "POST", body)
        val code = result.code
        if (code == null || code !in 200..299) {
            throw IOException("$path wurde nicht akzeptiert (HTTP ${code ?: "n/a"}): ${result.responsePreview()}")
        }
    }

    private fun buildZoneBody(
        masterDeviceId: String,
        senderIpAddress: String?,
        members: List<ZoneMember>
    ): String {
        val senderAttribute = senderIpAddress
            ?.takeIf { it.isNotBlank() }
            ?.let { " senderIPAddress=\"${it.xmlEscape()}\"" }
            .orEmpty()
        return buildString {
            append("<zone master=\"")
            append(masterDeviceId.xmlEscape())
            append("\"")
            append(senderAttribute)
            append(">")
            members
                .filter { !it.deviceId.isNullOrBlank() && !it.ipAddress.isNullOrBlank() }
                .distinctBy { "${it.deviceId}|${it.ipAddress}" }
                .forEach { member ->
                    append("<member ipaddress=\"")
                    append(member.ipAddress.orEmpty().xmlEscape())
                    append("\">")
                    append(member.deviceId.orEmpty().xmlEscape())
                    append("</member>")
                }
            append("</zone>")
        }
    }
}

internal class SoundTouchKeyApi(
    private val requestExecutor: SoundTouchRequestExecutor
) {
    suspend fun sendKey(host: String, port: Int, key: String) {
        val normalizedKey = key.trim().uppercase()
        if (normalizedKey.isBlank()) {
            throw IOException("Key fehlt")
        }
        sendKeyState(host = host, port = port, key = normalizedKey, state = "press")
        sendKeyState(host = host, port = port, key = normalizedKey, state = "release")
    }

    suspend fun playPreset(host: String, port: Int, presetId: Int) {
        sendKey(host = host, port = port, key = "PRESET_${presetId.coerceIn(1, 6)}")
    }

    suspend fun saveCurrentAsPreset(host: String, port: Int, presetId: Int, holdMs: Long) {
        val key = "PRESET_${presetId.coerceIn(1, 6)}"
        sendKeyState(host = host, port = port, key = key, state = "press")
        kotlinx.coroutines.delay(holdMs)
        sendKeyState(host = host, port = port, key = key, state = "release")
    }

    private suspend fun sendKeyState(host: String, port: Int, key: String, state: String) {
        val result = requestExecutor.request(
            host,
            port,
            "/key",
            "POST",
            """<key state="$state" sender="Gabbo">$key</key>"""
        )
        val code = result.code
        if (code == null || code !in 200..299) {
            throw IOException("Key $key/$state wurde nicht akzeptiert (HTTP ${code ?: "n/a"}): ${result.responsePreview()}")
        }
    }
}

private fun NetworkResult.responsePreview(): String {
    return body
        ?.replace("\n", " ")
        ?.take(300)
        .orEmpty()
}
