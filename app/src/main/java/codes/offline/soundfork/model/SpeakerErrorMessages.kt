package codes.offline.soundfork.model

import android.content.Context
import androidx.annotation.StringRes
import codes.offline.soundfork.R
import codes.offline.soundfork.data.SpeakerFailure
import codes.offline.soundfork.data.SpeakerRequestException

fun Throwable.speakerErrorMessage(context: Context, @StringRes fallback: Int): String {
    val error = this as? SpeakerRequestException ?: return message ?: context.getString(fallback)
    return when (error.failure) {
        SpeakerFailure.TIMEOUT -> context.getString(R.string.speaker_request_timeout)
        SpeakerFailure.UNREACHABLE -> context.getString(R.string.speaker_unreachable)
        SpeakerFailure.HTTP -> context.getString(R.string.speaker_request_rejected, error.httpCode?.toString() ?: "-")
        SpeakerFailure.INVALID_RESPONSE -> context.getString(R.string.speaker_invalid_response)
        SpeakerFailure.NETWORK -> context.getString(R.string.speaker_network_error)
    }
}
