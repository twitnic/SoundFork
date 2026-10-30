package ninja.richter.soundfork

import kotlinx.coroutines.runBlocking
import ninja.richter.soundfork.data.SoundTouchRepository
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class DlnaStreamPlaybackTest {
    @Test fun playbackReportsTheUrlActuallyAcceptedByTheSpeaker() = runBlocking {
        var rejectedHttps = 0
        var started = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val soapAction = request.header("SOAPAction").orEmpty()
            val body = request.body?.let { Buffer().apply { it.writeTo(this) }.readUtf8() }.orEmpty()
            val code = if (soapAction.contains("SetAVTransportURI") && body.contains("https://radio.test/")) {
                rejectedHttps++
                500
            } else 200
            if (soapAction.contains("#Play")) started = true
            val response = if (request.method == "GET") """
                <root><device><serviceList><service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/test/avtransport</controlURL>
                </service></serviceList></device></root>
            """.trimIndent() else "<ok/>"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                .body(response.toResponseBody()).build()
        }.build()
        try {
            val accepted = SoundTouchRepository(client).playDlnaStream("192.0.2.1", "https://radio.test/live?a=1&b=2")
            assertEquals("http://radio.test/live?a=1&b=2", accepted)
            assertEquals(4, rejectedHttps)
            assertTrue(started)
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
