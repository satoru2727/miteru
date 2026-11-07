package io.github.satoru2727.miteru.ui.reservations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.satoru2727.miteru.core.model.RecordPreset
import io.github.satoru2727.miteru.core.model.ReservationItem
import io.github.satoru2727.miteru.core.net.RecordSettingsDto
import io.github.satoru2727.miteru.core.net.ReservationConditionDto
import io.github.satoru2727.miteru.ui.AppViewModel
import io.github.satoru2727.miteru.ui.LoadState
import io.github.satoru2727.miteru.ui.components.ChannelLogo
import io.github.satoru2727.miteru.ui.components.EmptyState
import io.github.satoru2727.miteru.ui.components.ErrorState
import io.github.satoru2727.miteru.ui.util.formatDate
import io.github.satoru2727.miteru.ui.util.formatFileSize
import io.github.satoru2727.miteru.ui.util.formatTime
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class ReservationsMode { RESERVATIONS, CONDITIONS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationsScreen(
    vm: AppViewModel,
    isEdcb: Boolean,
    onOpenGuide: () -> Unit,
) {
    val state = vm.reservationsState
    val conditions = vm.conditionsState
    var mode by remember { mutableStateOf(ReservationsMode.RESERVATIONS) }
    var pendingDelete by remember { mutableStateOf<ReservationItem?>(null) }
    var pendingConditionDelete by remember { mutableStateOf<ReservationConditionDto?>(null) }
    var showAddCondition by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(mode) {
        if (mode == ReservationsMode.CONDITIONS) {
            vm.loadConditions()
            vm.loadRecordPresets()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when (mode) {
                                ReservationsMode.RESERVATIONS -> stringResource(R.string.reservations_title)
                                ReservationsMode.CONDITIONS -> stringResource(R.string.reservations_mode_conditions)
                            },
                        )
                        val count = when (mode) {
                            ReservationsMode.RESERVATIONS -> (state as? LoadState.Success)?.value?.size
                            ReservationsMode.CONDITIONS -> (conditions as? LoadState.Success)?.value?.size
                        }
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
                    IconButton(
                        onClick = {
                            when (mode) {
                                ReservationsMode.RESERVATIONS -> vm.loadReservations(force = true)
                                ReservationsMode.CONDITIONS -> vm.loadConditions(force = true)
                            }
                        },
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            if (mode == ReservationsMode.CONDITIONS && isEdcb) {
                FloatingActionButton(onClick = { showAddCondition = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.conditions_add))
                }
            }
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

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                SegmentedButton(
                    selected = mode == ReservationsMode.RESERVATIONS,
                    onClick = { mode = ReservationsMode.RESERVATIONS },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text(stringResource(R.string.reservations_mode_reservations))
                }
                SegmentedButton(
                    selected = mode == ReservationsMode.CONDITIONS,
                    onClick = { mode = ReservationsMode.CONDITIONS },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text(stringResource(R.string.reservations_mode_conditions))
                }
            }

            when (mode) {
                ReservationsMode.RESERVATIONS -> ReservationsList(
                    state = state,
                    onRetry = { vm.loadReservations(force = true) },
                    onToggle = { id, enabled -> scope.launch { vm.setReservationEnabled(id, enabled) } },
                    onDelete = { pendingDelete = it },
                    onOpenGuide = onOpenGuide,
                    logoUrl = { item -> item.channel?.let { vm.logoUrl(it) } },
                )

                ReservationsMode.CONDITIONS -> ConditionsList(
                    state = conditions,
                    onRetry = { vm.loadConditions(force = true) },
                    onToggle = { condition, enabled -> scope.launch { vm.setConditionEnabled(condition, enabled) } },
                    onDelete = { pendingConditionDelete = it },
                )
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

    pendingConditionDelete?.let { condition ->
        AlertDialog(
            onDismissRequest = { pendingConditionDelete = null },
            title = { Text(stringResource(R.string.conditions_delete_title)) },
            text = { Text(condition.program_search_condition.keyword) },
            confirmButton = {
                TextButton(onClick = {
                    pendingConditionDelete = null
                    scope.launch { vm.deleteCondition(condition.id) }
                }) {
                    Text(stringResource(R.string.reservations_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConditionDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showAddCondition) {
        AddConditionDialog(
            presets = vm.recordPresets,
            onDismiss = { showAddCondition = false },
            onConfirm = { keyword, exclude, note, enabled, settings ->
                vm.addCondition(keyword, exclude, note, enabled, settings)
            },
        )
    }
}

@Composable
private fun ReservationsList(
    state: LoadState<List<ReservationItem>>,
    onRetry: () -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onDelete: (ReservationItem) -> Unit,
    onOpenGuide: () -> Unit,
    logoUrl: (ReservationItem) -> String?,
) {
    when (state) {
        is LoadState.Failure -> ErrorState(
            error = state.error,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )

        LoadState.Loading, LoadState.Idle -> Box(Modifier.fillMaxSize())

        is LoadState.Success -> {
            if (state.value.isEmpty()) {
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
                    items(state.value.sortedBy { it.program?.start?.epochSecond ?: Long.MAX_VALUE }, key = { it.id }) { item ->
                        ReservationCard(
                            item = item,
                            logoUrl = logoUrl(item),
                            onToggle = { enabled -> onToggle(item.id, enabled) },
                            onDelete = { onDelete(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionsList(
    state: LoadState<List<ReservationConditionDto>>,
    onRetry: () -> Unit,
    onToggle: (ReservationConditionDto, Boolean) -> Unit,
    onDelete: (ReservationConditionDto) -> Unit,
) {
    when (state) {
        is LoadState.Failure -> ErrorState(
            error = state.error,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )

        LoadState.Loading, LoadState.Idle -> Box(Modifier.fillMaxSize())

        is LoadState.Success -> {
            if (state.value.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.conditions_empty),
                    message = stringResource(R.string.conditions_empty_hint),
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.value, key = { it.id }) { condition ->
                        ConditionCard(
                            condition = condition,
                            onToggle = { enabled -> onToggle(condition, enabled) },
                            onDelete = { onDelete(condition) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConditionCard(
    condition: ReservationConditionDto,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val search = condition.program_search_condition
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = search.keyword.ifBlank { stringResource(R.string.conditions_keyword) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (search.exclude_keyword.isNotBlank()) {
                        Text(
                            text = "− ${search.exclude_keyword}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (search.note.isNotBlank()) {
                        Text(
                            text = search.note,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Switch(checked = search.is_enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.reservations_delete_confirm),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!search.is_enabled) {
                    StatusChip(stringResource(R.string.conditions_disabled), false)
                }
                StatusChip(stringResource(R.string.conditions_count, condition.reservation_count), search.is_enabled)
                if (condition.record_settings.priority != 3) {
                    Text(
                        text = stringResource(R.string.reservations_priority, condition.record_settings.priority),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddConditionDialog(
    presets: List<RecordPreset>,
    onDismiss: () -> Unit,
    onConfirm: suspend (String, String, String, Boolean, RecordSettingsDto) -> Result<Unit>,
) {
    var keyword by remember { mutableStateOf("") }
    var exclude by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf(presets.firstOrNull()) }
    var priority by remember { mutableStateOf(selectedPreset?.settings?.priority ?: 3) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val requiredMessage = stringResource(R.string.conditions_keyword_required)

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(R.string.conditions_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text(stringResource(R.string.conditions_keyword)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = exclude,
                    onValueChange = { exclude = it },
                    label = { Text(stringResource(R.string.conditions_exclude)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.conditions_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.conditions_enabled),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.reserve_preset),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = selectedPreset == null,
                        onClick = {
                            selectedPreset = null
                            priority = 3
                        },
                        label = { Text(stringResource(R.string.reserve_default_preset)) },
                    )
                    presets.filter { it.id != 0 }.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset?.id == preset.id,
                            onClick = {
                                selectedPreset = preset
                                priority = preset.settings.priority
                            },
                            label = { Text(preset.name) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.reserve_priority, priority),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = priority.toFloat(),
                    onValueChange = { priority = it.roundToInt().coerceIn(1, 5) },
                    valueRange = 1f..5f,
                    steps = 3,
                )

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    if (keyword.isBlank()) {
                        error = requiredMessage
                        return@TextButton
                    }
                    scope.launch {
                        working = true
                        error = null
                        val base = selectedPreset?.settings ?: RecordSettingsDto()
                        val result = onConfirm(keyword.trim(), exclude.trim(), note.trim(), enabled, base.copy(priority = priority))
                        if (result.isSuccess) {
                            onDismiss()
                        } else {
                            error = result.exceptionOrNull()?.message
                            working = false
                        }
                    }
                },
            ) {
                if (working) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.reserve_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !working) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
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
