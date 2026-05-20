package ninja.richter.soundfork

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ninja.richter.soundfork.data.DiscoveredSpeaker

fun requiredDiscoveryPermissions(): List<String> = buildList {
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun ManualConnectCard(
    hostInput: String,
    onHostInputChanged: (String) -> Unit,
    onConnect: () -> Unit,
    enabled: Boolean
) {
    val tag = TAG
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Direkt verbinden",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = hostInput,
                onValueChange = onHostInputChanged,
                singleLine = true,
                label = { Text("IP oder Hostname") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        Log.i(tag, "Manual connect IME Done hostInput=$hostInput")
                        onConnect()
                    }
                )
            )
            Button(
                onClick = {
                    Log.i(tag, "Manual connect clicked hostInput=$hostInput")
                    onConnect()
                },
                enabled = enabled
            ) {
                Text("Verbinden")
            }
        }
    }
}

@Composable
fun DiscoveredSpeakerCard(
    speaker: DiscoveredSpeaker,
    onConnect: () -> Unit,
    enabled: Boolean
) {
    val tag = TAG
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
                text = speaker.name ?: "Unbekannter SoundTouch",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${speaker.host}:${speaker.port}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
            if (speaker.type != null || speaker.deviceId != null) {
                Text(
                    text = listOfNotNull(
                        speaker.type?.let { "Typ: $it" },
                        speaker.deviceId?.let { "ID: $it" }
                    ).joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (speaker.methods.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    speaker.methods.forEach { method ->
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(method.name)
                            }
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    Log.d(tag, "Open discovered speaker clicked host=${speaker.host}")
                    onConnect()
                },
                enabled = enabled
            ) {
                Text("Daten auslesen")
            }
        }
    }
}

private const val TAG = "SoundForkUI"
