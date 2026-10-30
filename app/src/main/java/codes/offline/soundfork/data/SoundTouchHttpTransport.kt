package codes.offline.soundfork.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** Includes response-body reading in the concurrency limit and closes every response. */
internal class SoundTouchHttpTransport(private val client: OkHttpClient, parallelRequests: Int = 4) {
    private val permits = Semaphore(parallelRequests)

    suspend fun execute(request: Request): NetworkResult = permits.withPermit {
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(NetworkResult(null, null, e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { NetworkResult(it.code, it.body?.string(), null) }
                    } catch (exception: IOException) {
                        NetworkResult(null, null, exception)
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }
}
