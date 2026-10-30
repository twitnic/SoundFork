package codes.offline.soundfork.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** An interrupted request must never be converted into an ordinary UI error. */
suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> {
    return try {
        currentCoroutineContext().ensureActive()
        val result = block()
        currentCoroutineContext().ensureActive()
        Result.success(result)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (exception: Exception) {
        currentCoroutineContext().ensureActive()
        Result.failure(exception)
    }
}
