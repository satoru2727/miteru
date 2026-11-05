package io.github.satoru2727.miteru.ui.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.core.model.ReservationItem
import io.github.satoru2727.miteru.ui.AppViewModel
import io.github.satoru2727.miteru.ui.LoadState
import io.github.satoru2727.miteru.ui.components.ChannelLogo
import io.github.satoru2727.miteru.ui.components.EmptyState
import io.github.satoru2727.miteru.ui.components.ErrorState
import io.github.satoru2727.miteru.ui.util.formatDate
import io.github.satoru2727.miteru.ui.util.formatFileSize
import io.github.satoru2727.miteru.ui.util.formatTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationsScreen(
    vm: AppViewModel,
    isEdcb: Boolean,
    onOpenGuide: () -> Unit,
) {
    val state = vm.reservationsState
    var pendingDelete by remember { mutableStateOf<ReservationItem?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.reservations_title))
                        val count = (state as? LoadState.Success)?.value?.size
                        if (count != null) {
                            Text(
                                text = stringResource(R.string.reservations_count, count),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadReservations(force = true) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!isEdcb) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.reservations_edcb_only),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            when (val s = state) {
                is LoadState.Failure -> ErrorState(
                    error = s.error,
                    onRetry = { vm.loadReservations(force = true) },
                    modifier = Modifier.fillMaxSize(),
                )

                LoadState.Loading, LoadState.Idle -> Box(Modifier.fillMaxSize())

                is LoadState.Success -> {
                    if (s.value.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.reservations_empty),
                            message = stringResource(R.string.reservations_empty_hint),
                            modifier = Modifier.fillMaxSize(),
                            actionLabel = stringResource(R.string.reservations_open_guide),
                            onAction = onOpenGuide,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(s.value.sortedBy { it.program?.start?.epochSecond ?: Long.MAX_VALUE }, key = { it.id }) { item ->
                                ReservationCard(
                                    item = item,
                                    logoUrl = item.channel?.let { vm.logoUrl(it) },
                                    onToggle = { enabled ->
                                        scope.launch { vm.setReservationEnabled(item.id, enabled) }
                                    },
                                    onDelete = { pendingDelete = item },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.reservations_delete_title)) },
            text = { Text(item.program?.title ?: stringResource(R.string.reservations_title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { vm.cancelReservation(item.id) }
                }) {
                    Text(stringResource(R.string.reservations_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReservationCard(
    item: ReservationItem,
    logoUrl: String?,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val program = item.program
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(url = logoUrl, size = 42.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.channel?.name ?: program?.channelId.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = program?.let {
                            "${formatDate(it.start)} ${formatTime(it.start)}–${formatTime(it.end)}"
                        } ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = item.isEnabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.reservations_delete_confirm),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = program?.title ?: stringResource(R.string.reservations_unknown_program),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    item.isRecording -> StatusChip(stringResource(R.string.reservations_recording), true)
                    item.isUnavailable -> StatusChip(stringResource(R.string.reservations_unavailable), false)
                    item.isPartial -> StatusChip(stringResource(R.string.reservations_partial), false)
                    !item.isEnabled -> StatusChip(stringResource(R.string.reservations_disabled), false)
                    else -> StatusChip(stringResource(R.string.reservations_reserved), false)
                }
                if (item.priority != 3) {
                    Text(
                        text = stringResource(R.string.reservations_priority, item.priority),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (item.estimatedFileSize > 0) {
                    Text(
                        text = formatFileSize(item.estimatedFileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, active: Boolean) {
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = if (active) {
            { Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.width(16.dp)) }
        } else {
            null
        },
        modifier = Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)),
    )
}
