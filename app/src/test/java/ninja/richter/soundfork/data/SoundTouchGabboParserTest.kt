package ninja.richter.soundfork.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundTouchGabboParserTest {
    @Test
    fun parseVolumeUpdate() {
        val update = SoundTouchGabboParser.parse(
            """
            <updates deviceID="abc">
                <volume>
                    <targetvolume>14</targetvolume>
                    <actualvolume>14</actualvolume>
                    <muteenabled>false</muteenabled>
                </volume>
            </updates>
            """.trimIndent()
        )

        assertEquals(setOf(GabboUpdateType.VOLUME), update.types)
    }

    @Test
    fun parseNowPlayingUpdate() {
        val update = SoundTouchGabboParser.parse(
            """
            <updates deviceID="abc">
                <nowPlaying source="UPNP">
                    <track>Radio Chemnitz</track>
                    <playStatus>PLAY_STATE</playStatus>
                </nowPlaying>
            </updates>
            """.trimIndent()
        )

        assertTrue(GabboUpdateType.NOW_PLAYING in update.types)
    }

    @Test
    fun parsePresetAndZoneUpdate() {
        val update = SoundTouchGabboParser.parse(
            """
            <updates deviceID="abc">
                <presetsUpdated />
                <zone master="abc">
                    <member ipaddress="192.168.178.93">abc</member>
                </zone>
            </updates>
            """.trimIndent()
        )

        assertEquals(setOf(GabboUpdateType.PRESETS, GabboUpdateType.ZONE), update.types)
    }

    @Test
    fun parseUnknownUpdate() {
        val update = SoundTouchGabboParser.parse("<updates deviceID=\"abc\"><something /></updates>")

        assertEquals(setOf(GabboUpdateType.UNKNOWN), update.types)
    }

    @Test
    fun parseConnectionAndUserActivityUpdates() {
        val connection = SoundTouchGabboParser.parse(
            """<updates deviceID="abc"><connectionStateUpdated state="NETWORK_WIFI_CONNECTED" /></updates>"""
        )
        val userActivity = SoundTouchGabboParser.parse("""<userActivityUpdate deviceID="abc" />""")

        assertEquals(setOf(GabboUpdateType.CONNECTION), connection.types)
        assertEquals(setOf(GabboUpdateType.USER_ACTIVITY), userActivity.types)
    }

    @Test
    fun parseErrorUpdate() {
        val update = SoundTouchGabboParser.parse(
            """
            <errorUpdate deviceID="abc">
                <error value="1036" name="UNABLE_TO_PROCESS_NOT_LOGGED_IN" severity="Unrecoverable">
                    UpnpRcvdContentItemInWrongState
                </error>
            </errorUpdate>
            """.trimIndent()
        )

        assertEquals(setOf(GabboUpdateType.ERROR), update.types)
        assertEquals(
            "UNABLE_TO_PROCESS_NOT_LOGGED_IN: Code 1036: UpnpRcvdContentItemInWrongState",
            update.errorMessage
        )
    }
}
