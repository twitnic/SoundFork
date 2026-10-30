package codes.offline.soundfork.model

import codes.offline.soundfork.data.NowPlayingState

/** Require explicit PLAY_STATE and the exact URI accepted by AVTransport, not a matching title. */
fun NowPlayingState.confirmsRadioStream(acceptedUrl: String): Boolean =
    source.equals("UPNP", true) &&
        playStatus?.uppercase(java.util.Locale.ROOT) in setOf("PLAY_STATE", "PLAYING") &&
        location?.trim() == acceptedUrl
