package ninja.richter.soundfork

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ninja.richter.soundfork.data.DiscoveredSpeaker
import ninja.richter.soundfork.data.SourceItem
import ninja.richter.soundfork.data.ZoneMember
import ninja.richter.soundfork.data.ZoneState

@Composable
fun SourceListCard(
    sources: List<SourceItem>,
    currentSource: SourceItem?,
    status: String?,
    isSelecting: Boolean,
    onSelectSource: (SourceItem) -> Unit
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
                text = stringResource(R.string.sources),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            currentSource?.let { source ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.current_source),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = source.displayName(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        source.sourceAccount?.takeIf { it.isNotBlank() }?.let { account ->
                            Text(
                                text = stringResource(R.string.account_prefix, account),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            if (sources.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_sources),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sources.forEach { source ->
                    val selected = source.matches(currentSource)
                    val selectable = source.isSelectable()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = !isSelecting && selectable,
                                onClick = { onSelectSource(source) }
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelectSource(source) },
                            enabled = !isSelecting && selectable
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = source.displayName(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                text = listOfNotNull(
                                    source.sourceAccount?.let { "Account: $it" },
                                    source.status?.let { stringResource(R.string.status_prefix, it) }
                                ).joinToString(" | ").ifBlank { source.source },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectable) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
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
fun ZoneInfoCard(
    zoneState: ZoneState?,
    zoneXml: String?,
    discoveredSpeakers: List<DiscoveredSpeaker>,
    selectedHost: String?,
    isUpdating: Boolean,
    status: String?,
    onSetZoneWithSpeaker: (DiscoveredSpeaker) -> Unit,
    onAddZoneSlave: (DiscoveredSpeaker) -> Unit,
    onRemoveZoneSlave: (ZoneMember) -> Unit
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
                text = stringResource(R.string.nav_zones),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (zoneState == null) {
                Text(
                    text = stringResource(R.string.no_zone_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ZoneSummary(
                    zoneState = zoneState,
                    selectedHost = selectedHost,
                    isUpdating = isUpdating,
                    onRemoveZoneSlave = onRemoveZoneSlave
                )
            }
            ZoneActions(
                discoveredSpeakers = discoveredSpeakers,
                selectedHost = selectedHost,
                isUpdating = isUpdating,
                onSetZoneWithSpeaker = onSetZoneWithSpeaker,
                onAddZoneSlave = onAddZoneSlave
            )
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
            Text(
                text = zoneXml ?: stringResource(R.string.no_zone_xml),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 0.dp, max = 1_200.dp)
                    .verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ZoneSummary(
    zoneState: ZoneState,
    selectedHost: String?,
    isUpdating: Boolean,
    onRemoveZoneSlave: (ZoneMember) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = stringResource(R.string.zone_master),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = zoneState.masterDeviceId ?: stringResource(R.string.not_reported),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
                zoneState.senderIpAddress?.let { ip ->
                    Text(
                        text = stringResource(R.string.sender_ip, ip),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (zoneState.members.isEmpty()) {
            Text(
                text = stringResource(R.string.no_zone_members),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            zoneState.members.forEach { member ->
                val canRemove = member.ipAddress != selectedHost &&
                    !member.deviceId.isNullOrBlank() &&
                    !member.ipAddress.isNullOrBlank()
                ZoneMemberRow(
                    member = member,
                    isUpdating = isUpdating,
                    onRemove = if (canRemove) {
                        { onRemoveZoneSlave(member) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
fun ZoneActions(
    discoveredSpeakers: List<DiscoveredSpeaker>,
    selectedHost: String?,
    isUpdating: Boolean,
    onSetZoneWithSpeaker: (DiscoveredSpeaker) -> Unit,
    onAddZoneSlave: (DiscoveredSpeaker) -> Unit
) {
    val candidates = remember(discoveredSpeakers, selectedHost) {
        discoveredSpeakers
            .filter { speaker ->
                speaker.host != selectedHost &&
                    !speaker.deviceId.isNullOrBlank() &&
                    !speaker.host.isBlank()
            }
            .distinctBy { it.deviceId ?: it.host }
    }
    HorizontalDivider()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.edit_zone),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.edit_zone_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (candidates.isEmpty()) {
            Text(
                text = stringResource(R.string.no_zone_candidates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            candidates.forEach { speaker ->
                ZoneCandidateRow(
                    speaker = speaker,
                    isUpdating = isUpdating,
                    onSetZoneWithSpeaker = { onSetZoneWithSpeaker(speaker) },
                    onAddZoneSlave = { onAddZoneSlave(speaker) }
                )
            }
        }
    }
}

@Composable
fun ZoneCandidateRow(
    speaker: DiscoveredSpeaker,
    isUpdating: Boolean,
    onSetZoneWithSpeaker: () -> Unit,
    onAddZoneSlave: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = speaker.name ?: speaker.type ?: speaker.host,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = listOfNotNull(
                    speaker.type?.takeIf { it.isNotBlank() },
                    speaker.host,
                    speaker.deviceId?.takeIf { it.isNotBlank() }
                ).joinToString(" | "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onSetZoneWithSpeaker,
                    enabled = !isUpdating
                ) {
                    Text(stringResource(R.string.create_zone))
                }
                OutlinedButton(
                    onClick = onAddZoneSlave,
                    enabled = !isUpdating
                ) {
                    Text(stringResource(R.string.add))
                }
            }
        }
    }
}

@Composable
fun ZoneMemberRow(
    member: ZoneMember,
    isUpdating: Boolean,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = member.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "Z",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = member.name ?: member.deviceId ?: stringResource(R.string.zone_member),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = listOfNotNull(
                    member.role?.takeIf { it.isNotBlank() },
                    member.ipAddress?.takeIf { it.isNotBlank() },
                    member.deviceId?.takeIf { it.isNotBlank() }
                ).joinToString(" | ").ifBlank { stringResource(R.string.no_details) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onRemove != null) {
            TextButton(
                onClick = onRemove,
                enabled = !isUpdating
            ) {
                Text(stringResource(R.string.remove))
            }
        }
    }
}

fun SourceItem.displayName(): String {
    return name?.takeIf { it.isNotBlank() } ?: source
}

fun SourceItem.matches(other: SourceItem?): Boolean {
    if (other == null) {
        return false
    }
    return source.equals(other.source, ignoreCase = true) &&
        sourceAccount.orEmpty().equals(other.sourceAccount.orEmpty(), ignoreCase = true)
}

fun SourceItem.isSelectable(): Boolean {
    val normalizedStatus = status.orEmpty().trim().uppercase()
    return normalizedStatus.isBlank() ||
        normalizedStatus == "READY" ||
        normalizedStatus == "SELECTABLE" ||
        normalizedStatus == "ACTIVE" ||
        normalizedStatus == "AVAILABLE"
}
