package io.github.satoru2727.miteru.ui

import androidx.compose.runtime.staticCompositionLocalOf
import coil3.ImageLoader

/**
 * アプリ全体で 1 つの ImageLoader を共有する。
 * SingletonImageLoader に頼らず明示的に渡すことで、OkHttp フェッチャーが確実に使われる。
 */
val LocalAppImageLoader = staticCompositionLocalOf<ImageLoader> {
    error("LocalAppImageLoader is not provided")
}
