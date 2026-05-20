package ninja.richter.soundfork.data

import android.util.Log
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

object SoundTouchXml {
    private const val TAG = "SoundTouchXml"

    fun prettyOrRaw(xml: String): String {
        val document = runCatching { parseDocument(xml) }.getOrNull() ?: return xml.trim()
        return runCatching {
            val transformerFactory = TransformerFactory.newInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }
            val transformer = transformerFactory.newTransformer().apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
                setOutputProperty(OutputKeys.INDENT, "yes")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }
            StringWriter().use { writer ->
                transformer.transform(DOMSource(document), StreamResult(writer))
                writer.toString().trim()
            }
        }.getOrElse {
            Log.w(TAG, "prettyOrRaw(): fallback due to transform failure error=${it.message}")
            xml.trim()
        }
    }

    fun parseSummary(host: String, infoXml: String): DeviceSummary? =
        SoundTouchDeviceParser.parseSummary(host, infoXml)

    fun extractCapabilityUrls(capabilitiesXml: String): List<String> =
        SoundTouchDeviceParser.extractCapabilityUrls(capabilitiesXml)

    fun parseVolume(volumeXml: String): VolumeState? =
        SoundTouchAudioParser.parseVolume(volumeXml)

    fun parseBass(bassXml: String): BassState? =
        SoundTouchAudioParser.parseBass(bassXml)

    fun parseToneControls(toneXml: String): ToneControlState? =
        SoundTouchAudioParser.parseToneControls(toneXml)

    fun parseSources(sourcesXml: String): List<SourceItem> =
        SoundTouchContentParser.parseSources(sourcesXml)

    fun parseNowPlayingSource(nowPlayingXml: String): SourceItem? =
        SoundTouchContentParser.parseNowPlayingSource(nowPlayingXml)

    fun parseNowPlaying(nowPlayingXml: String): NowPlayingState? =
        SoundTouchContentParser.parseNowPlaying(nowPlayingXml)

    fun parsePresets(presetsXml: String): List<PresetItem> =
        SoundTouchContentParser.parsePresets(presetsXml)

    fun parseRecents(recentsXml: String): List<BoseRecentItem> =
        SoundTouchContentParser.parseRecents(recentsXml)

    fun parseZone(zoneXml: String): ZoneState? =
        SoundTouchZoneParser.parseZone(zoneXml)

    private fun parseDocument(xml: String): Document? {
        val primaryFactory = runCatching { createDocumentBuilderFactory() }.getOrNull()
        return if (primaryFactory == null) {
            parseDocumentWithFactory(DocumentBuilderFactory.newInstance(), xml)
        } else {
            parseDocumentWithFactory(primaryFactory, xml) ?: parseDocumentWithFactory(
                DocumentBuilderFactory.newInstance(),
                xml
            )
        }
    }

    private fun parseDocumentWithFactory(factory: DocumentBuilderFactory, xml: String): Document? {
        return runCatching {
            val builder = factory.newDocumentBuilder()
            builder.parse(InputSource(StringReader(xml)))
        }.onFailure { throwable ->
            Log.w(TAG, "parseDocument(): parse failed: ${throwable.message}")
        }.getOrNull()
    }

    private fun sanitizeXml(xml: String): String {
        return xml.replace("\u0000", "").trimStart('\uFEFF', '\u200B', '\u200E', '\u200F')
    }

    private fun createDocumentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching {
                isXIncludeAware = false
            }.onFailure { throwable ->
                Log.w(TAG, "parseDocument(): xinclude-aware not set: ${throwable.message}")
            }
            runCatching {
                isExpandEntityReferences = false
            }.onFailure { throwable ->
                Log.w(TAG, "parseDocument(): expand-entity-references not set: ${throwable.message}")
            }

            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                .onFailure { throwable ->
                    Log.w(TAG, "parseDocument(): secure processing not set: ${throwable.message}")
                }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                .onFailure { throwable ->
                    Log.w(TAG, "parseDocument(): disallow-doctype not set: ${throwable.message}")
                }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                .onFailure { throwable ->
                    Log.w(TAG, "parseDocument(): external-general-entities not set: ${throwable.message}")
                }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                .onFailure { throwable ->
                    Log.w(TAG, "parseDocument(): external-parameter-entities not set: ${throwable.message}")
                }
        }
    }

    private fun parseSummaryFromRawXml(xml: String): ParsedSummary {
        val normalized = sanitizeXml(xml)
        val name = TAG_REGEX_NAME.find(normalized)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val type = TAG_REGEX_TYPE.find(normalized)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val deviceId = ATTRIBUTE_REGEX_DEVICE_ID.find(normalized)?.destructured
            ?.let { match ->
                val quoted = match.component1().trim().ifEmpty { match.component2().trim() }
                quoted.takeIf { it.isNotEmpty() }
            } ?: ATTRIBUTE_REGEX_DEVICE_TAG_ID.find(normalized)?.destructured
            ?.let { match ->
                val quoted = match.component1().trim().ifEmpty { match.component2().trim() }
                quoted.takeIf { it.isNotEmpty() }
            } ?: TAG_REGEX_DEVICE_ID_TAG.find(normalized)?.groupValues?.getOrNull(1)
            ?.trim()?.takeIf { it.isNotBlank() }

        return ParsedSummary(
            name = name,
            type = type,
            deviceId = deviceId
        )
    }

    private fun looksLikeSoundTouchInfo(xml: String): Boolean {
        val normalized = xml.lowercase()
        return normalized.contains("<info")
    }

    private fun firstMatch(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun attributeOf(element: Element?, attributeName: String): String? {
        return element
            ?.getAttribute(attributeName)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun textOfFirstDescendant(element: Element, tagName: String): String? {
        return element
            .getElementsByTagName(tagName)
            .item(0)
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun textFromRegex(xml: String, tagName: String): String? {
        val escapedTagName = Regex.escape(tagName)
        return Regex("(?is)<$escapedTagName\\b[^>]*>(.*?)</$escapedTagName>")
            .find(xml)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseBoolean(value: String): Boolean? {
        return when (value.trim().lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
    }

    private data class ParsedSummary(
        val name: String?,
        val type: String?,
        val deviceId: String?
    )

    private fun ParsedSummary.hasAny(): Boolean {
        return name != null || type != null || deviceId != null
    }

    private fun NowPlayingState.hasAnyNowPlayingValue(): Boolean {
        return source != null ||
            sourceAccount != null ||
            location != null ||
            track != null ||
            artist != null ||
            album != null ||
            stationName != null ||
            playStatus != null ||
            streamType != null ||
            artUrl != null
    }

    private fun PresetItem.hasAnyPresetValue(): Boolean {
        return id != null ||
            name != null ||
            source != null ||
            sourceAccount != null ||
            location != null ||
            isPresetable != null
    }

    private fun BoseRecentItem.hasAnyRecentValue(): Boolean {
        return id != null ||
            name != null ||
            source != null ||
            sourceAccount != null ||
            location != null ||
            isPresetable != null
    }

    private fun ZoneMember.hasAnyZoneMemberValue(): Boolean {
        return deviceId != null ||
            ipAddress != null ||
            name != null ||
            role != null
    }

    private fun parseAudioControlValue(document: Document, tagName: String): AudioControlValue? {
        val nodes = document.getElementsByTagName(tagName)
        if (nodes.length == 0) {
            return null
        }
        val attributes = nodes.item(0)?.attributes ?: return null
        val value = attributeOf(attributes, "value")?.toIntOrNull()
        val minValue = attributeOf(attributes, "minValue")?.toIntOrNull()
        val maxValue = attributeOf(attributes, "maxValue")?.toIntOrNull()
        val step = attributeOf(attributes, "step")?.toIntOrNull()
        return AudioControlValue(
            value = value,
            minValue = minValue,
            maxValue = maxValue,
            step = step
        ).takeIf {
            it.value != null || it.minValue != null || it.maxValue != null || it.step != null
        }
    }

    private fun attributeOf(attributes: org.w3c.dom.NamedNodeMap, attributeName: String): String? {
        return (0 until attributes.length)
            .mapNotNull { attributes.item(it) }
            .firstOrNull { it.nodeName.equals(attributeName, ignoreCase = true) }
            ?.nodeValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private val TAG_REGEX_NAME = Regex("(?is)<name[^>]*>(.*?)</name>")
    private val TAG_REGEX_TYPE = Regex("(?is)<type[^>]*>(.*?)</type>")
    private val ATTRIBUTE_REGEX_DEVICE_ID = Regex(
        "(?i)<info[^>]*\\bdeviceid\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)')"
    )
    private val ATTRIBUTE_REGEX_DEVICE_TAG_ID = Regex(
        "(?i)<device[^>]*\\bdeviceid\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)')"
    )
    private val TAG_REGEX_DEVICE_ID_TAG = Regex(
        "(?i)<device(?:id|ID)[^>]*>(.*?)</device(?:id|ID)>"
    )
    private val CAPABILITY_PATH_REGEX = Regex(
        "<capability\\s+[^>]*\\burl\\s*=\\s*\"([^\"]+)\""
    )
    private val TAG_REGEX_TARGET_VOLUME = Regex("(?is)<targetvolume[^>]*>(.*?)</targetvolume>")
    private val TAG_REGEX_ACTUAL_VOLUME = Regex("(?is)<actualvolume[^>]*>(.*?)</actualvolume>")
    private val TAG_REGEX_MUTE_ENABLED = Regex("(?is)<muteenabled[^>]*>(.*?)</muteenabled>")
    private val TAG_REGEX_TARGET_BASS = Regex("(?is)<targetbass[^>]*>(-?\\d+)</targetbass>")
    private val TAG_REGEX_ACTUAL_BASS = Regex("(?is)<actualbass[^>]*>(-?\\d+)</actualbass>")
    private val TAG_REGEX_BASS_VALUE = Regex("(?is)<bass[^>]*>(-?\\d+)</bass>")
    private val SOURCE_ITEM_REGEX = Regex("(?is)<sourceItem\\s+([^>]*)>(.*?)</sourceItem>")
    private val NOW_PLAYING_TAG_REGEX = Regex("(?is)<nowPlaying\\s+([^>]*)>")
    private val CONTENT_ITEM_TAG_REGEX = Regex("(?is)<ContentItem\\s+([^>]*)>")
    private val CONTENT_ITEM_ANY_TAG_REGEX = Regex("(?is)<(?:ContentItem|contentItem)\\s+([^>]*)>")
    private val PRESET_REGEX = Regex("(?is)<preset\\s+([^>]*)>(.*?)</preset>")
    private val RECENT_REGEX = Regex("(?is)<recent\\s+([^>]*)>(.*?)</recent>")

    private fun firstDescendantElement(element: Element, vararg tagNames: String): Element? {
        for (tagName in tagNames) {
            val node = element.getElementsByTagName(tagName).item(0) as? Element
            if (node != null) {
                return node
            }
        }
        return null
    }

    private fun XML_ATTRIBUTE_REGEX(attributeName: String): Regex {
        return Regex("(?i)\\b$attributeName\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')")
    }

    private fun attributeFromRegex(attributes: String, attributeName: String): String? {
        val match = XML_ATTRIBUTE_REGEX(attributeName).find(attributes) ?: return null
        return match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
            ?: match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
    }

    private fun textOfFirstTag(document: Document, tagName: String): String? {
        val nodes = document.getElementsByTagName(tagName)
        if (nodes.length == 0) {
            return null
        }
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun attributeOfFirstTag(document: Document, tagName: String, attributeName: String): String? {
        val nodes = document.getElementsByTagName(tagName)
        if (nodes.length == 0) {
            return null
        }

        val attributes = nodes.item(0)?.attributes
        if (attributes == null) {
            return null
        }

        val direct = attributes.getNamedItem(attributeName)
        if (direct != null) {
            return direct.nodeValue?.trim()?.takeIf { it.isNotEmpty() }
        }

        val caseInsensitive = (0 until attributes.length)
            .mapNotNull { attributes.item(it) }
            .firstOrNull { it.nodeName.equals(attributeName, ignoreCase = true) }
            ?.nodeValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return caseInsensitive
    }
}
