package codes.offline.soundfork.usecase

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import codes.offline.soundfork.data.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class SoundTouchHttpTransportTest {
    @Test fun cancelledCoroutineCancelsTheUnderlyingHttpCall() = runBlocking {
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            started.countDown()
            while (!chain.call().isCanceled()) Thread.sleep(5)
            cancelled.countDown()
            throw IOException("Cancelled")
        }.build()
        try {
            val transport = SoundTouchHttpTransport(client)
            val job = launch(Dispatchers.Default) { transport.execute(request()) }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(cancelled.await(5, TimeUnit.SECONDS))
            assertTrue(job.isCancelled)
        } finally { client.dispatcher.executorService.shutdown() }
    }

    @Test fun requestsNeverExceedTheConfiguredParallelism() = runBlocking {
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val count = active.incrementAndGet()
            maximum.updateAndGet { maxOf(it, count) }
            started.countDown()
            try {
                check(release.await(5, TimeUnit.SECONDS))
                response(chain.request(), "OK".toResponseBody())
            } finally { active.decrementAndGet() }
        }.build()
        try {
            val transport = SoundTouchHttpTransport(client, parallelRequests = 2)
            val tasks = List(12) { async(Dispatchers.Default) { transport.execute(request()) } }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertEquals(2, maximum.get())
            release.countDown()
            assertTrue(tasks.awaitAll().all { it.code == 200 && it.body == "OK" })
            assertEquals(2, maximum.get())
        } finally {
            release.countDown()
            client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun responseBodyIsClosedAfterReading() = runBlocking {
        val closed = AtomicBoolean(false)
        val source = object : ForwardingSource(Buffer().writeUtf8("<volume>42</volume>")) {
            override fun close() { closed.set(true); super.close() }
        }.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = "text/xml".toMediaType()
            override fun contentLength() = -1L
            override fun source() = source
        }
        val client = OkHttpClient.Builder().addInterceptor { response(it.request(), body) }.build()
        try {
            assertEquals("<volume>42</volume>", SoundTouchHttpTransport(client).execute(request()).body)
            assertTrue(closed.get())
        } finally { client.dispatcher.executorService.shutdown() }
    }

    @Test fun timeoutAndUnreachableKeepTheirOriginalCause() {
        val timeout = SocketTimeoutException("Read timed out")
        val unreachable = ConnectException("Connection refused")
        assertEquals(SpeakerFailure.TIMEOUT, SpeakerRequestException.network("/info", timeout).failure)
        assertEquals(SpeakerFailure.UNREACHABLE, SpeakerRequestException.network("/volume", unreachable).failure)
        assertSame(timeout, SpeakerRequestException.network("/info", timeout).cause)
    }

    private fun request() = Request.Builder().url("http://soundtouch.test/info").build()
    private fun response(request: Request, body: ResponseBody) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
}
