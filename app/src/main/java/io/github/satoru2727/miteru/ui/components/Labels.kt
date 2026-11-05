package io.github.satoru2727.miteru.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.core.model.ChannelType

@Composable
fun channelTypeLabel(type: ChannelType): String = when (type) {
    ChannelType.GR -> stringResource(R.string.live_gr)
    ChannelType.BS, ChannelType.BS4K -> stringResource(R.string.live_bs)
    ChannelType.CS -> stringResource(R.string.live_cs)
    else -> stringResource(R.string.live_other)
}
