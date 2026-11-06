package io.github.satoru2727.miteru.ui

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.satoru2727.miteru.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }
