package io.github.satoru2727.miteru.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.satoru2727.miteru.BuildConfig
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.core.model.Quality
import io.github.satoru2727.miteru.core.settings.ThemeMode
import io.github.satoru2727.miteru.ui.AppViewModel
import io.github.satoru2727.miteru.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onChangeServer: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val serverInfoState by vm.serverInfo.collectAsStateWithLifecycle()
    val serverInfo = serverInfoState
    val current = settings ?: return

    var qualitySheet by remember { mutableStateOf(false) }
    var confirmClearServer by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
        )

        SectionHeader(
            title = stringResource(R.string.settings_server),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = current.serverUrl.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (serverInfo != null) {
                        Pill(stringResource(R.string.settings_connected), MaterialTheme.colorScheme.tertiaryContainer)
                        Pill(serverInfo.backend, MaterialTheme.colorScheme.secondaryContainer)
                        Pill("v${serverInfo.version}", MaterialTheme.colorScheme.surfaceContainerHighest)
                    } else {
                        Pill(stringResource(R.string.settings_disconnected), MaterialTheme.colorScheme.errorContainer)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onChangeServer) {
                        Text(stringResource(R.string.settings_server_change))
                    }
                    TextButton(onClick = { confirmClearServer = true }) {
                        Text(stringResource(R.string.settings_disconnect), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        SectionHeader(
            title = stringResource(R.string.settings_playback),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        SettingsCard {
            Column {
                SettingRow(
                    title = stringResource(R.string.settings_default_quality),
                    subtitle = current.defaultQuality.label,
                    onClick = { qualitySheet = true },
                    trailing = {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                SettingRow(
                    title = stringResource(R.string.settings_mobile_warning),
                    subtitle = stringResource(R.string.settings_mobile_warning_desc),
                    trailing = {
                        Switch(
                            checked = current.warnOnMobileData,
                            onCheckedChange = vm::setWarnOnMobileData,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                SettingRow(
                    title = stringResource(R.string.settings_resume),
                    subtitle = stringResource(R.string.settings_resume_desc),
                    trailing = {
                        Switch(
                            checked = current.resumePlayback,
                            onCheckedChange = vm::setResumePlayback,
                        )
                    },
                )
            }
        }

        SectionHeader(
            title = stringResource(R.string.settings_appearance),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        SettingsCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                        ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                    )
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = current.themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_dynamic_color),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.settings_dynamic_color_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = current.dynamicColor,
                        onCheckedChange = vm::setDynamicColor,
                    )
                }
            }
        }

        SectionHeader(
            title = stringResource(R.string.settings_about),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        SettingsCard {
            Column {
                SettingRow(
                    title = stringResource(R.string.settings_version),
                    subtitle = BuildConfig.VERSION_NAME,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                SettingRow(
                    title = stringResource(R.string.settings_source),
                    subtitle = "github.com/satoru2727/miteru",
                    onClick = { uriHandler.openUri("https://github.com/satoru2727/miteru") },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                SettingRow(
                    title = stringResource(R.string.settings_license),
                    subtitle = stringResource(R.string.settings_license_value),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                SettingRow(
                    title = stringResource(R.string.settings_fdroid),
                    subtitle = stringResource(R.string.settings_fdroid_value),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                SettingRow(
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = stringResource(R.string.settings_clear_cache_desc),
                    onClick = { confirmClearCache = true },
                    trailing = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_made_with),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )
        Text(
            text = stringResource(R.string.settings_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        )
    }

    if (qualitySheet) {
        ModalBottomSheet(
            onDismissRequest = { qualitySheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
                Text(
                    text = stringResource(R.string.settings_default_quality),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
                Quality.entries.forEach { quality ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.setDefaultQuality(quality)
                                qualitySheet = false
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = current.defaultQuality == quality,
                            onClick = {
                                vm.setDefaultQuality(quality)
                                qualitySheet = false
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(quality.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (confirmClearServer) {
        AlertDialog(
            onDismissRequest = { confirmClearServer = false },
            title = { Text(stringResource(R.string.settings_disconnect)) },
            text = { Text(stringResource(R.string.settings_disconnect_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearServer = false
                        vm.clearServer()
                    },
                ) {
                    Text(stringResource(R.string.settings_ok), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearServer = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = { Text(stringResource(R.string.settings_clear_cache_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClearCache = false
                        vm.clearCache()
                    },
                ) {
                    Text(stringResource(R.string.settings_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun Pill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(7.dp), color = color) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}
