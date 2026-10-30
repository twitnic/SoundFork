package codes.offline.soundfork.data

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

enum class SpeakerFailure { TIMEOUT, UNREACHABLE, HTTP, INVALID_RESPONSE, NETWORK }

class SpeakerRequestException(
    val failure: SpeakerFailure,
    val endpoint: String,
    val httpCode: Int? = null,
    cause: IOException? = null
) : IOException("$failure at $endpoint${httpCode?.let { " (HTTP $it)" }.orEmpty()}", cause) {
    companion object {
        fun network(endpoint: String, cause: IOException): SpeakerRequestException {
            if (cause is SpeakerRequestException) return cause
            val failure = when (cause) {
                is InterruptedIOException -> SpeakerFailure.TIMEOUT
                is ConnectException, is NoRouteToHostException, is UnknownHostException -> SpeakerFailure.UNREACHABLE
                else -> SpeakerFailure.NETWORK
            }
            return SpeakerRequestException(failure, endpoint, cause = cause)
        }
    }
}
