package io.github.satoru2727.miteru.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.core.model.Program
import io.github.satoru2727.miteru.ui.AppViewModel
import io.github.satoru2727.miteru.ui.LoadState
import io.github.satoru2727.miteru.ui.util.formatDate
import io.github.satoru2727.miteru.ui.util.formatTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramSearchSheet(
    vm: AppViewModel,
    isEdcb: Boolean,
    onDismiss: () -> Unit,
) {
    val state = vm.searchState
    val query = vm.searchQuery
    val channels = (vm.channelsState as? LoadState.Success)?.value.orEmpty()
    val scope = rememberCoroutineScope()
    var reserved by remember { mutableStateOf(setOf<String>()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.guide_search),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = vm::onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.guide_search_hint)) },
                    shape = RoundedCornerShape(14.dp),
                )
                IconButton(
                    onClick = { vm.runSearch() },
                    enabled = query.isNotBlank() && isEdcb,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.guide_search_action))
                }
            }

            if (!isEdcb) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.guide_search_edcb_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))
            when (val s = state) {
                LoadState.Idle -> Unit
                LoadState.Loading -> CircularProgressIndicator(
                    modifier = Modifier.padding(vertical = 24.dp).height(28.dp),
                    strokeWidth = 2.dp,
                )

                is LoadState.Failure -> Text(
                    text = stringResource(R.string.guide_search_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp),
                )

                is LoadState.Success -> {
                    if (s.value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.guide_search_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 520.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(s.value, key = { it.id }) { program ->
                                val channel = channels.firstOrNull { it.id == program.channelId }
                                SearchResultRow(
                                    program = program,
                                    channelName = channel?.name,
                                    isEdcb = isEdcb,
                                    alreadyReserved = program.reservation != null || reserved.contains(program.id),
                                    onWatch = channel?.let {
                                        {
                                            vm.requestPlayChannel(it)
                                            onDismiss()
                                        }
                                    },
                                    onReserve = {
                                        scope.launch {
                                            vm.reserve(program).onSuccess {
                                                reserved = reserved + program.id
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    program: Program,
    channelName: String?,
    isEdcb: Boolean,
    alreadyReserved: Boolean,
    onWatch: (() -> Unit)?,
    onReserve: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    channelName,
                    "${formatDate(program.start)} ${formatTime(program.start)}–${formatTime(program.end)}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onWatch != null && program.isOnAir) {
                    TextButton(onClick = onWatch) {
                        Text(stringResource(R.string.guide_watch_now))
                    }
                }
                if (isEdcb) {
                    TextButton(onClick = onReserve, enabled = !alreadyReserved) {
                        Text(
                            if (alreadyReserved) {
                                stringResource(R.string.guide_reserved)
                            } else {
                                stringResource(R.string.guide_reserve)
                            },
                        )
                    }
                }
            }
        }
    }
}
