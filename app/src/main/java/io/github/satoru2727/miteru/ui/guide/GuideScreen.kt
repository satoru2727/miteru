package io.github.satoru2727.miteru.ui.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.core.model.Channel
import io.github.satoru2727.miteru.core.model.ChannelType
import io.github.satoru2727.miteru.core.model.Program
import io.github.satoru2727.miteru.core.model.RecordPreset
import io.github.satoru2727.miteru.core.net.RecordSettingsDto
import io.github.satoru2727.miteru.core.model.TimetableChannel
import io.github.satoru2727.miteru.ui.AppViewModel
import io.github.satoru2727.miteru.ui.GuideFilter
import io.github.satoru2727.miteru.ui.LoadState
import io.github.satoru2727.miteru.ui.components.ChannelLogo
import io.github.satoru2727.miteru.ui.components.EmptyState
import io.github.satoru2727.miteru.ui.components.ErrorState
import io.github.satoru2727.miteru.ui.components.channelTypeLabel
import io.github.satoru2727.miteru.ui.util.formatTime
import io.github.satoru2727.miteru.ui.util.minutesOfDay
import io.github.satoru2727.miteru.ui.util.formatTimeRange
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

private val ChannelColumnWidth = 104.dp
private val RowHeight = 66.dp
private val RulerHeight = 30.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(vm: AppViewModel) {
    val state = vm.guideState
    val dayOffset = vm.guideDayOffset
    val filter = vm.guideFilter
    val serverInfo by vm.serverInfo.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }

    var selected by remember { mutableStateOf<Pair<TimetableChannel, Program>?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var hourWidth by remember { mutableStateOf(220.dp) }
    var nowJump by remember { mutableStateOf(0) }

    val filterOptions = listOf(
        GuideFilter.GR to stringResource(R.string.live_gr),
        GuideFilter.BS to stringResource(R.string.live_bs),
        GuideFilter.CS to stringResource(R.string.live_cs),
        GuideFilter.ALL to stringResource(R.string.live_all),
    )

    val timetable = (state as? LoadState.Success)?.value
    val channels = remember(timetable, filter) {
        timetable?.channels.orEmpty().filter { row ->
            when (filter) {
                GuideFilter.ALL -> true
                GuideFilter.GR -> row.channel.type == ChannelType.GR
                GuideFilter.BS -> row.channel.type == ChannelType.BS || row.channel.type == ChannelType.BS4K
                GuideFilter.CS -> row.channel.type == ChannelType.CS
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.guide_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showSearch = true }) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.guide_search))
            }
            TextButton(onClick = { nowJump += 1 }) {
                Text(stringResource(R.string.guide_now))
            }
            IconButton(onClick = { vm.loadGuide(force = true) }) {
                Icon(Icons.Rounded.Schedule, contentDescription = stringResource(R.string.common_retry))
            }
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            val labels = listOf(
                stringResource(R.string.guide_today),
                stringResource(R.string.guide_tomorrow),
                stringResource(R.string.guide_day_after),
            )
            labels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = dayOffset == index,
                    onClick = { vm.selectGuideDay(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filterOptions) { pair ->
                    val value = pair.first
                    val label = pair.second
                    FilterChip(
                        selected = filter == value,
                        onClick = { vm.changeGuideFilter(value) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            }
            IconButton(
                onClick = { hourWidth = (hourWidth - 40.dp).coerceAtLeast(140.dp) },
                enabled = hourWidth > 140.dp,
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.guide_zoom_out))
            }
            IconButton(
                onClick = { hourWidth = (hourWidth + 40.dp).coerceAtMost(360.dp) },
                enabled = hourWidth < 360.dp,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.guide_zoom_in))
            }
        }

        when {
            state is LoadState.Failure -> ErrorState(
                error = state.error,
                onRetry = { vm.loadGuide(force = true) },
                modifier = Modifier.fillMaxSize(),
            )

            timetable == null -> GuideSkeleton()

            channels.isEmpty() -> EmptyState(
                title = stringResource(R.string.guide_no_programs),
                message = stringResource(R.string.live_empty_hint),
                modifier = Modifier.fillMaxSize(),
            )

            else -> GuideGrid(
                channels = channels,
                now = now,
                hourWidth = hourWidth,
                nowJump = nowJump,
                showNowLine = dayOffset == 0,
                playingChannelId = nowPlaying?.channelId,
                logoUrlFor = { vm.logoUrl(it) },
                onProgramClick = { row, program -> selected = row to program },
            )
        }
    }

    if (showSearch) {
        ProgramSearchSheet(
            vm = vm,
            isEdcb = serverInfo?.isEdcb == true,
            onDismiss = { showSearch = false },
        )
    }

    selected?.let { (row, program) ->
        ProgramSheet(
            channel = row.channel,
            program = program,
            logoUrl = vm.logoUrl(row.channel),
            isEdcb = serverInfo?.isEdcb == true,
            isPlaying = nowPlaying?.channelId == row.channel.displayId,
            onDismiss = { selected = null },
            onWatch = {
                selected = null
                vm.requestPlayChannel(row.channel)
            },
            onReserve = { settings -> vm.reserve(program, settings) },
            presets = vm.recordPresets,
            onCancelReservation = { id -> vm.cancelReservation(id) },
        )
    }
}

@Composable
private fun GuideSkeleton() {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        repeat(8) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp)) {
                Box(
                    Modifier
                        .width(ChannelColumnWidth)
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(10.dp),
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            RoundedCornerShape(10.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun GuideGrid(
    channels: List<TimetableChannel>,
    now: Instant,
    hourWidth: Dp,
    nowJump: Int,
    showNowLine: Boolean,
    playingChannelId: String?,
    logoUrlFor: (Channel) -> String?,
    onProgramClick: (TimetableChannel, Program) -> Unit,
) {
    val density = LocalDensity.current
    val pxPerMinute = with(density) { hourWidth.toPx() } / 60f
    val dayWidthPx = 24f * 60f * pxPerMinute
    val dayWidth = with(density) { dayWidthPx.toDp() }
    var scrollPx by remember { mutableFloatStateOf(0f) }
    var didInitialScroll by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportPx = with(density) { (maxWidth - ChannelColumnWidth).toPx() }.coerceAtLeast(1f)
        val maxScroll = (dayWidthPx - viewportPx).coerceAtLeast(0f)
        LaunchedEffect(maxScroll) { scrollPx = scrollPx.coerceIn(0f, maxScroll) }
        LaunchedEffect(maxScroll, pxPerMinute, showNowLine) {
            if (showNowLine && !didInitialScroll) {
                scrollPx = (minutesOfDay(now) * pxPerMinute - viewportPx / 2f).coerceIn(0f, maxScroll)
                didInitialScroll = true
            }
        }
        LaunchedEffect(nowJump) {
            if (nowJump > 0) {
                scrollPx = (minutesOfDay(now) * pxPerMinute - viewportPx / 2f).coerceIn(0f, maxScroll)
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(RulerHeight)) {
                Spacer(Modifier.width(ChannelColumnWidth))
                Box(Modifier.weight(1f).clipToBounds()) {
                    Row(Modifier.offset { IntOffset(-scrollPx.roundToInt(), 0) }) {
                        for (hour in 0..24) {
                            Box(Modifier.width(hourWidth).fillMaxHeight()) {
                                Box(
                                    Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                )
                                Text(
                                    text = "%d:00".format(hour),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                                )
                            }
                        }
                    }
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(maxScroll) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            scrollPx = (scrollPx - dragAmount).coerceIn(0f, maxScroll)
                        }
                    },
            ) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(channels, key = { it.channel.id }) { row ->
                        GuideRow(
                            row = row,
                            scrollPx = scrollPx,
                            pxPerMinute = pxPerMinute,
                            dayWidth = dayWidth,
                            now = now,
                            logoUrl = logoUrlFor(row.channel),
                            isPlaying = playingChannelId == row.channel.displayId,
                            onProgramClick = onProgramClick,
                        )
                    }
                }

                if (showNowLine) {
                    Row(Modifier.fillMaxSize()) {
                        Spacer(Modifier.width(ChannelColumnWidth))
                        Box(Modifier.weight(1f).clipToBounds()) {
                            val nowX = minutesOfDay(now) * pxPerMinute - scrollPx
                            if (nowX >= 0f && nowX <= viewportPx) {
                                Box(
                                    Modifier
                                        .offset { IntOffset(nowX.roundToInt(), 0) }
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
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
private fun GuideRow(
    row: TimetableChannel,
    scrollPx: Float,
    pxPerMinute: Float,
    dayWidth: Dp,
    now: Instant,
    logoUrl: String?,
    isPlaying: Boolean,
    onProgramClick: (TimetableChannel, Program) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(RowHeight)) {
        Row(
            modifier = Modifier
                .width(ChannelColumnWidth)
                .fillMaxHeight()
                .background(
                    if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surface,
                )
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(url = logoUrl, size = 26.dp)
            Spacer(Modifier.width(6.dp))
            Column {
                Text(
                    text = row.channel.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = channelTypeLabel(row.channel.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            Box(
                Modifier
                    .offset { IntOffset(-scrollPx.roundToInt(), 0) }
                    .width(dayWidth)
                    .fillMaxHeight(),
            ) {
                row.programs.forEach { program ->
                    val startMinutes = minutesOfDay(program.start)
                    var endMinutes = minutesOfDay(program.end)
                    if (endMinutes <= startMinutes) endMinutes = 24f * 60f
                    val widthPx = ((endMinutes - startMinutes) * pxPerMinute).coerceAtLeast(24f)
                    val onAir = !now.isBefore(program.start) && now.isBefore(program.end)
                    ProgramBlock(
                        program = program,
                        onAir = onAir,
                        modifier = Modifier
                            .offset { IntOffset((startMinutes * pxPerMinute).roundToInt(), 0) }
                            .width(with(LocalDensity.current) { widthPx.toDp() })
                            .fillMaxHeight()
                            .padding(vertical = 2.dp, horizontal = 1.dp),
                        onClick = { onProgramClick(row, program) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramBlock(
    program: Program,
    onAir: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (onAir) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            1.dp,
            if (onAir) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatTime(program.start),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (onAir) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (program.reservation != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.guide_reserved),
                        modifier = Modifier.size(11.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Text(
                text = program.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (onAir) FontWeight.SemiBold else FontWeight.Normal,
                color = if (onAir) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgramSheet(
    channel: Channel,
    program: Program,
    logoUrl: String?,
    isEdcb: Boolean,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onWatch: () -> Unit,
    onReserve: suspend (RecordSettingsDto) -> Result<Unit>,
    onCancelReservation: suspend (Int) -> Result<Unit>,
    presets: List<RecordPreset>,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var reserveError by remember { mutableStateOf<String?>(null) }
    var showReserveDialog by remember { mutableStateOf(false) }
    val failedMessage = stringResource(R.string.guide_reserve_failed)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(url = logoUrl, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatTimeRange(program.start, program.end),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (program.reservation != null) {
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                if (program.reservation.status == "Recording") {
                                    stringResource(R.string.guide_recording)
                                } else {
                                    stringResource(R.string.guide_reserved)
                                },
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = program.title,
                style = MaterialTheme.typography.headlineSmall,
            )

            if (program.genres.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    program.genres.take(3).forEach { genre ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = listOf(genre.major, genre.middle).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = program.description.ifBlank { stringResource(R.string.guide_no_programs) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val details = program.detail.entries.take(6)
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(12.dp))
                details.forEach { (label, value) ->
                    if (value.isNotBlank()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(84.dp),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (program.isOnAir || isPlaying) {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onWatch,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.guide_watch_now))
                }
            }

            if (isEdcb) {
                Spacer(Modifier.height(8.dp))
                val reservation = program.reservation
                if (reservation != null) {
                    OutlinedButton(
                        onClick = {
                            if (working) return@OutlinedButton
                            scope.launch {
                                working = true
                                reserveError = null
                                val result = onCancelReservation(reservation.id)
                                working = false
                                if (result.isSuccess) onDismiss() else {
                                    reserveError = result.exceptionOrNull()?.message ?: failedMessage
                                }
                            }
                        },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(if (working) stringResource(R.string.guide_reserving) else stringResource(R.string.guide_cancel_reservation))
                    }
                } else if (!program.end.isBefore(java.time.Instant.now())) {
                    Button(
                        onClick = { if (!working) showReserveDialog = true },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(if (working) stringResource(R.string.guide_reserving) else stringResource(R.string.guide_reserve))
                    }
                }
                if (reserveError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = reserveError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.guide_reserve_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showReserveDialog) {
        ReserveDialog(
            presets = presets,
            onDismiss = { showReserveDialog = false },
            onConfirm = { settings ->
                showReserveDialog = false
                scope.launch {
                    working = true
                    reserveError = null
                    val result = onReserve(settings)
                    working = false
                    if (result.isSuccess) onDismiss() else {
                        reserveError = result.exceptionOrNull()?.message ?: failedMessage
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReserveDialog(
    presets: List<RecordPreset>,
    onDismiss: () -> Unit,
    onConfirm: (RecordSettingsDto) -> Unit,
) {
    var selectedPreset by remember { mutableStateOf(presets.firstOrNull()) }
    var priority by remember { mutableStateOf(selectedPreset?.settings?.priority ?: 3) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reserve_dialog_title)) },
        text = {
            Column {
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
                Spacer(Modifier.height(14.dp))
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val base = selectedPreset?.settings ?: RecordSettingsDto()
                onConfirm(base.copy(priority = priority))
            }) {
                Text(stringResource(R.string.reserve_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}



