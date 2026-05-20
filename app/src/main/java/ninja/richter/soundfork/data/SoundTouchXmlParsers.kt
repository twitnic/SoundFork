package ninja.richter.soundfork.data

import android.util.Log
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal object SoundTouchDeviceParser {
    fun parseSummary(host: String, infoXml: String): DeviceSummary? {
        val normalized = SoundTouchXmlSupport.sanitizeXml(infoXml)
        if (normalized.isBlank() || !normalized.lowercase().contains("<info")) {
            return null
        }

        val document = SoundTouchXmlSupport.parseDocument(normalized)
        val root = document?.documentElement
        val parsed = ParsedSummary(
            name = SoundTouchXmlSupport.textOfFirstTag(document, "name")
                ?: TAG_REGEX_NAME.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() },
            type = SoundTouchXmlSupport.textOfFirstTag(document, "type")
                ?: TAG_REGEX_TYPE.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() },
            deviceId = SoundTouchXmlSupport.firstMatch(
                SoundTouchXmlSupport.attributeOf(root, "deviceID"),
                SoundTouchXmlSupport.attributeOf(root, "deviceId"),
                SoundTouchXmlSupport.attributeOfFirstTag(document, "device", "deviceID"),
                TAG_REGEX_DEVICE_ID_TAG.find(normalized)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            )
        )

        return if (parsed.hasAny()) {
            DeviceSummary(host = host, name = parsed.name, type = parsed.type, deviceId = parsed.deviceId)
        } else {
            null
        }
    }

    fun extractCapabilityUrls(capabilitiesXml: String): List<String> {
        val sanitized = SoundTouchXmlSupport.sanitizeXml(capabilitiesXml)
        if (sanitized.isBlank()) {
            return emptyList()
        }
        val document = SoundTouchXmlSupport.parseDocument(sanitized)
        val fromDom = if (document != null) {
            buildList {
                val nodes = document.getElementsByTagName("capability")
                for (index in 0 until nodes.length) {
                    val element = nodes.item(index) as? Element ?: continue
                    SoundTouchXmlSupport.attributeOf(element, "url")
                        ?.takeIf { it.startsWith("/") }
                        ?.let(::add)
                }
            }
        } else {
            emptyList()
        }
        val fromRegex = CAPABILITY_PATH_REGEX.findAll(sanitized)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf { path -> path.startsWith("/") } }
            .toList()
        return (fromDom + fromRegex).distinct()
    }

    private data class ParsedSummary(val name: String?, val type: String?, val deviceId: String?) {
        fun hasAny(): Boolean = name != null || type != null || deviceId != null
    }

    private val TAG_REGEX_NAME = Regex("(?is)<name[^>]*>(.*?)</name>")
    private val TAG_REGEX_TYPE = Regex("(?is)<type[^>]*>(.*?)</type>")
    private val TAG_REGEX_DEVICE_ID_TAG = Regex("(?i)<device(?:id|ID)[^>]*>(.*?)</device(?:id|ID)>")
    private val CAPABILITY_PATH_REGEX = Regex("<capability\\s+[^>]*\\burl\\s*=\\s*\"([^\"]+)\"")
}

internal object SoundTouchAudioParser {
    fun parseVolume(volumeXml: String): VolumeState? {
        val sanitized = SoundTouchXmlSupport.sanitizeXml(volumeXml)
        if (sanitized.isBlank()) {
            return null
        }
        val document = SoundTouchXmlSupport.parseDocument(sanitized)
        val merged = VolumeState(
            targetVolume = SoundTouchXmlSupport.textOfFirstTag(document, "targetvolume")?.toIntOrNull()
                ?: TAG_REGEX_TARGET_VOLUME.find(sanitized)?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull(),
            actualVolume = SoundTouchXmlSupport.textOfFirstTag(document, "actualvolume")?.toIntOrNull()
                ?: TAG_REGEX_ACTUAL_VOLUME.find(sanitized)?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull(),
            muteEnabled = SoundTouchXmlSupport.textOfFirstTag(document, "muteenabled")?.let(SoundTouchXmlSupport::parseBoolean)
                ?: TAG_REGEX_MUTE_ENABLED.find(sanitized)
                    ?.groupValues?.getOrNull(1)?.trim()?.let(SoundTouchXmlSupport::parseBoolean)
        )
        return merged.takeIf {
            it.targetVolume != null || it.actualVolume != null || it.muteEnabled != null
        }
    }

    fun parseBass(bassXml: String): BassState? {
        val sanitized = SoundTouchXmlSupport.sanitizeXml(bassXml)
        if (sanitized.isBlank()) {
            return null
        }
        val document = SoundTouchXmlSupport.parseDocument(sanitized)
        val targetFromDom = SoundTouchXmlSupport.textOfFirstTag(document, "targetbass")?.toIntOrNull()
            ?: SoundTouchXmlSupport.textOfFirstTag(document, "bass")?.toIntOrNull()
        val targetFromRegex = TAG_REGEX_TARGET_BASS.find(sanitized)
            ?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull()
            ?: TAG_REGEX_BASS_VALUE.find(sanitized)?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull()
        val actualFromDom = SoundTouchXmlSupport.textOfFirstTag(document, "actualbass")?.toIntOrNull()
        val actualFromRegex = TAG_REGEX_ACTUAL_BASS.find(sanitized)
            ?.groupValues?.getOrNull(1)?.trim()?.toIntOrNull()
        val merged = BassState(
            targetBass = targetFromDom ?: targetFromRegex,
            actualBass = actualFromDom ?: actualFromRegex
        )
        return merged.takeIf { it.targetBass != null || it.actualBass != null }
    }

    fun parseToneControls(toneXml: String): ToneControlState? {
        val sanitized = SoundTouchXmlSupport.sanitizeXml(toneXml)
        if (sanitized.isBlank()) {
            return null
        }
        val document = SoundTouchXmlSupport.parseDocument(sanitized)
        val state = ToneControlState(
            bass = document?.let { parseAudioControlValue(it, "bass") },
            treble = document?.let { parseAudioControlValue(it, "treble") }
        )
        return state.takeIf { it.bass != null || it.treble != null }
    }

    private fun parseAudioControlValue(document: Document, tagName: String): AudioControlValue? {
        val node = document.getElementsByTagName(tagName).item(0) ?: return null
        val attributes = node.attributes ?: return null
        val value = SoundTouchXmlSupport.attributeOf(attributes, "value")?.toIntOrNull()
        val minValue = SoundTouchXmlSupport.attributeOf(attributes, "minValue")?.toIntOrNull()
        val maxValue = SoundTouchXmlSupport.attributeOf(attributes, "maxValue")?.toIntOrNull()
        val step = SoundTouchXmlSupport.attributeOf(attributes, "step")?.toIntOrNull()
        return AudioControlValue(value = value, minValue = minValue, maxValue = maxValue, step = step)
            .takeIf { it.value != null || it.minValue != null || it.maxValue != null || it.step != null }
    }

    private val TAG_REGEX_TARGET_VOLUME = Regex("(?is)<targetvolume[^>]*>(.*?)</targetvolume>")
    private val TAG_REGEX_ACTUAL_VOLUME = Regex("(?is)<actualvolume[^>]*>(.*?)</actualvolume>")
    private val TAG_REGEX_MUTE_ENABLED = Regex("(?is)<muteenabled[^>]*>(.*?)</muteenabled>")
    private val TAG_REGEX_TARGET_BASS = Regex("(?is)<targetbass[^>]*>(-?\\d+)</targetbass>")
    private val TAG_REGEX_ACTUAL_BASS = Regex("(?is)<actualbass[^>]*>(-?\\d+)</actualbass>")
    private val TAG_REGEX_BASS_VALUE = Regex("(?is)<bass[^>]*>(-?\\d+)</bass>")
}

internal object SoundTouchContentParser {
    fun parseSources(sourcesXml: String): List<SourceItem> {
        val sanitized = SoundTouchXmlSupport.sanitizeXml(sourcesXml)
        if (sanitized.isBlank()) {
            return emptyList()
        }
        val document = SoundTouchXmlSupport.parseDocument(sanitized)
        if (document != null) {
            val items = mutableListOf<SourceItem>()
            val nodes = document.getElementsByTagName("sourceItem")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val source = SoundTouchXmlSupport.attributeOf(element, "source").orEmpty()
                if (source.isBlank()) {
                    continue
                }
                items += SourceItem(
                    source = source,
                    sourceAccount = SoundTouchXmlSupport.attributeOf(element, "sourceAccount"),
                    status = SoundTouchXmlSupport.attributeOf(element, "status"),
                    name = element.textContent?.trim()?.takeIf { it.isNotBlank() }
                )
            }
            if (items.isNotEmpty()) {
                return items
            }
        }
        return SOURCE_ITEM_REGEX.findAll(sanitized)
            .mapNotNull { match ->
                val attrs = match.groupValues.getOrNull(1).orEmpty()
                val body = match.groupValues.getOrNull(2).orEmpty()
                val source = SoundTouchXmlSupport.attributeFromRegex(attrs, "source") ?: return@mapNotNull null
                SourceItem(
                    source = source,
                    sourceAccount = SoundTouchXmlSupport.attributeFromRegex(attrs, "sourceAccount"),
                    status = SoundTouchXmlSupport.attributeFromRegex(attrs, "status"),
                    name = body.trim().takeIf { it.isNotBlank() }
                )
            }
            .toList()
    }

    fun parseNowPlayingSource(nowPlayingXml: String): SourceItem? {
        return parseNowPlaying(nowPlayingXml)?.source?.takeIf { it.isNotBlank() }?.let { source ->
            SourceItem(source = source, sourceAccount = parseNowPlaying(nowPlayingXml)?.sourceAccount)
        }
    }

    fun parseNowPlaying(nowPlayingXml: String): NowPlayingState? {
        val sanitized = SoundTouchXmlSupport.sanitizeXml(nowPlayingXml)
        if (sanitized.isBlank()) {
            return null
        }
        val document = SoundTouchXmlSupport.parseDocument(sanitized)
        val parsedFromDom = document?.let {
            NowPlayingState(
                source = SoundTouchXmlSupport.attributeOfFirstTag(it, "nowPlaying", "source")
                    ?: SoundTouchXmlSupport.attributeOfFirstTag(it, "ContentItem", "source"),
                sourceAccount = SoundTouchXmlSupport.attributeOfFirstTag(it, "nowPlaying", "sourceAccount")
                    ?: SoundTouchXmlSupport.attributeOfFirstTag(it, "ContentItem", "sourceAccount"),
                location = SoundTouchXmlSupport.attributeOfFirstTag(it, "ContentItem", "location"),
                track = SoundTouchXmlSupport.textOfFirstTag(it, "track"),
                artist = SoundTouchXmlSupport.textOfFirstTag(it, "artist"),
                album = SoundTouchXmlSupport.textOfFirstTag(it, "album"),
                stationName = SoundTouchXmlSupport.textOfFirstTag(it, "stationName"),
                playStatus = SoundTouchXmlSupport.textOfFirstTag(it, "playStatus"),
                streamType = SoundTouchXmlSupport.textOfFirstTag(it, "streamType"),
                artUrl = SoundTouchXmlSupport.textOfFirstTag(it, "art")
            )
        }
        val contentItemTag = CONTENT_ITEM_TAG_REGEX.find(sanitized)?.groupValues?.getOrNull(1).orEmpty()
        val parsedFromRegex = NowPlayingState(
            source = SoundTouchXmlSupport.attributeFromRegex(contentItemTag, "source"),
            sourceAccount = SoundTouchXmlSupport.attributeFromRegex(contentItemTag, "sourceAccount"),
            location = SoundTouchXmlSupport.attributeFromRegex(contentItemTag, "location"),
            track = SoundTouchXmlSupport.textFromRegex(sanitized, "track"),
            artist = SoundTouchXmlSupport.textFromRegex(sanitized, "artist"),
            album = SoundTouchXmlSupport.textFromRegex(sanitized, "album"),
            stationName = SoundTouchXmlSupport.textFromRegex(sanitized, "stationName"),
            playStatus = SoundTouchXmlSupport.textFromRegex(sanitized, "playStatus"),
            streamType = SoundTouchXmlSupport.textFromRegex(sanitized, "streamType"),
            artUrl = SoundTouchXmlSupport.textFromRegex(sanitized, "art")
        )
        val merged = NowPlayingState(
            source = parsedFromDom?.source ?: parsedFromRegex.source,
            sourceAccount = parsedFromDom?.sourceAccount ?: parsedFromRegex.sourceAccount,
            location = parsedFromDom?.location ?: parsedFromRegex.location,
            track = parsedFromDom?.track ?: parsedFromRegex.track,
            artist = parsedFromDom?.artist ?: parsedFromRegex.artist,
            album = parsedFromDom?.album ?: parsedFromRegex.album,
            stationName = parsedFromDom?.stationName ?: parsedFromRegex.stationName,
            playStatus = parsedFromDom?.playStatus ?: parsedFromRegex.playStatus,
            streamType = parsedFromDom?.streamType ?: parsedFromRegex.streamType,
            artUrl = parsedFromDom?.artUrl ?: parsedFromRegex.artUrl
        )
        return merged.takeIf { it.hasAnyNowPlayingValue() }
    }

    fun parsePresets(presetsXml: String): List<PresetItem> {
        return parseContentItems(
            xml = presetsXml,
            tagName = "preset",
            regex = PRESET_REGEX
        ) { id, name, source, sourceAccount, location, presetable ->
            PresetItem(id, name, source, sourceAccount, location, presetable)
        }.sortedWith(compareBy<PresetItem> { it.id ?: Int.MAX_VALUE }.thenBy { it.name.orEmpty() })
    }

    fun parseRecents(recentsXml: String): List<BoseRecentItem> {
        return parseContentItems(
            xml = recentsXml,
            tagName = "recent",
            regex = RECENT_REGEX
        ) { id, name, source, sourceAccount, location, presetable ->
            BoseRecentItem(id, name, source, sourceAccount, location, presetable)
        }.sortedWith(compareBy<BoseRecentItem> { it.id ?: Int.MAX_VALUE }.thenBy { it.name.orEmpty() })
    }

    private fun <T> parseContentItems(
        xml: String,
        tagName: String,
        regex: Regex,
        build: (Int?, String?, String?, String?, String?, Boolean?) -> T
    ): List<T> {
        val sanitized = SoundTouchXmlSupport.sanitizeXml(xml)
        if (sanitized.isBlank()) {
            return emptyList()
        }
        val document = SoundTouchXmlSupport.parseDocument(sanitized)
        if (document != null) {
            val items = mutableListOf<T>()
            val nodes = document.getElementsByTagName(tagName)
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val contentItem = SoundTouchXmlSupport.firstDescendantElement(element, "ContentItem", "contentItem")
                val source = SoundTouchXmlSupport.firstMatch(
                    SoundTouchXmlSupport.attributeOf(contentItem, "source"),
                    SoundTouchXmlSupport.attributeOf(element, "source")
                )
                val sourceAccount = SoundTouchXmlSupport.firstMatch(
                    SoundTouchXmlSupport.attributeOf(contentItem, "sourceAccount"),
                    SoundTouchXmlSupport.attributeOf(element, "sourceAccount")
                )
                val location = SoundTouchXmlSupport.firstMatch(
                    SoundTouchXmlSupport.attributeOf(contentItem, "location"),
                    SoundTouchXmlSupport.attributeOf(element, "location")
                )
                val item = build(
                    SoundTouchXmlSupport.firstMatch(
                        SoundTouchXmlSupport.attributeOf(element, "id"),
                        SoundTouchXmlSupport.attributeOf(element, "${tagName}ID"),
                        SoundTouchXmlSupport.attributeOf(element, "${tagName}Id")
                    )?.toIntOrNull(),
                    SoundTouchXmlSupport.firstMatch(
                        SoundTouchXmlSupport.textOfFirstDescendant(element, "itemName"),
                        SoundTouchXmlSupport.textOfFirstDescendant(element, "stationName"),
                        SoundTouchXmlSupport.textOfFirstDescendant(element, "name"),
                        SoundTouchXmlSupport.attributeOf(contentItem, "name")
                    ),
                    source,
                    sourceAccount,
                    location,
                    SoundTouchXmlSupport.firstMatch(
                        SoundTouchXmlSupport.attributeOf(contentItem, "isPresetable"),
                        SoundTouchXmlSupport.attributeOf(element, "isPresetable")
                    )?.let(SoundTouchXmlSupport::parseBoolean)
                )
                if (source != null || sourceAccount != null || location != null) {
                    items += item
                }
            }
            if (items.isNotEmpty()) {
                return items
            }
        }
        return regex.findAll(sanitized).mapNotNull { match ->
            val attrs = match.groupValues.getOrNull(1).orEmpty()
            val body = match.groupValues.getOrNull(2).orEmpty()
            val contentAttrs = CONTENT_ITEM_ANY_TAG_REGEX.find(body)?.groupValues?.getOrNull(1).orEmpty()
            val source = SoundTouchXmlSupport.firstMatch(
                SoundTouchXmlSupport.attributeFromRegex(contentAttrs, "source"),
                SoundTouchXmlSupport.attributeFromRegex(attrs, "source")
            )
            val sourceAccount = SoundTouchXmlSupport.firstMatch(
                SoundTouchXmlSupport.attributeFromRegex(contentAttrs, "sourceAccount"),
                SoundTouchXmlSupport.attributeFromRegex(attrs, "sourceAccount")
            )
            val location = SoundTouchXmlSupport.firstMatch(
                SoundTouchXmlSupport.attributeFromRegex(contentAttrs, "location"),
                SoundTouchXmlSupport.attributeFromRegex(attrs, "location")
            )
            if (source == null && sourceAccount == null && location == null) {
                null
            } else {
                build(
                    SoundTouchXmlSupport.attributeFromRegex(attrs, "id")?.toIntOrNull(),
                    SoundTouchXmlSupport.firstMatch(
                        SoundTouchXmlSupport.textFromRegex(body, "itemName"),
                        SoundTouchXmlSupport.textFromRegex(body, "stationName"),
                        SoundTouchXmlSupport.textFromRegex(body, "name"),
                        SoundTouchXmlSupport.attributeFromRegex(contentAttrs, "name")
                    ),
                    source,
                    sourceAccount,
                    location,
                    SoundTouchXmlSupport.firstMatch(
                        SoundTouchXmlSupport.attributeFromRegex(contentAttrs, "isPresetable"),
                        SoundTouchXmlSupport.attributeFromRegex(attrs, "isPresetable")
                    )?.let(SoundTouchXmlSupport::parseBoolean)
                )
            }
        }.toList()
    }

    private fun NowPlayingState.hasAnyNowPlayingValue(): Boolean {
        return source != null || sourceAccount != null || location != null || track != null ||
            artist != null || album != null || stationName != null || playStatus != null ||
            streamType != null || artUrl != null
    }

    private val SOURCE_ITEM_REGEX = Regex("(?is)<sourceItem\\s+([^>]*)>(.*?)</sourceItem>")
    private val CONTENT_ITEM_TAG_REGEX = Regex("(?is)<ContentItem\\s+([^>]*)>")
    private val CONTENT_ITEM_ANY_TAG_REGEX = Regex("(?is)<(?:ContentItem|contentItem)\\s+([^>]*)>")
    private val PRESET_REGEX = Regex("(?is)<preset\\s+([^>]*)>(.*?)</preset>")
    private val RECENT_REGEX = Regex("(?is)<recent\\s+([^>]*)>(.*?)</recent>")
}

internal object SoundTouchZoneParser {
    fun parseZone(zoneXml: String): ZoneState? {
        val sanitized = SoundTouchXmlSupport.sanitizeXml(zoneXml)
        if (sanitized.isBlank()) {
            return null
        }
        val document = SoundTouchXmlSupport.parseDocument(sanitized) ?: return null
        val zoneElement = document.getElementsByTagName("zone").item(0) as? Element
            ?: document.documentElement
        val members = buildList {
            val memberNodes = document.getElementsByTagName("member")
            for (index in 0 until memberNodes.length) {
                val element = memberNodes.item(index) as? Element ?: continue
                val member = ZoneMember(
                    deviceId = SoundTouchXmlSupport.firstMatch(
                        SoundTouchXmlSupport.attributeOf(element, "deviceID"),
                        SoundTouchXmlSupport.attributeOf(element, "deviceId"),
                        element.textContent?.trim()?.takeIf { it.isNotBlank() }
                    ),
                    ipAddress = SoundTouchXmlSupport.firstMatch(
                        SoundTouchXmlSupport.attributeOf(element, "ipaddress"),
                        SoundTouchXmlSupport.attributeOf(element, "ipAddress"),
                        SoundTouchXmlSupport.attributeOf(element, "ip")
                    ),
                    name = SoundTouchXmlSupport.attributeOf(element, "name"),
                    role = SoundTouchXmlSupport.attributeOf(element, "role")
                )
                if (member.deviceId != null || member.ipAddress != null || member.name != null || member.role != null) {
                    add(member)
                }
            }
        }
        val zoneState = ZoneState(
            masterDeviceId = SoundTouchXmlSupport.firstMatch(
                SoundTouchXmlSupport.attributeOf(zoneElement, "master"),
                SoundTouchXmlSupport.attributeOf(zoneElement, "masterDeviceID"),
                SoundTouchXmlSupport.attributeOf(zoneElement, "masterDeviceId")
            ),
            senderIpAddress = SoundTouchXmlSupport.firstMatch(
                SoundTouchXmlSupport.attributeOf(zoneElement, "senderIPAddress"),
                SoundTouchXmlSupport.attributeOf(zoneElement, "senderIpAddress"),
                SoundTouchXmlSupport.attributeOf(zoneElement, "ipaddress")
            ),
            members = members
        )
        return zoneState.takeIf {
            !it.masterDeviceId.isNullOrBlank() || !it.senderIpAddress.isNullOrBlank() || it.members.isNotEmpty()
        }
    }
}

internal object SoundTouchXmlSupport {
    fun parseDocument(xml: String): Document? {
        val primaryFactory = runCatching { createDocumentBuilderFactory() }.getOrNull()
        return if (primaryFactory == null) {
            parseDocumentWithFactory(DocumentBuilderFactory.newInstance(), xml)
        } else {
            parseDocumentWithFactory(primaryFactory, xml)
                ?: parseDocumentWithFactory(DocumentBuilderFactory.newInstance(), xml)
        }
    }

    fun sanitizeXml(xml: String): String {
        return xml.replace("\u0000", "").trimStart('\uFEFF', '\u200B', '\u200E', '\u200F')
    }

    fun firstMatch(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    fun attributeOf(element: Element?, attributeName: String): String? {
        return element?.getAttribute(attributeName)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun attributeOf(attributes: org.w3c.dom.NamedNodeMap, attributeName: String): String? {
        return (0 until attributes.length)
            .mapNotNull { attributes.item(it) }
            .firstOrNull { it.nodeName.equals(attributeName, ignoreCase = true) }
            ?.nodeValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun textOfFirstDescendant(element: Element, tagName: String): String? {
        return element.getElementsByTagName(tagName).item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    fun textOfFirstTag(document: Document?, tagName: String): String? {
        val nodes = document?.getElementsByTagName(tagName) ?: return null
        if (nodes.length == 0) {
            return null
        }
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun attributeOfFirstTag(document: Document?, tagName: String, attributeName: String): String? {
        val nodes = document?.getElementsByTagName(tagName) ?: return null
        if (nodes.length == 0) {
            return null
        }
        val attrs = nodes.item(0)?.attributes ?: return null
        return attributeOf(attrs, attributeName)
    }

    fun textFromRegex(xml: String, tagName: String): String? {
        val escapedTagName = Regex.escape(tagName)
        return Regex("(?is)<$escapedTagName\\b[^>]*>(.*?)</$escapedTagName>")
            .find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun parseBoolean(value: String): Boolean? {
        return when (value.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    fun firstDescendantElement(element: Element, vararg tagNames: String): Element? {
        for (tagName in tagNames) {
            val node = element.getElementsByTagName(tagName).item(0) as? Element
            if (node != null) {
                return node
            }
        }
        return null
    }

    fun attributeFromRegex(attributes: String, attributeName: String): String? {
        val match = xmlAttributeRegex(attributeName).find(attributes) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
            ?: match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
    }

    private fun parseDocumentWithFactory(factory: DocumentBuilderFactory, xml: String): Document? {
        return runCatching {
            val builder = factory.newDocumentBuilder()
            builder.parse(InputSource(StringReader(xml)))
        }.onFailure { throwable ->
            Log.w(TAG, "parseDocument(): parse failed: ${throwable.message}")
        }.getOrNull()
    }

    private fun createDocumentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
    }

    private fun xmlAttributeRegex(attributeName: String): Regex {
        return Regex("(?i)\\b$attributeName\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    }

    private const val TAG = "SoundTouchXml"
}
