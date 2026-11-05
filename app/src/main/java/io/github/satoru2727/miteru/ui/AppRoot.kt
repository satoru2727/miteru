package io.github.satoru2727.miteru.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.satoru2727.miteru.AppContainer
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.core.settings.ThemeMode
import io.github.satoru2727.miteru.ui.guide.GuideScreen
import io.github.satoru2727.miteru.ui.live.LiveScreen
import io.github.satoru2727.miteru.ui.player.MiniPlayer
import io.github.satoru2727.miteru.ui.player.PlayerScreen
import io.github.satoru2727.miteru.ui.recordings.RecordingsScreen
import io.github.satoru2727.miteru.ui.settings.SettingsScreen
import io.github.satoru2727.miteru.ui.setup.SetupScreen
import io.github.satoru2727.miteru.ui.theme.KonomiTheme
import io.github.satoru2727.miteru.ui.util.formatClock

@Composable
fun AppRoot(container: AppContainer) {
    val vm: AppViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AppViewModel(container) }
        },
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    var editingServer by rememberSaveable { mutableStateOf(false) }
    val current = settings

    val dark = when (current?.themeMode ?: ThemeMode.SYSTEM) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    KonomiTheme(
        darkTheme = dark,
        dynamicColor = current?.dynamicColor ?: true,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                current == null -> Splash()

                !current.isConfigured || editingServer -> SetupScreen(
                    vm = vm,
                    initialUrl = current.serverUrl,
                    isChangeMode = editingServer && current.isConfigured,
                    onCancel = { editingServer = false },
                    onDone = { editingServer = false },
                )

                else -> MainScreen(
                    vm = vm,
                    onChangeServer = { editingServer = true },
                )
            }
        }
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun MainScreen(
    vm: AppViewModel,
    onChangeServer: () -> Unit,
) {
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val pendingResume = vm.pendingResume
    val pendingMetered = vm.pendingMeteredPlay
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    androidx.compose.runtime.LaunchedEffect(nowPlaying != null) {
        if (nowPlaying != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    if (nowPlaying != null) {
                        MiniPlayer(vm = vm, onExpand = vm::openPlayer)
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = vm.tab == tab,
                                onClick = { vm.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            Tab.LIVE -> Icons.Rounded.LiveTv
                                            Tab.GUIDE -> Icons.Rounded.CalendarMonth
                                            Tab.RECORDINGS -> Icons.Rounded.VideoLibrary
                                            Tab.SETTINGS -> Icons.Rounded.Settings
                                        },
                                        contentDescription = null,
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(
                                            when (tab) {
                                                Tab.LIVE -> R.string.nav_live
                                                Tab.GUIDE -> R.string.nav_guide
                                                Tab.RECORDINGS -> R.string.nav_recordings
                                                Tab.SETTINGS -> R.string.nav_settings
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (vm.tab) {
                    Tab.LIVE -> LiveScreen(vm)
                    Tab.GUIDE -> GuideScreen(vm)
                    Tab.RECORDINGS -> RecordingsScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm = vm, onChangeServer = onChangeServer)
                }
            }
        }

        if (vm.showPlayer && nowPlaying != null) {
            PlayerScreen(vm)
        }
    }

    if (pendingResume != null) {
        AlertDialog(
            onDismissRequest = vm::dismissResume,
            title = { Text(stringResource(R.string.player_resume_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.player_resume_message,
                        formatClock(pendingResume.positionMs),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = vm::confirmResume) {
                    Text(stringResource(R.string.player_resume_play))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::startFromBeginning) {
                    Text(stringResource(R.string.player_resume_start))
                }
            },
        )
    }

    if (pendingMetered != null) {
        AlertDialog(
            onDismissRequest = vm::dismissMeteredPlay,
            title = { Text(stringResource(R.string.metered_title)) },
            text = { Text(stringResource(R.string.metered_message)) },
            confirmButton = {
                TextButton(onClick = vm::confirmMeteredPlay) {
                    Text(stringResource(R.string.metered_play))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissMeteredPlay) {
                    Text(stringResource(R.string.metered_cancel))
                }
            },
        )
    }
}
