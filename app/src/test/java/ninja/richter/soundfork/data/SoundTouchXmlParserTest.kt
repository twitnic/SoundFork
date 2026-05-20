package ninja.richter.soundfork.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundTouchXmlParserTest {
    @Test
    fun parseToneControls() {
        val toneControls = SoundTouchXml.parseToneControls(
            """
            <audioproducttonecontrols>
                <bass value="2" minValue="-9" maxValue="9" step="1" />
                <treble value="4" minValue="-10" maxValue="10" step="1" />
            </audioproducttonecontrols>
            """.trimIndent()
        )

        assertEquals(2, toneControls?.bass?.value)
        assertEquals(4, toneControls?.treble?.value)
        assertEquals(-10, toneControls?.treble?.minValue)
        assertEquals(10, toneControls?.treble?.maxValue)
    }

    @Test
    fun parseRecents() {
        val recents = SoundTouchXml.parseRecents(
            """
            <recents>
                <recent id="1">
                    <itemName>Energy Sachsen</itemName>
                    <ContentItem source="UPNP" sourceAccount="UPnPUserName" location="http://nrj.de/sachsen" isPresetable="true" />
                </recent>
            </recents>
            """.trimIndent()
        )

        assertEquals(1, recents.size)
        assertEquals(1, recents.first().id)
        assertEquals("Energy Sachsen", recents.first().name)
        assertEquals("UPNP", recents.first().source)
        assertEquals("http://nrj.de/sachsen", recents.first().location)
        assertTrue(recents.first().isPresetable == true)
    }
}
