package io.github.satoru2727.miteru.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.core.model.Channel
import io.github.satoru2727.miteru.core.model.ChannelType
import io.github.satoru2727.miteru.ui.AppViewModel
import io.github.satoru2727.miteru.ui.ChannelFilter
import io.github.satoru2727.miteru.ui.LoadState
import io.github.satoru2727.miteru.ui.components.ChannelLogo
import io.github.satoru2727.miteru.ui.components.EmptyState
import io.github.satoru2727.miteru.ui.components.ErrorState
import io.github.satoru2727.miteru.ui.components.SkeletonCard
import io.github.satoru2727.miteru.ui.components.channelTypeLabel
import io.github.satoru2727.miteru.ui.util.JikkyoForceTier
import io.github.satoru2727.miteru.ui.util.formatRelative
import io.github.satoru2727.miteru.ui.util.formatTimeRange
import io.github.satoru2727.miteru.ui.util.jikkyoForceTier
import kotlinx.coroutines.delay
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(vm: AppViewModel) {
    val state = vm.channelsState
    val query = vm.channelQuery
    val filter = vm.channelFilter
    val serverInfo by vm.serverInfo.collectAsStateWithLifecycle()
    val lastRefresh by vm.lastChannelRefresh.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = Instant.now()
        }
    }

    val channels = (state as? LoadState.Success)?.value.orEmpty()
    val showSubchannels = settings?.showSubchannels ?: true
    val visible = remember(channels, query, filter, showSubchannels) {
        channels.filter { channel ->
            val matchesSubchannel = showSubchannels || !channel.isSubchannel
            val matchesType = when (filter) {
                ChannelFilter.ALL -> true
                ChannelFilter.GR -> channel.type == ChannelType.GR
                ChannelFilter.BS -> channel.type == ChannelType.BS || channel.type == ChannelType.BS4K
                ChannelFilter.CS -> channel.type == ChannelType.CS
                ChannelFilter.OTHER -> channel.type == ChannelType.CATV || channel.type == ChannelType.SKY
            }
            val matchesQuery = query.isBlank() ||
                channel.name.contains(query, ignoreCase = true) ||
                channel.current?.title?.contains(query, ignoreCase = true) == true
            matchesSubchannel && matchesType && matchesQuery
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.live_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = buildString {
                        append(stringResource(R.string.live_count, channels.size))
                        if (lastRefresh > 0) {
                            append(" · ")
                            append(formatRelative(lastRefresh))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectionDot(connected = serverInfo != null)
            IconButton(onClick = { vm.loadChannels() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.common_retry))
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = vm::onChannelQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            placeholder = { Text(stringResource(R.string.live_search), style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.onChannelQueryChange("") }) {
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ChannelFilter.entries.toList()) { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { vm.onChannelFilterChange(item) },
                    label = {
                        Text(
                            text = when (item) {
                                ChannelFilter.ALL -> stringResource(R.string.live_all)
                                ChannelFilter.GR -> stringResource(R.string.live_gr)
                                ChannelFilter.BS -> stringResource(R.string.live_bs)
                                ChannelFilter.CS -> stringResource(R.string.live_cs)
                                ChannelFilter.OTHER -> stringResource(R.string.live_other)
                            },
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }

        val fromCache = (state as? LoadState.Success)?.fromCache == true
        if (fromCache) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.common_offline) + " · " + formatRelative(lastRefresh) + " のデータ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        Box(Modifier.weight(1f)) {
            when {
                state is LoadState.Failure -> ErrorState(
                    error = state.error,
                    onRetry = { vm.loadChannels() },
                    modifier = Modifier.fillMaxSize(),
                )

                channels.isEmpty() && (state is LoadState.Loading || state is LoadState.Idle) -> SkeletonGrid()

                visible.isEmpty() && channels.isNotEmpty() -> EmptyState(
                    title = stringResource(R.string.live_empty),
                    message = stringResource(R.string.live_empty_hint),
                    modifier = Modifier.fillMaxSize(),
                )

                else -> PullToRefreshBox(
                    isRefreshing = state is LoadState.Loading,
                    onRefresh = { vm.loadChannels() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visible, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                logoUrl = vm.logoUrl(channel),
                                now = now,
                                isPlaying = nowPlaying?.channelId == channel.displayId,
                                onClick = { vm.requestPlayChannel(channel) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionDot(connected: Boolean) {
    val color = if (connected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    Surface(color = color, shape = CircleShape, modifier = Modifier.size(9.dp)) {}
}

@Composable
private fun SkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(6) {
            SkeletonCard(height = 150.dp)
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    logoUrl: String?,
    now: Instant,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val current = channel.current
    val progress = if (current != null && current.durationSeconds > 0) {
        ((now.epochSecond - current.start.epochSecond).toFloat() / current.durationSeconds).coerceIn(0f, 1f)
    } else 0f
    val onAir = current != null

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isPlaying) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (isPlaying || onAir) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.6f else 0.22f))
        } else {
            null
        },
        modifier = Modifier.heightIn(min = 152.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(url = logoUrl, size = 42.dp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (channel.isRadio) {
                            Icon(
                                Icons.Rounded.Radio,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(3.dp))
                        }
                        Text(
                            text = channelTypeLabel(channel.type) + " · " + channel.channelNumber.ifBlank { "${channel.remoconId}ch" },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = current?.title ?: stringResource(R.string.guide_no_programs),
                style = MaterialTheme.typography.bodySmall,
                color = if (current != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            if (current != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTimeRange(current.start, current.end),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    val forceTier = jikkyoForceTier(channel.jikkyoForce)
                    if (forceTier != JikkyoForceTier.NORMAL) {
                        JikkyoForceBadge(forceTier)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (channel.viewerCount > 0) {
                        Icon(
                            Icons.Rounded.People,
                            contentDescription = null,
                            modifier = Modifier.size(11.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = channel.viewerCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** ニコニコ実況の勢い（多 / 激多 / 祭）。KonomiTV 本体と同じ閾値・色使い。 */
@Composable
private fun JikkyoForceBadge(tier: JikkyoForceTier) {
    val labelRes = when (tier) {
        JikkyoForceTier.MANY -> R.string.live_force_many
        JikkyoForceTier.SO_MANY -> R.string.live_force_so_many
        JikkyoForceTier.FESTIVAL -> R.string.live_force_festival
        JikkyoForceTier.NORMAL -> return
    }
    val color = when (tier) {
        JikkyoForceTier.MANY -> Color(0xFFF59E0B)
        JikkyoForceTier.SO_MANY -> Color(0xFFF97316)
        JikkyoForceTier.FESTIVAL -> Color(0xFFEF4444)
        JikkyoForceTier.NORMAL -> return
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(5.dp),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}
