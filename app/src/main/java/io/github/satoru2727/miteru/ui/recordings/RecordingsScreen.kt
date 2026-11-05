package io.github.satoru2727.miteru.ui.recordings

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.compose.AsyncImage
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.ui.AppViewModel
import io.github.satoru2727.miteru.ui.LoadState
import io.github.satoru2727.miteru.ui.LocalAppImageLoader
import io.github.satoru2727.miteru.ui.RecordingFilter
import io.github.satoru2727.miteru.ui.components.EmptyState
import io.github.satoru2727.miteru.ui.components.ErrorState
import io.github.satoru2727.miteru.ui.components.SkeletonCard
import io.github.satoru2727.miteru.ui.util.formatClock
import io.github.satoru2727.miteru.ui.util.formatDateTime
import io.github.satoru2727.miteru.ui.util.formatFileSize

@Composable
fun RecordingsScreen(vm: AppViewModel) {
    val state = vm.recordingsState
    val query = vm.recordingsQuery
    val filter = vm.recordingsFilter
    val order = vm.recordingsOrder
    val positions = vm.resumePositions
    val loadingMore = vm.loadingMore
    val listState = rememberLazyListState()

    var sortMenu by remember { mutableStateOf(false) }

    val all = (state as? LoadState.Success)?.value.orEmpty()
    val visible = remember(all, filter) {
        all.filter { recording ->
            when (filter) {
                RecordingFilter.ALL -> true
                RecordingFilter.TS -> recording.video.container == "MPEG-TS"
                RecordingFilter.MP4 -> recording.video.container == "MPEG-4"
            }
        }
    }

    LaunchedEffect(listState, visible.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (visible.isNotEmpty() && last >= visible.size - 3) {
                    vm.loadMoreRecordings()
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.recordings_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = stringResource(R.string.recordings_count, all.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Rounded.Sort, contentDescription = stringResource(R.string.recordings_sort))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recordings_sort_new)) },
                        onClick = {
                            sortMenu = false
                            vm.changeRecordingsOrder("desc")
                        },
                        trailingIcon = { if (order == "desc") Text("✓") },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recordings_sort_old)) },
                        onClick = {
                            sortMenu = false
                            vm.changeRecordingsOrder("asc")
                        },
                        trailingIcon = { if (order == "asc") Text("✓") },
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = vm::onRecordingsQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            placeholder = { Text(stringResource(R.string.recordings_search), style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.onRecordingsQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            ),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(RecordingFilter.entries.toList()) { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { vm.changeRecordingsFilter(item) },
                    label = {
                        Text(
                            when (item) {
                                RecordingFilter.ALL -> stringResource(R.string.recordings_all)
                                RecordingFilter.TS -> stringResource(R.string.recordings_ts)
                                RecordingFilter.MP4 -> stringResource(R.string.recordings_mp4)
                            },
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                state is LoadState.Failure -> ErrorState(
                    error = state.error,
                    onRetry = { vm.loadRecordings() },
                    modifier = Modifier.fillMaxSize(),
                )

                all.isEmpty() && (state is LoadState.Loading || state is LoadState.Idle) -> {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        repeat(5) { SkeletonCard(height = 96.dp) }
                    }
                }

                all.isEmpty() -> EmptyState(
                    title = if (query.isBlank()) stringResource(R.string.recordings_empty) else stringResource(R.string.recordings_no_match),
                    message = stringResource(R.string.recordings_empty_hint),
                    modifier = Modifier.fillMaxSize(),
                    icon = {
                        Icon(
                            Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp),
                        )
                    },
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { recording ->
                        RecordingCard(
                            recording = recording,
                            thumbnailUrl = vm.thumbnailUrl(recording.id),
                            resumeMs = positions[recording.id] ?: 0L,
                            onClick = { vm.requestPlayRecording(recording) },
                        )
                    }
                    if (loadingMore) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(
    recording: Recording,
    thumbnailUrl: String?,
    resumeMs: Long,
    onClick: () -> Unit,
) {
    val durationMs = recording.durationSeconds * 1000
    val resumeProgress = if (durationMs > 0 && resumeMs > 0) (resumeMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .width(132.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ),
                    ),
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    val context = LocalContext.current
                    val request = remember(thumbnailUrl, context) {
                        ImageRequest.Builder(context.applicationContext).data(thumbnailUrl).build()
                    }
                    var loaded by remember(thumbnailUrl) { mutableStateOf(false) }
                    if (!loaded) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    AsyncImage(
                        model = request,
                        imageLoader = LocalAppImageLoader.current,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onState = { state ->
                            loaded = state is AsyncImagePainter.State.Success
                        },
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                ) {
                    Text(
                        text = formatClock(durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                if (resumeProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { resumeProgress },
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = recording.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(recording.channelName, formatDateTime(recording.start)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaChip(recording.video.container)
                    if (recording.video.width > 0) {
                        MetaChip("${recording.video.height}p")
                    }
                    MetaChip(formatFileSize(recording.video.fileSize))
                }
                if (resumeMs > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.recordings_resume, formatClock(resumeMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
