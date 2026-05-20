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
import androidx.compose.ui.res.stringResource
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
                text = stringResource(R.string.dlna_stream),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = streamUrl,
                onValueChange = onStreamUrlChanged,
                singleLine = true,
                label = { Text(stringResource(R.string.stream_url)) },
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
                Text(if (isStarting) stringResource(R.string.starting) else stringResource(R.string.start_stream))
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
                text = stringResource(R.string.api_test),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.api_test_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = endpointInput,
                onValueChange = onEndpointInputChanged,
                singleLine = true,
                label = { Text(stringResource(R.string.api_path)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onTestEndpoint() })
            )
            FilledTonalButton(
                onClick = onTestEndpoint,
                enabled = !isTesting
            ) {
                Text(if (isTesting) stringResource(R.string.testing) else stringResource(R.string.test_endpoint))
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
                text = stringResource(R.string.report),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.report_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onSendReport,
                enabled = enabled
            ) {
                Text(stringResource(R.string.send_report_mail))
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
        result.errorMessage != null -> stringResource(R.string.error)
        else -> stringResource(R.string.unknown)
    }
    val statusColor = when {
        result.isError -> MaterialTheme.colorScheme.error
        result.httpCode in 200..299 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    val body = result.body ?: result.errorMessage ?: stringResource(R.string.no_data)
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
                    Text(if (expanded) stringResource(R.string.collapse) else stringResource(R.string.show_more))
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
