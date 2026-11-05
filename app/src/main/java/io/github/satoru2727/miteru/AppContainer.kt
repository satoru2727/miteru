package io.github.satoru2727.miteru

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.satoru2727.miteru.core.settings.SettingsStore
import io.github.satoru2727.miteru.data.JikkyoRepository
import io.github.satoru2727.miteru.data.KonomiRepository
import io.github.satoru2727.miteru.player.PlaybackLauncher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    val settings: SettingsStore = SettingsStore(appContext)
    val repository: KonomiRepository = KonomiRepository(settings, httpClient, json)
    val jikkyo: JikkyoRepository = JikkyoRepository(repository, httpClient, json)
    val playback: PlaybackLauncher = PlaybackLauncher(appContext, repository, settings)

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
        }
        .crossfade(true)
        .build()
}

class MiteruApplication : android.app.Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SingletonImageLoader.setSafe { container.imageLoader }
    }
}
