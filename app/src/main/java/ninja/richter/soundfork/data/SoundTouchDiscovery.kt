package ninja.richter.soundfork.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class SoundTouchDiscovery(
    private val context: Context,
    private val repository: SoundTouchRepository
) {

    suspend fun discover(timeoutMs: Long = 7_000): List<DiscoveredSpeaker> = coroutineScope {
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "discover(): start timeoutMs=$timeoutMs")

        val mdnsDeferred = async { discoverViaMdns(timeoutMs) }
        val ssdpDeferred = async { discoverViaSsdp(timeoutMs) }

        val byHostMethods = mutableMapOf<String, MutableSet<DiscoveryMethod>>()
        val hostPorts = mutableMapOf<String, Int>()

        val mdnsHosts = runCatching { mdnsDeferred.await() }.getOrElse {
            Log.w(TAG, "discover(): discoverViaMdns() threw error: ${it.message}")
            emptyMap()
        }
        mdnsHosts.forEach { (host, port) ->
            Log.d(TAG, "discover(): mDNS host=$host port=$port")
            hostPorts[host] = port
            val methods = byHostMethods.getOrPut(host) { linkedSetOf() }
            methods += DiscoveryMethod.MDNS
        }

        val ssdpHosts = runCatching { ssdpDeferred.await() }.getOrElse {
            Log.w(TAG, "discover(): discoverViaSsdp() threw error: ${it.message}")
            emptyMap()
        }
        ssdpHosts.forEach { (host, port) ->
            Log.d(TAG, "discover(): SSDP host=$host port=$port")
            hostPorts.putIfAbsent(host, port)
            val methods = byHostMethods.getOrPut(host) { linkedSetOf() }
            methods += DiscoveryMethod.SSDP
        }

        if (byHostMethods.isEmpty()) {
            Log.w(TAG, "discover(): no hosts discovered")
            return@coroutineScope emptyList()
        }

        Log.i(
            TAG,
            "discover(): combining results mDNS=${mdnsHosts.size} SSDP=${ssdpHosts.size} unique=${byHostMethods.size}"
        )

        byHostMethods.entries
            .map { (host, methods) ->
                async {
                    val port = hostPorts[host] ?: SoundTouchRepository.DEFAULT_PORT
                    Log.d(TAG, "discover(): probing host=$host port=$port methods=$methods")
                    val summary = runCatching {
                        repository.probeDevice(host, port)
                    }.onFailure { throwable ->
                        Log.w(TAG, "discover(): probe failed host=$host port=$port error=${throwable.message}")
                    }.getOrNull() ?: run {
                        Log.w(TAG, "discover(): probe fallback host-only for host=$host")
                        DeviceSummary(host = host, port = port)
                    }

                    Log.i(
                        TAG,
                        "discover(): probe success host=$host name=${summary.name} type=${summary.type} deviceId=${summary.deviceId}"
                    )
                    DiscoveredSpeaker(
                        host = host,
                        port = port,
                        name = summary.name,
                        type = summary.type,
                        deviceId = summary.deviceId,
                        methods = methods
                    )
                }
            }
            .awaitAll()
            .filterNotNull()
            .sortedWith(compareBy({ it.name ?: "" }, { it.host }))
            .also {
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                Log.i(TAG, "discover(): complete found=${it.size} elapsedMs=$elapsed result=$it")
            }
    }

    private suspend fun discoverViaMdns(timeoutMs: Long): Map<String, Int> = withContext(Dispatchers.Main) {
        val nsdManager = context.getSystemService(NsdManager::class.java)
        if (nsdManager == null) {
            Log.w(TAG, "discoverViaMdns(): NsdManager unavailable")
            return@withContext emptyMap()
        }

        val hosts = ConcurrentHashMap<String, Int>()
        var serviceTypeIndex = 0

        val perTypeTimeoutMs = max(timeoutMs / MdnsServiceType.values().size, 1_000L)
        for (serviceType in MdnsServiceType.values()) {
            serviceTypeIndex++
            val discovered = discoverViaMdnsServiceType(
                nsdManager = nsdManager,
                serviceType = serviceType.raw,
                timeoutMs = perTypeTimeoutMs
            )
            discovered.forEach { (host, port) ->
                hosts[host] = port
            }
            Log.i(
                TAG,
                "discoverViaMdns(): scan#$serviceTypeIndex type=${serviceType.raw} found=${discovered.size}"
            )
        }

        if (hosts.isEmpty()) {
            Log.w(TAG, "discoverViaMdns(): no hosts")
            emptyMap()
        } else {
            hosts.forEach { (host, port) ->
                Log.i(TAG, "discoverViaMdns(): host=$host port=$port")
            }
            hosts.toMap()
        }
    }

    private suspend fun discoverViaMdnsServiceType(
        nsdManager: NsdManager,
        serviceType: String,
        timeoutMs: Long
    ): Map<String, Int> = withContext(Dispatchers.Main) {
        val hosts = ConcurrentHashMap<String, Int>()
        val normalizedDiscoveryType = normalizeServiceType(serviceType)
        Log.d(TAG, "discoverViaMdnsServiceType(): start type=$normalizedDiscoveryType timeoutMs=$timeoutMs")

        var serviceFoundCount = 0
        var candidateCount = 0
        var ignoredCount = 0
        var resolveCount = 0

        val startTime = SystemClock.elapsedRealtime()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(
                    TAG,
                    "mDNS onStartDiscoveryFailed requested=${normalizedDiscoveryType} actual=$serviceType error=$errorCode"
                )
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(
                    TAG,
                    "mDNS onStopDiscoveryFailed requested=${normalizedDiscoveryType} actual=$serviceType error=$errorCode"
                )
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "mDNS onDiscoveryStarted type=$serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.d(TAG, "mDNS onDiscoveryStopped type=$serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                serviceFoundCount++
                val rawServiceType = serviceInfo.serviceType.orEmpty()
                val normalizedType = normalizeServiceType(rawServiceType)
                Log.d(
                    TAG,
                    "mDNS onServiceFound rawType=$rawServiceType normalizedType=$normalizedType " +
                        "name=${serviceInfo.serviceName} requestedType=$normalizedDiscoveryType hostHint=${serviceInfo.host}"
                )

                val candidateByServiceType = isSoundTouchServiceType(normalizedType)
                val candidateByServiceName = serviceInfo.serviceName?.contains(
                    "soundtouch",
                    ignoreCase = true
                ) == true && (serviceInfo.serviceType?.isBlank() == true)

                if (!candidateByServiceType && !candidateByServiceName) {
                    ignoredCount++
                    Log.d(TAG, "mDNS rejected service type=$normalizedType name=${serviceInfo.serviceName}")
                    return
                }

                candidateCount++
                val servicePort = serviceInfo.port.takeIf { it > 0 } ?: SoundTouchRepository.DEFAULT_PORT
                Log.i(
                    TAG,
                    "mDNS candidate service name=${serviceInfo.serviceName} type=$normalizedType " +
                        "requestedType=$normalizedDiscoveryType serviceTypeMatch=$candidateByServiceType " +
                        "serviceNameMatch=$candidateByServiceName port=$servicePort"
                )

                runCatching {
                    val immediateHost = serviceInfo.host?.hostAddress?.sanitizeHost().orEmpty()
                    if (immediateHost.isNotBlank()) {
                        Log.d(TAG, "mDNS immediate host available host=$immediateHost port=$servicePort")
                        hosts[immediateHost] = servicePort
                    } else {
                        Log.d(TAG, "mDNS immediate host empty for service=${serviceInfo.serviceName}")
                    }

                    val immediateHostName = serviceInfo.host?.hostName
                        ?.trim()
                        ?.trimEnd('.')
                        ?.removeSuffix(".local")
                        ?.trimEnd('.')
                        ?.sanitizeHost()
                    immediateHostName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { hostName ->
                            Log.d(
                                TAG,
                                "mDNS immediate hostname fallback name=$hostName port=$servicePort"
                            )
                            hosts[hostName] = servicePort
                        }

                    nsdManager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                Log.w(
                                    TAG,
                                    "mDNS resolve failed requested=${normalizedDiscoveryType} " +
                                        "name=${serviceInfo?.serviceName} error=$errorCode"
                                )
                            }

                            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                val host = serviceInfo.host?.hostAddress?.sanitizeHost().orEmpty()
                                val resolvedPort = serviceInfo.port.takeIf { it > 0 } ?: servicePort
                                if (host.isNotBlank()) {
                                    resolveCount++
                                    Log.i(
                                        TAG,
                                        "mDNS resolved host=$host port=$resolvedPort name=${serviceInfo.serviceName} requested=$normalizedDiscoveryType"
                                    )
                                    hosts[host] = resolvedPort
                                } else {
                                    val resolvedHostName = serviceInfo.host?.hostName
                                        ?.trim()
                                        ?.trimEnd('.')
                                        ?.removeSuffix(".local")
                                        ?.trimEnd('.')
                                        ?.sanitizeHost()
                                    resolvedHostName
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { hostName ->
                                            resolveCount++
                                            Log.i(
                                                TAG,
                                                "mDNS resolved hostname fallback name=$hostName port=$resolvedPort name=${serviceInfo.serviceName} requested=$normalizedDiscoveryType"
                                            )
                                            hosts[hostName] = resolvedPort
                                        } ?: run {
                                        Log.w(
                                            TAG,
                                            "mDNS resolved empty host for=${serviceInfo.serviceName} requested=$normalizedDiscoveryType"
                                        )
                                    }
                                }
                            }
                        }
                    )
                }.onFailure { throwable ->
                    Log.w(
                        TAG,
                        "mDNS resolve dispatch failed requested=${normalizedDiscoveryType} name=${serviceInfo.serviceName} error=${throwable.message}"
                    )
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "mDNS service lost name=${serviceInfo.serviceName}")
            }
        }

        runCatching {
            nsdManager.discoverServices(
                serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
            Log.i(TAG, "mDNS discoverServices called for=$serviceType")
        }.onFailure { throwable ->
            Log.w(TAG, "mDNS discoverServices failed requested=$normalizedDiscoveryType error=${throwable.message}")
        }

        delay(max(timeoutMs, 2_000))

        val elapsed = SystemClock.elapsedRealtime() - startTime
        Log.i(
            TAG,
            "mDNS scan finished requested=$normalizedDiscoveryType serviceFound=$serviceFoundCount " +
                "candidate=$candidateCount ignored=$ignoredCount resolved=$resolveCount " +
                "hostCount=${hosts.size} elapsedMs=$elapsed"
        )

        runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            .onSuccess { Log.d(TAG, "mDNS stopServiceDiscovery success requested=$normalizedDiscoveryType") }
            .onFailure { throwable ->
                Log.w(
                    TAG,
                    "mDNS stopServiceDiscovery failed requested=$normalizedDiscoveryType error=${throwable.message}"
                )
            }

        if (hosts.isEmpty()) {
            emptyMap()
        } else {
            hosts.toMap()
        }
    }

    private suspend fun discoverViaSsdp(timeoutMs: Long): Map<String, Int> = withContext(Dispatchers.IO) {
        val hosts = ConcurrentHashMap<String, Int>()
        Log.d(TAG, "discoverViaSsdp(): start timeoutMs=$timeoutMs")

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager == null) {
            Log.w(TAG, "discoverViaSsdp(): WifiManager unavailable")
        }

        val multicastLock = wifiManager
            ?.createMulticastLock("soundfork:ssdp")
            ?.apply {
                setReferenceCounted(false)
                runCatching {
                    acquire()
                    Log.d(TAG, "SSDP multicast lock acquired")
                }.onFailure { throwable ->
                    Log.w(TAG, "SSDP multicast lock acquire failed: ${throwable.message}")
                }
            }
        if (multicastLock == null) {
            Log.w(TAG, "discoverViaSsdp(): Multicast lock unavailable, continuing anyway")
        }

        var responseCount = 0
        var parseCount = 0
        var sourceCount = 0
        val startTime = SystemClock.elapsedRealtime()

        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = SSDP_SOCKET_TIMEOUT_MS
                Log.d(TAG, "SSDP socket timeoutMs=$SSDP_SOCKET_TIMEOUT_MS")

                val destination = InetAddress.getByName(SSDP_MULTICAST_IP)
                SSDP_SEARCH_TARGETS.forEach { target ->
                    Log.d(TAG, "SSDP query target=$target")
                    val request = buildSsdpSearchRequest(target)
                    val requestPacket = DatagramPacket(
                        request,
                        request.size,
                        destination,
                        SSDP_PORT
                    )
                    repeat(2) { attempt ->
                        socket.send(requestPacket)
                        Log.v(TAG, "SSDP send target=$target attempt=${attempt + 1} bytes=${request.size}")
                    }
                }

                val deadline = System.currentTimeMillis() + max(timeoutMs, 2_000)
                val buffer = ByteArray(4 * 1024)
                while (System.currentTimeMillis() < deadline) {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(responsePacket)
                        responseCount++

                        val sourceHost = responsePacket.address?.hostAddress?.sanitizeHost().orEmpty()
                        Log.v(TAG, "SSDP packet source=$sourceHost len=${responsePacket.length} total=$responseCount")

                        val payload = String(responsePacket.data, 0, responsePacket.length, Charsets.UTF_8)
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            val shortPayload = if (payload.length > 260) payload.take(260) else payload
                            Log.v(TAG, "SSDP payload=$shortPayload")
                        }

                        parseLocationHost(payload)?.let { endpoint ->
                            parseCount++
                            Log.d(TAG, "SSDP parsed endpoint host=${endpoint.host} port=${endpoint.port}")
                            hosts[endpoint.host] = endpoint.port
                        }

                        if (sourceHost.isNotBlank()) {
                            sourceCount++
                            hosts.putIfAbsent(sourceHost, SoundTouchRepository.DEFAULT_PORT)
                            Log.d(TAG, "SSDP source host added=$sourceHost")
                        }
                    } catch (_: SocketTimeoutException) {
                        // Continue until discovery window has elapsed.
                    }
                }
            }
        } finally {
            multicastLock?.runCatching {
                release()
                Log.d(TAG, "SSDP multicast lock released")
            }?.onFailure { throwable ->
                Log.w(TAG, "SSDP multicast lock release failed: ${throwable.message}")
            }

            val elapsed = SystemClock.elapsedRealtime() - startTime
            Log.i(
                TAG,
                "discoverViaSsdp(): done responses=$responseCount parsed=$parseCount source=$sourceCount elapsedMs=$elapsed"
            )
        }

        if (hosts.isEmpty()) {
            Log.i(TAG, "discoverViaSsdp(): hosts=0")
            emptyMap()
        } else {
            Log.i(TAG, "discoverViaSsdp(): hosts=${hosts.size} -> $hosts")
            hosts.toMap()
        }
    }

    private fun parseLocationHost(response: String): HostEndpoint? {
        val location = LOCATION_REGEX.find(response)?.groupValues?.getOrNull(1)?.trim()
            ?: run {
                Log.v(TAG, "SSDP location header not found")
                return null
            }

        val parsed = runCatching { URI(location) }.getOrNull() ?: run {
            Log.w(TAG, "SSDP parse location failed invalid URI location=$location")
            return null
        }

        val host = parsed.host?.sanitizeHost()?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "SSDP parse location failed host missing location=$location")
            return null
        }

        val port = if (parsed.port > 0) parsed.port else SoundTouchRepository.DEFAULT_PORT
        return HostEndpoint(host, port)
    }

    private fun buildSsdpSearchRequest(target: String): ByteArray {
        val payload = buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: $SSDP_MULTICAST_IP:$SSDP_PORT\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: 2\r\n")
            append("ST: $target\r\n")
            append("\r\n")
        }
        return payload.toByteArray(Charsets.UTF_8)
    }

    private fun String.sanitizeHost(): String =
        trim()
            .removePrefix("[")
            .removeSuffix("]")
            .substringBefore('%')

    private fun normalizeServiceType(serviceType: String): String {
        return serviceType
            .trim()
            .lowercase(Locale.US)
            .trimEnd('.')
    }

    private fun isSoundTouchServiceType(serviceType: String): Boolean {
        val normalized = normalizeServiceType(serviceType)
        val normalizedWithoutLocal = normalized.removeSuffix(".local")
        return normalizedWithoutLocal == MDNS_SERVICE_TYPE_NO_DOT
    }

    private data class HostEndpoint(val host: String, val port: Int)

    private enum class MdnsServiceType(val raw: String) {
        TRAILING_DOT("_soundtouch._tcp."),
        NO_DOT("_soundtouch._tcp"),
        LOCAL("_soundtouch._tcp.local."),
        LOCAL_NO_TRAIL("_soundtouch._tcp.local")
    }

    companion object {
        private const val TAG = "SoundTouchDiscovery"
        private const val MDNS_SERVICE_TYPE_NO_DOT = "_soundtouch._tcp"

        private const val SSDP_MULTICAST_IP = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private val SSDP_SEARCH_TARGETS = listOf(
            "ssdp:all",
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            "urn:schemas-upnp-org:service:AVTransport:1",
            "urn:schemas-upnp-org:service:RenderingControl:1"
        )
        private const val SSDP_SOCKET_TIMEOUT_MS = 600

        private val LOCATION_REGEX = Regex("(?im)^LOCATION:\\s*(.+)$")
    }
}
