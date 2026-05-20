package ninja.richter.soundfork.report

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ninja.richter.soundfork.R
import ninja.richter.soundfork.model.SoundForkUiState

fun shareSpeakerReport(
    context: Context,
    state: SoundForkUiState
) {
    if (state.endpointResults.isEmpty()) {
        Toast.makeText(context, "Noch keine Lautsprecher-Antworten fuer den Bericht vorhanden.", Toast.LENGTH_LONG)
            .show()
        return
    }

    val reportFile = runCatching { writeSpeakerReport(context, state) }
        .onFailure { throwable ->
            Log.w(TAG, "shareSpeakerReport() failed to write report error=${throwable.message}", throwable)
            Toast.makeText(context, "Bericht konnte nicht erstellt werden.", Toast.LENGTH_LONG).show()
        }
        .getOrNull()
        ?: return

    val reportUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        reportFile
    )
    val subject = buildString {
        append("SoundFork Bericht")
        state.selectedSummary?.name?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
    }
    val message = buildString {
        appendLine("SoundFork Bericht im Anhang.")
        appendLine("Host: ${state.selectedHost ?: "unbekannt"}:${state.selectedPort}")
    }
    val recipientEmail = context.getString(R.string.report_recipient_email)

    val gmailIntent = buildReportEmailIntent(
        context = context,
        recipientEmail = recipientEmail,
        subject = subject,
        message = message,
        reportFileName = reportFile.name,
        reportUri = reportUri
    ).apply {
        setPackage(GMAIL_PACKAGE)
    }

    runCatching {
        context.startActivity(gmailIntent)
    }.onFailure { throwable ->
        if (throwable is ActivityNotFoundException) {
            val fallbackIntent = buildReportEmailIntent(
                context = context,
                recipientEmail = recipientEmail,
                subject = subject,
                message = message,
                reportFileName = reportFile.name,
                reportUri = reportUri
            )
            runCatching {
                context.startActivity(Intent.createChooser(fallbackIntent, "Bericht per E-Mail senden"))
            }.onFailure { fallbackThrowable ->
                Log.w(
                    TAG,
                    "shareSpeakerReport() failed to start email fallback error=${fallbackThrowable.message}",
                    fallbackThrowable
                )
                Toast.makeText(context, "Keine E-Mail-App gefunden.", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.w(TAG, "shareSpeakerReport() failed to start Gmail intent error=${throwable.message}", throwable)
            Toast.makeText(context, "Bericht konnte nicht an Gmail uebergeben werden.", Toast.LENGTH_LONG).show()
        }
    }
}

private fun buildReportEmailIntent(
    context: Context,
    recipientEmail: String,
    subject: String,
    message: String,
    reportFileName: String,
    reportUri: android.net.Uri
): Intent {
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, message)
        putExtra(Intent.EXTRA_STREAM, reportUri)
        clipData = ClipData.newUri(context.contentResolver, reportFileName, reportUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun writeSpeakerReport(
    context: Context,
    state: SoundForkUiState
): File {
    val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    val safeHost = (state.selectedHost ?: "unknown")
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
    val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
    val reportFile = File(reportsDir, "soundfork-report-${safeHost}-$timestamp.txt")
    reportFile.writeText(buildSpeakerReportText(context, state), Charsets.UTF_8)
    return reportFile
}

private fun buildSpeakerReportText(
    context: Context,
    state: SoundForkUiState
): String {
    val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
    return buildString {
        appendLine("SoundFork Lautsprecher-Bericht")
        appendLine("Erstellt: $generatedAt")
        appendLine("App-Version: ${appVersionText(context)}")
        appendLine()
        appendLine("Geraet")
        appendLine("Name: ${state.selectedSummary?.name ?: "-"}")
        appendLine("Typ: ${state.selectedSummary?.type ?: "-"}")
        appendLine("Device ID: ${state.selectedSummary?.deviceId ?: "-"}")
        appendLine("Host: ${state.selectedHost ?: "-"}:${state.selectedPort}")
        appendLine()
        appendLine("Volume")
        appendLine("Target: ${state.selectedVolume?.targetVolume ?: "-"}")
        appendLine("Actual: ${state.selectedVolume?.actualVolume ?: "-"}")
        appendLine("Mute: ${state.selectedVolume?.muteEnabled ?: "-"}")
        appendLine()
        appendLine("Bass")
        appendLine("Target: ${state.selectedBass?.targetBass ?: "-"}")
        appendLine("Actual: ${state.selectedBass?.actualBass ?: "-"}")
        appendLine()
        appendLine("Now Playing")
        appendLine("Source: ${state.nowPlaying?.source ?: "-"}")
        appendLine("Source Account: ${state.nowPlaying?.sourceAccount ?: "-"}")
        appendLine("Location: ${state.nowPlaying?.location ?: "-"}")
        appendLine("Track: ${state.nowPlaying?.track ?: "-"}")
        appendLine("Artist: ${state.nowPlaying?.artist ?: "-"}")
        appendLine("Station: ${state.nowPlaying?.stationName ?: "-"}")
        appendLine("Play Status: ${state.nowPlaying?.playStatus ?: "-"}")
        appendLine("Stream Type: ${state.nowPlaying?.streamType ?: "-"}")
        appendLine("Art URL: ${state.nowPlaying?.artUrl ?: "-"}")
        appendLine()
        appendLine("Quellen")
        appendLine("Aktuell: ${state.currentSource?.source ?: "-"} ${state.currentSource?.sourceAccount ?: ""}".trim())
        state.sources.forEachIndexed { index, source ->
            appendLine("${index + 1}. ${source.source} account=${source.sourceAccount ?: "-"} name=${source.name ?: "-"} status=${source.status ?: "-"}")
        }
        appendLine()
        appendLine("Presets")
        state.presets.forEachIndexed { index, preset ->
            appendLine("${index + 1}. id=${preset.id ?: "-"} name=${preset.name ?: "-"} source=${preset.source ?: "-"} account=${preset.sourceAccount ?: "-"} location=${preset.location ?: "-"} presetable=${preset.isPresetable ?: "-"}")
        }
        appendLine()
        appendLine("Zonen")
        appendLine("Master: ${state.zoneState?.masterDeviceId ?: "-"}")
        appendLine("Sender-IP: ${state.zoneState?.senderIpAddress ?: "-"}")
        state.zoneState?.members.orEmpty().forEachIndexed { index, member ->
            appendLine("${index + 1}. deviceId=${member.deviceId ?: "-"} ip=${member.ipAddress ?: "-"} name=${member.name ?: "-"} role=${member.role ?: "-"}")
        }
        appendLine()
        state.debugEndpointResult?.let { result ->
            appendLine("Letzter API-Test")
            appendLine("- Requested aliases: ${result.requestedAliases.joinToString(", ")}")
            appendLine("- Resolved path: ${result.resolvedPath ?: "-"}")
            appendLine("- HTTP code: ${result.httpCode ?: "-"}")
            appendLine("- Error: ${result.errorMessage ?: "-"}")
            appendLine("- Body:")
            appendLine(result.body ?: "-")
            appendLine()
        }
        appendLine("Endpoint-Antworten")
        appendLine("==================")
        state.endpointResults.forEachIndexed { index, result ->
            appendLine()
            appendLine("${index + 1}. ${result.title}")
            appendLine("- Requested aliases: ${result.requestedAliases.joinToString(", ")}")
            appendLine("- Resolved path: ${result.resolvedPath ?: "-"}")
            appendLine("- HTTP code: ${result.httpCode ?: "-"}")
            appendLine("- Error: ${result.errorMessage ?: "-"}")
            appendLine("- Body:")
            appendLine(result.body ?: "-")
            appendLine("------------------------------------------------------------")
        }
    }
}

private fun appVersionText(context: Context): String {
    return runCatching {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        "${packageInfo.versionName ?: "unbekannt"} (${packageInfo.longVersionCode})"
    }.getOrElse {
        "unbekannt"
    }
}

private const val TAG = "SpeakerReport"
private const val GMAIL_PACKAGE = "com.google.android.gm"
