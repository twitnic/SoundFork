package ninja.richter.soundfork.data

internal fun String.xmlEscape(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

internal fun normalizePath(path: String): String {
    if (path.isBlank()) {
        return "/"
    }
    return if (path.startsWith('/')) path else "/$path"
}

internal fun normalizeHost(host: String): String {
    val clean = host.trim().substringBefore('%')
    return if (clean.contains(':') && !clean.startsWith("[") && !clean.endsWith("]")) {
        "[$clean]"
    } else {
        clean
    }
}

internal fun defaultReadDescriptors(): List<EndpointDescriptor> = listOf(
    EndpointDescriptor(
        title = "Info",
        aliases = listOf("/info")
    ),
    EndpointDescriptor(
        title = "Sources",
        aliases = listOf("/sources")
    ),
    EndpointDescriptor(
        title = "Capabilities",
        aliases = listOf("/capabilities")
    ),
    EndpointDescriptor(
        title = "Bass Capabilities",
        aliases = listOf("/bassCapabilities"),
        optional = true
    ),
    EndpointDescriptor(
        title = "Bass",
        aliases = listOf("/bass"),
        optional = true
    ),
    EndpointDescriptor(
        title = "Zone",
        aliases = listOf("/getZone"),
        optional = true
    ),
    EndpointDescriptor(
        title = "Now Playing",
        aliases = listOf("/now_playing", "/nowplaying", "/now%20playing")
    ),
    EndpointDescriptor(
        title = "Track Info",
        aliases = listOf("/trackInfo"),
        optional = true
    ),
    EndpointDescriptor(
        title = "Volume",
        aliases = listOf("/volume")
    ),
    EndpointDescriptor(
        title = "Presets",
        aliases = listOf("/presets")
    ),
    EndpointDescriptor(
        title = "Recents",
        aliases = listOf("/recents"),
        optional = true
    )
)
