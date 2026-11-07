package io.github.satoru2727.miteru.ui.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.satoru2727.miteru.core.model.Quality
import io.github.satoru2727.miteru.core.model.Recording
import io.github.satoru2727.miteru.core.model.Series
import io.github.satoru2727.miteru.data.OfflineItem
import io.github.satoru2727.miteru.data.OfflineStatus
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

private enum class RecordingsView { RECORDINGS, SERIES, OFFLINE }

@Composable
fun RecordingsScreen(vm: AppViewModel) {
    val state = vm.recordingsState
    val query = vm.recordingsQuery
    val filter = vm.recordingsFilter
    val order = vm.recordingsOrder
    val positions = vm.resumePositions
    val loadingMore = vm.loadingMore
    val watchedIds by vm.watchedIds.collectAsStateWithLifecycle()
    val unwatchedOnly = vm.recordingsUnwatchedOnly
    val genreFilter = vm.recordingsGenre
    val capped = vm.recordingsCapped
    val seriesState = vm.seriesState
    val seriesQuery = vm.seriesQuery
    val loadingMoreSeries = vm.loadingMoreSeries
    val listState = rememberLazyListState()
    val seriesListState = rememberLazyListState()

    var view by remember { mutableStateOf(RecordingsView.RECORDINGS) }
    var sortMenu by remember { mutableStateOf(false) }
    var genreMenu by remember { mutableStateOf(false) }
    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var actionRecording by remember { mutableStateOf<Recording?>(null) }
    var pendingOfflineDelete by remember { mutableStateOf<OfflineItem?>(null) }

    val all = (state as? LoadState.Success)?.value.orEmpty()
    val filtering = unwatchedOnly || genreFilter != null
    val availableGenres = remember(all) {
        all.flatMap { it.genres }
            .map { it.major }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }
    val visible = remember(all, filter, watchedIds, unwatchedOnly, genreFilter) {
        all.filter { recording ->
            val matchesContainer = when (filter) {
                RecordingFilter.ALL -> true
                RecordingFilter.TS -> recording.video.container == "MPEG-TS"
                RecordingFilter.MP4 -> recording.video.container == "MPEG-4"
            }
            val matchesUnwatched = !unwatchedOnly || recording.id !in watchedIds
            val matchesGenre = genreFilter == null || recording.genres.any { it.major == genreFilter }
            matchesContainer && matchesUnwatched && matchesGenre
        }
    }
    val seriesList = (seriesState as? LoadState.Success)?.value.orEmpty()
    val offlineItems by vm.offlineItems.collectAsStateWithLifecycle()
    val offlineById = remember(offlineItems) { offlineItems.associateBy { it.videoId } }
    val settings by vm.settings.collectAsStateWithLifecycle()

    // 未視聴・ジャンルフィルタは全件取得してから行う
    LaunchedEffect(filtering, all.size) {
        if (filtering) vm.ensureAllRecordings()
    }

    LaunchedEffect(view) {
        if (view == RecordingsView.SERIES && seriesState is LoadState.Idle) vm.loadSeries()
    }

    LaunchedEffect(listState, visible.size, filtering) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (!filtering && visible.isNotEmpty() && last >= visible.size - 3) {
                    vm.loadMoreRecordings()
                }
            }
    }

    LaunchedEffect(seriesListState, seriesList.size) {
        snapshotFlow { seriesListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (seriesList.isNotEmpty() && last >= seriesList.size - 3) {
                    vm.loadMoreSeries()
                }
            }
    }

    val currentSeries = selectedSeries
    if (view == RecordingsView.SERIES && currentSeries != null) {
        BackHandler { selectedSeries = null }
        SeriesDetail(
            series = currentSeries,
            positions = positions,
            watchedIds = watchedIds,
            onBack = { selectedSeries = null },
            onPlay = { vm.requestPlayRecording(it) },
        )
        return
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
            if (view == RecordingsView.RECORDINGS) {
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.recordings_sort))
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
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            SegmentedButton(
                selected = view == RecordingsView.RECORDINGS,
                onClick = { view = RecordingsView.RECORDINGS },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
            ) {
                Text(stringResource(R.string.recordings_view_recordings))
            }
            SegmentedButton(
                selected = view == RecordingsView.SERIES,
                onClick = { view = RecordingsView.SERIES },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
            ) {
                Text(stringResource(R.string.recordings_view_series))
            }
            SegmentedButton(
                selected = view == RecordingsView.OFFLINE,
                onClick = { view = RecordingsView.OFFLINE },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
            ) {
                Text(stringResource(R.string.offline_title))
            }
        }

        when (view) {
            RecordingsView.RECORDINGS -> RecordingsList(
                query = query,
                onQueryChange = vm::onRecordingsQueryChange,
                filter = filter,
                onFilterChange = vm::changeRecordingsFilter,
                unwatchedOnly = unwatchedOnly,
                onUnwatchedChange = vm::changeRecordingsUnwatchedOnly,
                genreFilter = genreFilter,
                onGenreChange = vm::changeRecordingsGenre,
                genreMenuOpen = genreMenu,
                onGenreMenuChange = { genreMenu = it },
                availableGenres = availableGenres,
                state = state,
                visible = visible,
                positions = positions,
                loadingMore = loadingMore,
                capped = capped,
                filtering = filtering,
                listState = listState,
                onRetry = { vm.loadRecordings() },
                onPlay = { vm.requestPlayRecording(it) },
                thumbnailUrl = { vm.thumbnailUrl(it) },
                offlineById = offlineById,
                onLongPress = { actionRecording = it },
            )

            RecordingsView.SERIES -> SeriesList(
                query = seriesQuery,
                onQueryChange = vm::onSeriesQueryChange,
                state = seriesState,
                seriesList = seriesList,
                loadingMore = loadingMoreSeries,
                listState = seriesListState,
                onRetry = { vm.loadSeries() },
                onOpenSeries = { selectedSeries = it },
            )

            RecordingsView.OFFLINE -> OfflineList(
                items = offlineItems,
                onPlay = vm::playOfflineItem,
                onCancel = vm::cancelDownload,
                onDelete = { pendingOfflineDelete = it },
                onRetry = vm::retryDownload,
            )
        }
    }

    actionRecording?.let { recording ->
        RecordingActionSheet(
            recording = recording,
            offlineItem = offlineById[recording.id],
            defaultQuality = settings?.defaultQuality ?: Quality.Q720P,
            onDismiss = { actionRecording = null },
            onDownload = { quality ->
                vm.downloadRecording(recording, quality)
                actionRecording = null
            },
            onCancelDownload = { vm.cancelDownload(recording.id) },
            onDeleteDownload = { vm.deleteDownload(recording.id) },
            onPlayOffline = {
                offlineById[recording.id]?.let(vm::playOfflineItem)
                actionRecording = null
            },
        )
    }

    pendingOfflineDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingOfflineDelete = null },
            title = { Text(stringResource(R.string.offline_delete_title)) },
            text = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = {
                    pendingOfflineDelete = null
                    vm.deleteDownload(item.videoId)
                }) {
                    Text(stringResource(R.string.offline_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingOfflineDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ColumnScope.RecordingsList(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: RecordingFilter,
    onFilterChange: (RecordingFilter) -> Unit,
    unwatchedOnly: Boolean,
    onUnwatchedChange: (Boolean) -> Unit,
    genreFilter: String?,
    onGenreChange: (String?) -> Unit,
    genreMenuOpen: Boolean,
    onGenreMenuChange: (Boolean) -> Unit,
    availableGenres: List<String>,
    state: LoadState<List<Recording>>,
    visible: List<Recording>,
    positions: Map<Int, Long>,
    loadingMore: Boolean,
    capped: Boolean,
    filtering: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRetry: () -> Unit,
    onPlay: (Recording) -> Unit,
    thumbnailUrl: (Int) -> String?,
    offlineById: Map<Int, OfflineItem>,
    onLongPress: (Recording) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        placeholder = { Text(stringResource(R.string.recordings_search), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
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
                onClick = { onFilterChange(item) },
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
        item {
            FilterChip(
                selected = unwatchedOnly,
                onClick = { onUnwatchedChange(!unwatchedOnly) },
                label = { Text(stringResource(R.string.recordings_unwatched)) },
                shape = RoundedCornerShape(12.dp),
            )
        }
        if (availableGenres.isNotEmpty()) {
            item {
                Box {
                    FilterChip(
                        selected = genreFilter != null,
                        onClick = { onGenreMenuChange(true) },
                        label = { Text(genreFilter ?: stringResource(R.string.recordings_genre)) },
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(12.dp),
                    )
                    DropdownMenu(expanded = genreMenuOpen, onDismissRequest = { onGenreMenuChange(false) }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.recordings_genre_all)) },
                            onClick = {
                                onGenreMenuChange(false)
                                onGenreChange(null)
                            },
                            trailingIcon = { if (genreFilter == null) Text("✓") },
                        )
                        availableGenres.forEach { genre ->
                            DropdownMenuItem(
                                text = { Text(genre) },
                                onClick = {
                                    onGenreMenuChange(false)
                                    onGenreChange(genre)
                                },
                                trailingIcon = { if (genreFilter == genre) Text("✓") },
                            )
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.weight(1f)) {
        when {
            state is LoadState.Failure -> ErrorState(
                error = state.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            visible.isEmpty() && (state is LoadState.Loading || state is LoadState.Idle) -> {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(5) { SkeletonCard(height = 96.dp) }
                }
            }

            visible.isEmpty() -> EmptyState(
                title = if (query.isBlank() && !filtering) {
                    stringResource(R.string.recordings_empty)
                } else {
                    stringResource(R.string.recordings_no_match)
                },
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
                    val download = offlineById[recording.id]
                    RecordingCard(
                        recording = recording,
                        thumbnailUrl = thumbnailUrl(recording.id),
                        resumeMs = positions[recording.id] ?: 0L,
                        downloadState = download?.status,
                        downloadProgress = download?.progress ?: 0f,
                        onClick = { onPlay(recording) },
                        onLongClick = { onLongPress(recording) },
                    )
                }
                if (loadingMore || (filtering && !capped && state is LoadState.Success)) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                if (filtering && capped) {
                    item {
                        Text(
                            text = stringResource(R.string.recordings_filter_capped, visible.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SeriesList(
    query: String,
    onQueryChange: (String) -> Unit,
    state: LoadState<List<Series>>,
    seriesList: List<Series>,
    loadingMore: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRetry: () -> Unit,
    onOpenSeries: (Series) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        placeholder = { Text(stringResource(R.string.series_search), style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
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

    Box(Modifier.weight(1f)) {
        when {
            state is LoadState.Failure -> ErrorState(
                error = state.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            seriesList.isEmpty() && (state is LoadState.Loading || state is LoadState.Idle) -> {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { SkeletonCard(height = 84.dp) }
                }
            }

            seriesList.isEmpty() -> EmptyState(
                title = stringResource(R.string.series_empty),
                message = stringResource(R.string.series_empty_hint),
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(seriesList, key = { it.id }) { series ->
                    SeriesCard(series = series, onClick = { onOpenSeries(series) })
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

@Composable
private fun SeriesCard(series: Series, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = series.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (series.recordingCount > 0) {
                    MetaChip(stringResource(R.string.series_recording_count, series.recordingCount))
                }
                series.genres.firstOrNull { it.major.isNotBlank() }?.let { MetaChip(it.major) }
            }
            val startDate = series.periods.minOfOrNull { it.startDate }?.takeIf { it.isNotBlank() }
            val endDate = series.periods.maxOfOrNull { it.endDate }?.takeIf { it.isNotBlank() }
            if (startDate != null && endDate != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$startDate 〜 $endDate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SeriesDetail(
    series: Series,
    positions: Map<Int, Long>,
    watchedIds: Set<Int>,
    onBack: () -> Unit,
    onPlay: (Recording) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.series_back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.series_recording_count, series.recordingCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val periods = remember(series) { series.periods.sortedByDescending { it.startDate } }
        if (periods.none { it.recordings.isNotEmpty() }) {
            EmptyState(
                title = stringResource(R.string.series_empty),
                message = stringResource(R.string.series_empty_hint),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                periods.forEach { period ->
                    if (period.recordings.isEmpty()) return@forEach
                    item(key = "period-${period.channelId}-${period.startDate}") {
                        Text(
                            text = listOfNotNull(
                                period.channelName,
                                "${period.startDate} 〜 ${period.endDate}".takeIf { period.startDate.isNotBlank() },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                        )
                    }
                    items(period.recordings.sortedByDescending { it.start }, key = { "rec-${it.id}" }) { recording ->
                        SeriesRecordingRow(
                            recording = recording,
                            resumeMs = positions[recording.id] ?: 0L,
                            watched = recording.id in watchedIds,
                            onClick = { onPlay(recording) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesRecordingRow(
    recording: Recording,
    resumeMs: Long,
    watched: Boolean,
    onClick: () -> Unit,
) {
    val durationMs = recording.durationSeconds * 1000
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = recording.displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (watched) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(recording.channelName, formatDateTime(recording.start)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (resumeMs > 0) {
                    Text(
                        text = stringResource(R.string.recordings_resume, formatClock(resumeMs)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatClock(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordingCard(
    recording: Recording,
    thumbnailUrl: String?,
    resumeMs: Long,
    downloadState: OfflineStatus?,
    downloadProgress: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val durationMs = recording.durationSeconds * 1000
    val resumeProgress = if (durationMs > 0 && resumeMs > 0) (resumeMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
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
                    when (downloadState) {
                        OfflineStatus.COMPLETED -> MetaChip(stringResource(R.string.offline_badge))
                        OfflineStatus.DOWNLOADING, OfflineStatus.QUEUED -> MetaChip("↓${(downloadProgress * 100).toInt()}%")
                        else -> Unit
                    }
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

/** 録画カードの長押しで開くアクション（ダウンロード / オフライン再生 / 削除）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecordingActionSheet(
    recording: Recording,
    offlineItem: OfflineItem?,
    defaultQuality: Quality,
    onDismiss: () -> Unit,
    onDownload: (Quality) -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onPlayOffline: () -> Unit,
) {
    val downloadQualities = listOf(
        Quality.Q1080P, Quality.Q720P, Quality.Q540P, Quality.Q480P, Quality.Q360P,
    )
    var quality by remember(recording.id) {
        mutableStateOf(downloadQualities.firstOrNull { it == defaultQuality } ?: Quality.Q720P)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
            Text(
                text = recording.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOfNotNull(
                    recording.channelName,
                    formatClock(recording.durationSeconds * 1000),
                    recording.video.container,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            when (offlineItem?.status) {
                OfflineStatus.QUEUED, OfflineStatus.DOWNLOADING -> {
                    val item = offlineItem
                    Text(
                        text = stringResource(
                            R.string.offline_downloading,
                            (item.progress * 100).toInt(),
                            item.segmentsDone,
                            item.segmentsTotal,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onCancelDownload) {
                        Text(stringResource(R.string.offline_cancel))
                    }
                }

                OfflineStatus.COMPLETED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onPlayOffline, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.offline_play))
                        }
                        TextButton(onClick = onDeleteDownload) {
                            Text(stringResource(R.string.offline_delete))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatFileSize(offlineItem.bytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    if (offlineItem?.status == OfflineStatus.FAILED) {
                        Text(
                            text = stringResource(R.string.offline_failed, offlineItem.error.orEmpty()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        text = stringResource(R.string.offline_download_quality),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        downloadQualities.forEach { q ->
                            FilterChip(
                                selected = quality == q,
                                onClick = { quality = q },
                                label = { Text(q.label) },
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { onDownload(quality) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.offline_start))
                    }
                }
            }
        }
    }
}

/** オフライン保存の一覧（保存済み・ダウンロード中・失敗）。 */
@Composable
private fun ColumnScope.OfflineList(
    items: List<OfflineItem>,
    onPlay: (OfflineItem) -> Unit,
    onCancel: (Int) -> Unit,
    onDelete: (OfflineItem) -> Unit,
    onRetry: (Int) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.offline_empty),
            message = stringResource(R.string.offline_empty_hint),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val totalBytes = items.filter { it.isCompleted }.sumOf { it.bytes }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items.sortedByDescending { it.updatedAt }, key = { it.videoId }) { item ->
            OfflineRow(
                item = item,
                onPlay = { onPlay(item) },
                onCancel = { onCancel(item.videoId) },
                onDelete = { onDelete(item) },
                onRetry = { onRetry(item.videoId) },
            )
        }
        item {
            Text(
                text = stringResource(R.string.offline_total, items.size, formatFileSize(totalBytes)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
        }
    }
}

@Composable
private fun OfflineRow(
    item: OfflineItem,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            item.channelName,
                            Quality.fromApiName(item.quality)?.label,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (item.status) {
                    OfflineStatus.COMPLETED -> {
                        IconButton(onClick = onPlay) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.offline_play),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.offline_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }

                    OfflineStatus.QUEUED, OfflineStatus.DOWNLOADING -> {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.offline_cancel))
                        }
                    }

                    OfflineStatus.FAILED -> {
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.offline_retry))
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.offline_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            when (item.status) {
                OfflineStatus.QUEUED -> Text(
                    text = stringResource(R.string.offline_queued),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OfflineStatus.DOWNLOADING -> {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.offline_downloading,
                            (item.progress * 100).toInt(),
                            item.segmentsDone,
                            item.segmentsTotal,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OfflineStatus.FAILED -> Text(
                    text = stringResource(R.string.offline_failed, item.error.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                OfflineStatus.COMPLETED -> Text(
                    text = formatFileSize(item.bytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
