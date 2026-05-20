package ninja.richter.soundfork

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ninja.richter.soundfork.data.EndpointResult

@Composable
fun DlnaStreamCard(
    streamUrl: String,
    status: String?,
    isStarting: Boolean,
    onStreamUrlChanged: (String) -> Unit,
    onPlay: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "DLNA Stream",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = streamUrl,
                onValueChange = onStreamUrlChanged,
                singleLine = true,
                label = { Text("Stream-URL") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onPlay() }
                )
            )
            Button(
                onClick = onPlay,
                enabled = !isStarting
            ) {
                Text(if (isStarting) "Starte..." else "Stream starten")
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isErrorStatus(status)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

@Composable
fun ApiTestCard(
    endpointInput: String,
    result: EndpointResult?,
    status: String?,
    isTesting: Boolean,
    expanded: Boolean,
    onEndpointInputChanged: (String) -> Unit,
    onTestEndpoint: () -> Unit,
    onToggleExpand: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "API-Test",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Teste einzelne SoundTouch-Endpunkte direkt am verbundenen Lautsprecher, z. B. /now_playing, /volume oder /presets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = endpointInput,
                onValueChange = onEndpointInputChanged,
                singleLine = true,
                label = { Text("API-Pfad") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onTestEndpoint() })
            )
            FilledTonalButton(
                onClick = onTestEndpoint,
                enabled = !isTesting
            ) {
                Text(if (isTesting) "Teste..." else "Endpunkt testen")
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isErrorStatus(status)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            if (result != null) {
                EndpointResultCard(
                    result = result,
                    expanded = expanded,
                    onToggleExpand = onToggleExpand
                )
            }
        }
    }
}

@Composable
fun ReportCard(
    enabled: Boolean,
    onSendReport: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Bericht",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Erstellt eine Textdatei mit allen aktuell ausgelesenen Lautsprecher-Antworten und haengt sie an eine Mail an.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onSendReport,
                enabled = enabled
            ) {
                Text("Bericht per Mail senden")
            }
        }
    }
}

fun isErrorStatus(status: String): Boolean {
    return status.contains("konnte", ignoreCase = true) ||
        status.contains("HTTP", ignoreCase = true) ||
        status.contains("Fehler", ignoreCase = true)
}

@Composable
fun EndpointResultCard(
    result: EndpointResult,
    expanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val resolvedPath = result.resolvedPath ?: result.requestedAliases.firstOrNull().orEmpty()
    val statusText = when {
        result.httpCode != null -> "HTTP ${result.httpCode}"
        result.errorMessage != null -> "Fehler"
        else -> "Unbekannt"
    }
    val statusColor = when {
        result.isError -> MaterialTheme.colorScheme.error
        result.httpCode in 200..299 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    val body = result.body ?: result.errorMessage ?: "Keine Daten"
    val canCollapse = body.length > MAX_BODY_PREVIEW_LENGTH
    val shownBody = if (canCollapse && !expanded) {
        body.take(MAX_BODY_PREVIEW_LENGTH) + "\n..."
    } else {
        body
    }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = resolvedPath,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.Medium
            )
            HorizontalDivider()
            Text(
                text = shownBody,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 0.dp, max = if (expanded) 8_000.dp else 400.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
            if (canCollapse) {
                TextButton(onClick = onToggleExpand) {
                    Text(if (expanded) "Einklappen" else "Mehr anzeigen")
                }
            }
        }
    }
}

fun resultKey(result: EndpointResult): String {
    return listOf(
        result.title,
        result.resolvedPath,
        result.requestedAliases.joinToString("|")
    ).joinToString("#")
}

private const val MAX_BODY_PREVIEW_LENGTH = 2_000
