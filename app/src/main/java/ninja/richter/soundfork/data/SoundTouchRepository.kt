package ninja.richter.soundfork.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

class SoundTouchRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1_500, TimeUnit.MILLISECONDS)
        .readTimeout(2_500, TimeUnit.MILLISECONDS)
        .writeTimeout(2_500, TimeUnit.MILLISECONDS)
        .build()
) {
    companion object {
        private const val TAG = "SoundTouchRepository"
        const val DEFAULT_PORT = 8090
        private const val PRESET_SAVE_HOLD_MS = 2_500L
        private const val SSDP_MULTICAST_IP = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_SOCKET_TIMEOUT_MS = 700
        private val XML_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()
        private val SOAP_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()
        private val AV_TRANSPORT_SEARCH_TARGETS = listOf(
            "urn:schemas-upnp-org:service:AVTransport:1",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "ssdp:all"
        )
        private val LOCATION_HEADER_REGEX = Regex("(?im)^LOCATION:\\s*(.+)$")
        private val URL_BASE_REGEX = Regex("(?is)<URLBase>\\s*([^<]+?)\\s*</URLBase>")
    }

    private val requestExecutor = SoundTouchRequestExecutor { host, port, path, method, body ->
        performRequest(host = host, port = port, path = path, method = method, body = body)
    }
    private val deviceApi = SoundTouchDeviceApi(requestExecutor)
    private val selectionApi = SoundTouchSelectionApi(requestExecutor)
    private val zoneApi = SoundTouchZoneApi(requestExecutor)
    private val keyApi = SoundTouchKeyApi(requestExecutor)

    suspend fun loadSnapshot(
        host: String,
        port: Int = Companion.DEFAULT_PORT
    ): SoundTouchSnapshot = coroutineScope {
        Log.i(TAG, "loadSnapshot(): start host=$host port=$port")
        val start = System.currentTimeMillis()
        return@coroutineScope runCatching {
        val infoDescriptor = EndpointDescriptor(
            title = "Info",
            aliases = listOf("/info")
        )
        val capabilitiesDescriptor = EndpointDescriptor(
            title = "Capabilities",
            aliases = listOf("/capabilities")
        )

        val infoDeferred = async { fetchEndpoint(host, port, infoDescriptor) }
        val capabilitiesDeferred = async { fetchEndpoint(host, port, capabilitiesDescriptor) }

        val infoResult = infoDeferred.await()
        val capabilitiesResult = capabilitiesDeferred.await()
        Log.i(TAG, "loadSnapshot(): initial results info=${infoResult.httpCode} capabilities=${capabilitiesResult.httpCode}")
        val summary = runCatching {
            infoResult.body?.let { SoundTouchXml.parseSummary(host, it) }
        }.onFailure { throwable ->
            Log.w(
                TAG,
                "loadSnapshot(): parseSummary failed host=$host port=$port error=${throwable.message}"
            )
        }.getOrNull()
        Log.d(
            TAG,
            "loadSnapshot(): summary host=${summary?.host} name=${summary?.name} " +
                "type=${summary?.type} deviceId=${summary?.deviceId}"
        )

        val dynamicDescriptors = runCatching {
            capabilitiesResult.body?.let(SoundTouchXml::extractCapabilityUrls)
        }.onFailure { throwable ->
            Log.w(
                TAG,
                "loadSnapshot(): extractCapabilityUrls failed host=$host port=$port error=${throwable.message}"
            )
        }.getOrElse { null }.orEmpty()
            .map { path ->
                EndpointDescriptor(
                    title = "Capability $path",
                    aliases = listOf(path),
                    optional = true
                )
            }

        val staticDescriptors = defaultReadDescriptors()
            .filterNot { descriptor ->
                descriptor.aliases.firstOrNull() in setOf("/info", "/capabilities")
            }

        val descriptors = (staticDescriptors + dynamicDescriptors)
            .distinctBy { it.aliases.firstOrNull().orEmpty().lowercase() }

        val remainingResults = descriptors
            .map { descriptor ->
                Log.d(TAG, "loadSnapshot(): enqueue endpoint=${descriptor.title} aliases=${descriptor.aliases}")
                async {
                    runCatching { fetchEndpoint(host, port, descriptor) }
                        .onFailure { throwable ->
                            Log.w(
                                TAG,
                                "loadSnapshot(): fetchEndpoint failed host=$host port=$port " +
                                    "title=${descriptor.title} error=${throwable.message}"
                            )
                        }.getOrElse {
                            EndpointResult(
                                title = descriptor.title,
                                requestedAliases = descriptor.aliases.ifEmpty { listOf("/") },
                                resolvedPath = descriptor.aliases.firstOrNull(),
                                httpCode = null,
                                body = null,
                                isError = true,
                                errorMessage = it.message ?: "Unerwarteter Fehler beim Laden des Endpunkts"
                            )
                        }
                }
            }
            .awaitAll()

        Log.i(TAG, "loadSnapshot(): complete host=$host endpointResults=${remainingResults.size}")
        Log.d(TAG, "loadSnapshot(): done host=$host durationMs=${System.currentTimeMillis() - start}")
        val endpointResults = listOf(infoResult, capabilitiesResult) + remainingResults
        val volumeState = endpointResults
            .firstOrNull { result ->
                val path = result.resolvedPath.orEmpty().lowercase()
                result.title == "Volume" || path == "/volume"
            }
            ?.body
            ?.let(SoundTouchXml::parseVolume)
        val bassState = endpointResults
            .firstOrNull { result ->
                val path = result.resolvedPath.orEmpty().lowercase()
                result.title == "Bass" || path == "/bass"
            }
            ?.body
            ?.let(SoundTouchXml::parseBass)
        val toneControlState = endpointResults
            .firstOrNull { result ->
                val path = result.resolvedPath.orEmpty().lowercase()
                path == "/audioproducttonecontrols"
            }
            ?.body
            ?.let(SoundTouchXml::parseToneControls)
        val sources = endpointResults
            .firstOrNull { result ->
                val path = result.resolvedPath.orEmpty().lowercase()
                result.title == "Sources" || path == "/sources"
            }
            ?.body
            ?.let(SoundTouchXml::parseSources)
            .orEmpty()
        val nowPlayingResult = endpointResults
            .firstOrNull { result ->
                val path = result.resolvedPath.orEmpty().lowercase()
                result.title == "Now Playing" || path in setOf("/now_playing", "/nowplaying", "/now%20playing")
            }
        val nowPlaying = nowPlayingResult
            ?.body
            ?.let(SoundTouchXml::parseNowPlaying)
        val currentSource = nowPlaying?.source
            ?.takeIf { it.isNotBlank() }
            ?.let { source ->
                SourceItem(
                    source = source,
                    sourceAccount = nowPlaying.sourceAccount
                )
            }
            ?: nowPlayingResult
                ?.body
                ?.let(SoundTouchXml::parseNowPlayingSource)
        val presets = endpointResults
            .firstOrNull { result ->
                val path = result.resolvedPath.orEmpty().lowercase()
                result.title == "Presets" || path == "/presets"
            }
            ?.body
            ?.let(SoundTouchXml::parsePresets)
            .orEmpty()
        val recents = endpointResults
            .firstOrNull { result ->
                val path = result.resolvedPath.orEmpty().lowercase()
                result.title == "Recents" || path == "/recents"
            }
            ?.body
            ?.let(SoundTouchXml::parseRecents)
            .orEmpty()
        val zoneXml = endpointResults
            .firstOrNull { result ->
                val path = result.resolvedPath.orEmpty().lowercase()
                result.title == "Zone" || path == "/getzone"
            }
            ?.body
        val zoneState = zoneXml?.let(SoundTouchXml::parseZone)

        SoundTouchSnapshot(
            summary = summary,
            endpointResults = endpointResults,
            volumeState = volumeState,
            bassState = bassState,
            toneControlState = toneControlState,
            sources = sources,
            currentSource = currentSource,
            nowPlaying = nowPlaying,
            presets = presets,
            recents = recents,
            zoneState = zoneState,
            zoneXml = zoneXml
        )
        }.getOrElse { throwable ->
            Log.w(
                TAG,
                "loadSnapshot(): unhandled failure host=$host port=$port error=${throwable.message}",
                throwable
            )
            throw throwable
        }
    }

    suspend fun probeDevice(host: String, port: Int = Companion.DEFAULT_PORT): DeviceSummary? {
        Log.d(TAG, "probeDevice(): host=$host port=$port")
        val result = fetchEndpoint(
            host = host,
            port = port,
            descriptor = EndpointDescriptor(
                title = "Info",
                aliases = listOf("/info")
            )
        )
        val code = result.httpCode ?: return null
        val body = result.body
        if (code !in 200..299 || body.isNullOrBlank()) {
            return null
        }
        val bodyPreview = body.take(260).replace("\n", "\\n")
        Log.d(
            TAG,
            "probeDevice(): success host=$host code=$code bodyLen=${body.length} preview=$bodyPreview"
        )

        val summary = try {
            SoundTouchXml.parseSummary(host = host, infoXml = body)
        } catch (throwable: Throwable) {
            Log.w(
                TAG,
                "probeDevice(): parseSummary threw exception host=$host port=$port " +
                    "error=${throwable.message}"
            )
            null
        }

        val effectiveSummary = summary ?: run {
            Log.w(TAG, "probeDevice(): parseSummary returned null; falling back to host-only summary host=$host")
            DeviceSummary(host = host)
        }
        Log.d(
            TAG,
            "probeDevice(): parsed summary host=${effectiveSummary.host} " +
                "name=${effectiveSummary.name} type=${effectiveSummary.type} " +
                "deviceId=${effectiveSummary.deviceId}"
        )

        return effectiveSummary
    }

    suspend fun readEndpoint(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        path: String
    ): EndpointResult {
        val normalizedPath = normalizePath(path.trim())
        return fetchEndpoint(
            host = host,
            port = port,
            descriptor = EndpointDescriptor(
                title = "API Test $normalizedPath",
                aliases = listOf(normalizedPath),
                optional = true
            )
        )
    }

    suspend fun setVolume(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        targetVolume: Int,
        muteEnabled: Boolean? = null
    ): VolumeState = deviceApi.setVolume(host, port, targetVolume, muteEnabled)

    suspend fun setMute(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        muteEnabled: Boolean,
        targetVolume: Int? = null
    ): VolumeState = deviceApi.setMute(host, port, muteEnabled, targetVolume)

    suspend fun readVolumeStateOrNull(
        host: String,
        port: Int = Companion.DEFAULT_PORT
    ): VolumeState? = deviceApi.readVolumeStateOrNull(host, port)

    suspend fun setBass(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        targetBass: Int
    ): BassState = deviceApi.setBass(host, port, targetBass)

    suspend fun readBassStateOrNull(
        host: String,
        port: Int = Companion.DEFAULT_PORT
    ): BassState? = deviceApi.readBassStateOrNull(host, port)

    suspend fun readToneControlsOrNull(
        host: String,
        port: Int = Companion.DEFAULT_PORT
    ): ToneControlState? = deviceApi.readToneControlsOrNull(host, port)

    suspend fun setTreble(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        targetTreble: Int
    ): ToneControlState = deviceApi.setTreble(host, port, targetTreble)

    suspend fun readNowPlayingStateOrNull(
        host: String,
        port: Int = Companion.DEFAULT_PORT
    ): NowPlayingState? {
        val result = fetchEndpoint(
            host = host,
            port = port,
            descriptor = EndpointDescriptor(
                title = "Now Playing",
                aliases = listOf("/now_playing", "/nowplaying", "/now%20playing")
            )
        )
        if (result.httpCode !in 200..299 || result.body.isNullOrBlank()) {
            return null
        }
        return SoundTouchXml.parseNowPlaying(result.body)
    }

    suspend fun setDeviceName(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        name: String
    ): DeviceSummary? = deviceApi.setDeviceName(host, port, name)

    suspend fun readDeviceSummaryOrNull(
        host: String,
        port: Int = Companion.DEFAULT_PORT
    ): DeviceSummary? = deviceApi.readDeviceSummaryOrNull(host, port)

    suspend fun selectSource(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        source: SourceItem
    ) = selectionApi.selectSource(host, port, source)

    suspend fun selectPreset(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        preset: PresetItem
    ) = selectionApi.selectPreset(host, port, preset)

    suspend fun selectRecent(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        recent: BoseRecentItem
    ) = selectionApi.selectRecent(host, port, recent)

    suspend fun setZone(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        masterDeviceId: String,
        masterIpAddress: String,
        members: List<ZoneMember>
    ): ZoneState? = zoneApi.setZone(host, port, masterDeviceId, masterIpAddress, members)

    suspend fun addZoneSlave(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        masterDeviceId: String,
        slave: ZoneMember
    ): ZoneState? = zoneApi.addZoneSlave(host, port, masterDeviceId, slave)

    suspend fun removeZoneSlave(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        masterDeviceId: String,
        slave: ZoneMember
    ): ZoneState? = zoneApi.removeZoneSlave(host, port, masterDeviceId, slave)

    suspend fun readZoneStateOrNull(
        host: String,
        port: Int = Companion.DEFAULT_PORT
    ): ZoneState? = zoneApi.readZoneStateOrNull(host, port)

    suspend fun sendKey(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        key: String
    ) = keyApi.sendKey(host, port, key)

    suspend fun playPreset(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        presetId: Int
    ) = keyApi.playPreset(host, port, presetId)

    suspend fun saveCurrentAsPreset(
        host: String,
        port: Int = Companion.DEFAULT_PORT,
        presetId: Int
    ) = keyApi.saveCurrentAsPreset(host, port, presetId, PRESET_SAVE_HOLD_MS)

    suspend fun playDlnaStream(
        host: String,
        streamUrl: String,
        controlPort: Int = 8091
    ) {
        val normalizedUrl = streamUrl.trim()
        if (normalizedUrl.isBlank()) {
            throw IOException("Stream-URL fehlt")
        }

        val playableUrl = resolvePlayableStreamUrl(normalizedUrl)
        Log.i(TAG, "playDlnaStream(): host=$host controlPort=$controlPort url=$normalizedUrl playable=$playableUrl")
        val avTransportEndpoint = resolveAvTransportEndpoint(host, controlPort)
        Log.i(
            TAG,
            "playDlnaStream(): resolved AVTransport host=${avTransportEndpoint.host} " +
                "port=${avTransportEndpoint.port} path=${avTransportEndpoint.path}"
        )
        val setUriResult = performSetAvTransportUri(
            endpoint = avTransportEndpoint,
            streamUrl = playableUrl
        )
        val setUriCode = setUriResult.result.code
        if (setUriCode == null || setUriCode !in 200..299) {
            val responsePreview = setUriResult.result.body
                ?.replace("\n", " ")
                ?.take(400)
                .orEmpty()
            Log.w(
                TAG,
                "playDlnaStream(): SetAVTransportURI rejected code=$setUriCode " +
                    "endpoint=$avTransportEndpoint variant=${setUriResult.variant} response=$responsePreview"
            )
            throw IOException(
                "DLNA Stream-URL wurde nicht akzeptiert (HTTP ${setUriCode ?: "n/a"}): $responsePreview"
            )
        }

        val playResult = performSoapRequest(
            host = avTransportEndpoint.host,
            port = avTransportEndpoint.port,
            path = avTransportEndpoint.path,
            soapAction = "urn:schemas-upnp-org:service:AVTransport:1#Play",
            body = buildPlayBody()
        )
        val playCode = playResult.code
        if (playCode == null || playCode !in 200..299) {
            val responsePreview = playResult.body
                ?.replace("\n", " ")
                ?.take(400)
                .orEmpty()
            Log.w(
                TAG,
                "playDlnaStream(): Play rejected code=$playCode endpoint=$avTransportEndpoint response=$responsePreview"
            )
            throw IOException("DLNA Play konnte nicht gestartet werden (HTTP ${playCode ?: "n/a"}): $responsePreview")
        }
    }

    private suspend fun resolvePlayableStreamUrl(streamUrl: String): String {
        val trimmedUrl = streamUrl.trim()
        if (!trimmedUrl.looksLikePlaylistUrl()) {
            return trimmedUrl
        }

        val result = performAbsoluteGet(trimmedUrl)
        val body = result.body
        if (result.code !in 200..299 || body.isNullOrBlank()) {
            Log.w(
                TAG,
                "resolvePlayableStreamUrl(): playlist fetch failed url=$trimmedUrl code=${result.code}"
            )
            return trimmedUrl
        }

        val resolved = when {
            trimmedUrl.endsWith(".pls", ignoreCase = true) -> parsePlsStreamUrl(body)
            else -> parseM3uStreamUrl(body)
        }
        Log.i(TAG, "resolvePlayableStreamUrl(): url=$trimmedUrl resolved=${resolved ?: "-"}")
        return resolved ?: trimmedUrl
    }

    private fun String.looksLikePlaylistUrl(): Boolean {
        val lower = lowercase()
        return lower.endsWith(".m3u") ||
            lower.endsWith(".m3u8") ||
            lower.endsWith(".pls") ||
            lower.contains(".m3u?") ||
            lower.contains(".m3u8?") ||
            lower.contains(".pls?")
    }

    private fun parseM3uStreamUrl(playlistBody: String): String? {
        return playlistBody
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                line.startsWith("http://", ignoreCase = true) ||
                    line.startsWith("https://", ignoreCase = true)
            }
    }

    private fun parsePlsStreamUrl(playlistBody: String): String? {
        return playlistBody
            .lineSequence()
            .map { it.trim() }
            .firstNotNullOfOrNull { line ->
                line.substringAfter("=", missingDelimiterValue = "")
                    .takeIf {
                        line.startsWith("File", ignoreCase = true) &&
                            (it.startsWith("http://", ignoreCase = true) ||
                                it.startsWith("https://", ignoreCase = true))
                    }
            }
    }

    suspend fun sendDlnaTransportAction(
        host: String,
        action: String,
        controlPort: Int = 8091
    ) {
        val soapActionName = when (action.trim().uppercase()) {
            "PLAY" -> "Play"
            "PAUSE" -> "Pause"
            "STOP" -> "Stop"
            else -> throw IOException("DLNA Aktion wird nicht unterstuetzt: $action")
        }

        Log.i(TAG, "sendDlnaTransportAction(): host=$host controlPort=$controlPort action=$soapActionName")
        val avTransportEndpoint = resolveAvTransportEndpoint(host, controlPort)
        val result = performSoapRequest(
            host = avTransportEndpoint.host,
            port = avTransportEndpoint.port,
            path = avTransportEndpoint.path,
            soapAction = "urn:schemas-upnp-org:service:AVTransport:1#$soapActionName",
            body = buildAvTransportActionBody(soapActionName)
        )
        val code = result.code
        if (code == null || code !in 200..299) {
            val responsePreview = result.body
                ?.replace("\n", " ")
                ?.take(400)
                .orEmpty()
            Log.w(
                TAG,
                "sendDlnaTransportAction(): rejected code=$code endpoint=$avTransportEndpoint " +
                    "action=$soapActionName response=$responsePreview"
            )
            throw IOException(
                "DLNA $soapActionName wurde nicht akzeptiert (HTTP ${code ?: "n/a"}): $responsePreview"
            )
        }
    }

    private suspend fun performSetAvTransportUri(
        endpoint: UpnpControlEndpoint,
        streamUrl: String
    ): SoapAttemptResult {
        val streamUrls = buildList {
            add(streamUrl)
            if (streamUrl.startsWith("https://", ignoreCase = true)) {
                add("http://" + streamUrl.removePrefix("https://"))
            }
        }.distinct()

        val attempts = streamUrls.flatMap { candidateUrl ->
            listOf(
                SoapBodyVariant(
                    name = "CurrentURI compact url=$candidateUrl",
                    body = buildSetAvTransportUriBody(
                        streamUrl = candidateUrl,
                        metadata = "",
                        useCdata = false,
                        useNewUriNames = false
                    )
                ),
                SoapBodyVariant(
                    name = "CurrentURI CDATA url=$candidateUrl",
                    body = buildSetAvTransportUriBody(
                        streamUrl = candidateUrl,
                        metadata = "",
                        useCdata = true,
                        useNewUriNames = false
                    )
                ),
                SoapBodyVariant(
                    name = "CurrentURI DIDL audio/mpeg url=$candidateUrl",
                    body = buildSetAvTransportUriBody(
                        streamUrl = candidateUrl,
                        metadata = buildDidlLiteMetadata(candidateUrl),
                        useCdata = false,
                        useNewUriNames = false
                    )
                ),
                SoapBodyVariant(
                    name = "NewURI compact url=$candidateUrl",
                    body = buildSetAvTransportUriBody(
                        streamUrl = candidateUrl,
                        metadata = "",
                        useCdata = false,
                        useNewUriNames = true
                    )
                )
            )
        }

        var lastResult: SoapAttemptResult? = null
        for (attempt in attempts) {
            Log.d(TAG, "performSetAvTransportUri(): variant=${attempt.name} body=${attempt.body}")
            val result = performSoapRequest(
                host = endpoint.host,
                port = endpoint.port,
                path = endpoint.path,
                soapAction = "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI",
                body = attempt.body
            )
            val attemptResult = SoapAttemptResult(
                variant = attempt.name,
                result = result
            )
            if (result.code in 200..299) {
                return attemptResult
            }
            lastResult = attemptResult
        }

        return lastResult ?: SoapAttemptResult(
            variant = "none",
            result = NetworkResult(
                code = null,
                body = null,
                exception = IOException("SetAVTransportURI wurde nicht ausgefuehrt")
            )
        )
    }

    private suspend fun resolveAvTransportEndpoint(
        host: String,
        fallbackPort: Int
    ): UpnpControlEndpoint {
        val discoveredDescriptions = discoverUpnpDescriptionUrls(host)
        val fallbackDescriptions = listOf(
            "http://${normalizeHost(host)}:$fallbackPort/rootDesc.xml",
            "http://${normalizeHost(host)}:$fallbackPort/description.xml",
            "http://${normalizeHost(host)}:$fallbackPort/device.xml"
        )
        val candidateDescriptions = (discoveredDescriptions + fallbackDescriptions).distinct()

        for (descriptionUrl in candidateDescriptions) {
            val result = performAbsoluteGet(descriptionUrl)
            val body = result.body
            if (result.code !in 200..299 || body.isNullOrBlank()) {
                Log.d(
                    TAG,
                    "resolveAvTransportEndpoint(): description miss url=$descriptionUrl code=${result.code}"
                )
                continue
            }

            Log.i(
                TAG,
                "resolveAvTransportEndpoint(): description hit url=$descriptionUrl code=${result.code} " +
                    "bodyPreview=${body.take(500).replace("\n", " ")}"
            )

            val controlUrl = extractAvTransportControlUrl(body)
            if (controlUrl.isNullOrBlank()) {
                Log.w(TAG, "resolveAvTransportEndpoint(): AVTransport controlURL not found url=$descriptionUrl")
                continue
            }

            Log.i(TAG, "resolveAvTransportEndpoint(): AVTransport controlURL=$controlUrl")
            return resolveControlEndpoint(
                descriptionUrl = descriptionUrl,
                descriptionXml = body,
                controlUrl = controlUrl
            )
        }

        Log.w(TAG, "resolveAvTransportEndpoint(): falling back to guessed AVTransport control URL")
        return UpnpControlEndpoint(
            host = host,
            port = fallbackPort,
            path = "/MediaRenderer/AVTransport/Control"
        )
    }

    private suspend fun discoverUpnpDescriptionUrls(targetHost: String): List<String> = withContext(Dispatchers.IO) {
        val urls = linkedSetOf<String>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = SSDP_SOCKET_TIMEOUT_MS
                socket.broadcast = true
                val destination = InetAddress.getByName(SSDP_MULTICAST_IP)

                AV_TRANSPORT_SEARCH_TARGETS.forEach { searchTarget ->
                    val request = buildSsdpSearchRequest(searchTarget)
                    val packet = DatagramPacket(
                        request,
                        request.size,
                        destination,
                        SSDP_PORT
                    )
                    socket.send(packet)
                    Log.d(TAG, "discoverUpnpDescriptionUrls(): SSDP sent target=$searchTarget")
                }

                val deadline = System.currentTimeMillis() + 2_500
                val buffer = ByteArray(4 * 1024)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                        val sourceHost = packet.address?.hostAddress?.substringBefore('%').orEmpty()
                        val payload = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val location = LOCATION_HEADER_REGEX.find(payload)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.trim()

                        if (location.isNullOrBlank()) {
                            continue
                        }

                        val locationHost = runCatching { URI(location).host?.substringBefore('%') }
                            .getOrNull()
                            .orEmpty()
                        val hostMatches = sourceHost == targetHost || locationHost == targetHost
                        Log.d(
                            TAG,
                            "discoverUpnpDescriptionUrls(): response source=$sourceHost " +
                                "locationHost=$locationHost match=$hostMatches location=$location"
                        )
                        if (hostMatches) {
                            urls += location
                        }
                    } catch (_: SocketTimeoutException) {
                        // Keep listening until the discovery deadline has elapsed.
                    }
                }
            }
        }.onFailure { throwable ->
            Log.w(TAG, "discoverUpnpDescriptionUrls(): failed targetHost=$targetHost error=${throwable.message}")
        }

        Log.i(TAG, "discoverUpnpDescriptionUrls(): host=$targetHost urls=$urls")
        urls.toList()
    }

    private fun buildSsdpSearchRequest(searchTarget: String): ByteArray {
        val payload = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_MULTICAST_IP:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: $searchTarget\r\n")
            append("\r\n")
        }
        return payload.toByteArray(Charsets.UTF_8)
    }

    private fun resolveControlEndpoint(
        descriptionUrl: String,
        descriptionXml: String,
        controlUrl: String
    ): UpnpControlEndpoint {
        val urlBase = URL_BASE_REGEX.find(descriptionXml)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val baseUri = URI(urlBase ?: descriptionUrl)
        val resolvedUri = baseUri.resolve(controlUrl.trim())
        val resolvedHost = resolvedUri.host ?: URI(descriptionUrl).host
        val resolvedPort = if (resolvedUri.port > 0) {
            resolvedUri.port
        } else {
            when (resolvedUri.scheme?.lowercase()) {
                "https" -> 443
                else -> 80
            }
        }
        val resolvedPath = buildString {
            append(resolvedUri.rawPath?.takeIf { it.isNotBlank() } ?: "/")
            if (!resolvedUri.rawQuery.isNullOrBlank()) {
                append("?")
                append(resolvedUri.rawQuery)
            }
        }

        return UpnpControlEndpoint(
            host = resolvedHost,
            port = resolvedPort,
            path = resolvedPath
        )
    }

    private fun extractAvTransportControlUrl(descriptionXml: String): String? {
        val serviceRegex = Regex("(?is)<service>(.*?)</service>")
        val serviceTypeRegex = Regex("(?is)<serviceType>\\s*([^<]+?)\\s*</serviceType>")
        val controlUrlRegex = Regex("(?is)<controlURL>\\s*([^<]+?)\\s*</controlURL>")

        return serviceRegex.findAll(descriptionXml)
            .map { it.groupValues[1] }
            .firstNotNullOfOrNull { serviceXml ->
                val serviceType = serviceTypeRegex.find(serviceXml)?.groupValues?.getOrNull(1)
                val isAvTransport = serviceType
                    ?.contains("urn:schemas-upnp-org:service:AVTransport:1", ignoreCase = true) == true
                if (isAvTransport) {
                    controlUrlRegex.find(serviceXml)?.groupValues?.getOrNull(1)?.trim()
                } else {
                    null
                }
            }
    }

    private suspend fun fetchEndpoint(
        host: String,
        port: Int,
        descriptor: EndpointDescriptor
    ): EndpointResult {
        Log.d(TAG, "fetchEndpoint(): start title=${descriptor.title} host=$host aliases=${descriptor.aliases}")
        val aliases = descriptor.aliases.ifEmpty { listOf("/") }
        var lastFailure: EndpointResult? = null

        for ((index, alias) in aliases.withIndex()) {
            val result = performRequest(host = host, port = port, path = alias)
            val code = result.code
            val body = result.body

            if (result.exception != null) {
                lastFailure = EndpointResult(
                    title = descriptor.title,
                    requestedAliases = aliases,
                    resolvedPath = alias,
                    httpCode = null,
                    body = null,
                    isError = true,
                    errorMessage = result.exception.message ?: "Unbekannter Netzwerkfehler"
                )
                continue
            }

            val prettyBody = body?.let(SoundTouchXml::prettyOrRaw)
            val isSuccess = code in 200..299
            val unsupported = descriptor.optional && code == 404
            val shouldTryNextAlias = !isSuccess && code == 404 && index < aliases.lastIndex
            Log.d(
                TAG,
                "fetchEndpoint(): result title=${descriptor.title} alias=$alias code=$code isSuccess=$isSuccess unsupported=$unsupported"
            )

            val endpointResult = EndpointResult(
                title = descriptor.title,
                requestedAliases = aliases,
                resolvedPath = alias,
                httpCode = code,
                body = prettyBody,
                isError = !isSuccess && !unsupported,
                errorMessage = when {
                    isSuccess -> null
                    unsupported -> "Endpoint auf diesem Modell nicht verfügbar"
                    else -> "HTTP $code"
                }
            )

            if (isSuccess || unsupported || !shouldTryNextAlias) {
                return endpointResult
            }
            lastFailure = endpointResult
        }

        return lastFailure ?: EndpointResult(
            title = descriptor.title,
            requestedAliases = aliases,
            resolvedPath = aliases.firstOrNull(),
            httpCode = null,
            body = null,
            isError = true,
            errorMessage = "Endpoint konnte nicht abgefragt werden"
        )
    }

    private suspend fun performRequest(
        host: String,
        port: Int,
        path: String,
        method: String = "GET",
        body: String? = null
    ): NetworkResult {
        val normalizedPath = normalizePath(path)
        Log.d(
            TAG,
            "performRequest(): method=$method rawHost=$host port=$port path=$path normalizedPath=$normalizedPath"
        )
        return withContext(Dispatchers.IO) {
            val url = "http://${normalizeHost(host)}:$port$normalizedPath"
            Log.d(TAG, "performRequest(): $method $url")

            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "text/xml")

            val request = when (method.uppercase()) {
                "POST" -> {
                    val requestBody = (body ?: "").toRequestBody(XML_MEDIA_TYPE)
                    requestBuilder.post(requestBody).build()
                }
                else -> requestBuilder.get().build()
            }

            try {
                client.newCall(request).execute().use { response ->
                    Log.v(TAG, "performRequest(): response code=${response.code} url=$url method=$method")
                    NetworkResult(
                        code = response.code,
                        body = response.body?.string(),
                        exception = null
                    )
                }
            } catch (exception: IOException) {
                Log.w(TAG, "performRequest(): failed url=$url error=${exception.message}")
                NetworkResult(
                    code = null,
                    body = null,
                    exception = exception
                )
            }
        }
    }

    private suspend fun performSoapRequest(
        host: String,
        port: Int,
        path: String,
        soapAction: String,
        body: String
    ): NetworkResult {
        val normalizedPath = normalizePath(path)
        return withContext(Dispatchers.IO) {
            val url = "http://${normalizeHost(host)}:$port$normalizedPath"
            Log.d(TAG, "performSoapRequest(): POST $url action=$soapAction")
            val request = Request.Builder()
                .url(url)
                .header("SOAPACTION", "\"$soapAction\"")
                .header("Content-Type", "text/xml; charset=\"utf-8\"")
                .post(body.toRequestBody(SOAP_MEDIA_TYPE))
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    Log.v(
                        TAG,
                        "performSoapRequest(): response code=${response.code} url=$url action=$soapAction"
                    )
                    NetworkResult(
                        code = response.code,
                        body = response.body?.string(),
                        exception = null
                    )
                }
            } catch (exception: IOException) {
                Log.w(TAG, "performSoapRequest(): failed url=$url error=${exception.message}")
                NetworkResult(
                    code = null,
                    body = null,
                    exception = exception
                )
            }
        }
    }

    private suspend fun performAbsoluteGet(url: String): NetworkResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "performAbsoluteGet(): GET $url")
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/xml")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    Log.v(TAG, "performAbsoluteGet(): response code=${response.code} url=$url")
                    NetworkResult(
                        code = response.code,
                        body = response.body?.string(),
                        exception = null
                    )
                }
            } catch (exception: IOException) {
                Log.w(TAG, "performAbsoluteGet(): failed url=$url error=${exception.message}")
                NetworkResult(
                    code = null,
                    body = null,
                    exception = exception
                )
            }
        }
    }

    private fun buildSetAvTransportUriBody(
        streamUrl: String,
        metadata: String,
        useCdata: Boolean,
        useNewUriNames: Boolean
    ): String {
        val uriValue = if (useCdata) {
            "<![CDATA[$streamUrl]]>"
        } else {
            streamUrl.xmlEscape()
        }
        val uriTag = if (useNewUriNames) "NewURI" else "CurrentURI"
        val metaDataTag = if (useNewUriNames) "NewURIMetaData" else "CurrentURIMetaData"
        val metadataValue = metadata.xmlEscape()
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <$uriTag>$uriValue</$uriTag>
                  <$metaDataTag>$metadataValue</$metaDataTag>
                </u:SetAVTransportURI>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    private fun buildDidlLiteMetadata(streamUrl: String): String {
        val escapedUrl = streamUrl.xmlEscape()
        return """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="soundfork-stream" parentID="0" restricted="1">
                <dc:title>SoundFork Stream</dc:title>
                <upnp:class>object.item.audioItem.audioBroadcast</upnp:class>
                <res protocolInfo="http-get:*:audio/mpeg:*">$escapedUrl</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
    }

    private fun buildPlayBody(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <Speed>1</Speed>
                </u:Play>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    private fun buildAvTransportActionBody(actionName: String): String {
        val speedElement = if (actionName == "Play") {
            "\n                  <Speed>1</Speed>"
        } else {
            ""
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:$actionName xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>$speedElement
                </u:$actionName>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    private data class UpnpControlEndpoint(
        val host: String,
        val port: Int,
        val path: String
    )

    private data class SoapBodyVariant(
        val name: String,
        val body: String
    )

    private data class SoapAttemptResult(
        val variant: String,
        val result: NetworkResult
    )

}
