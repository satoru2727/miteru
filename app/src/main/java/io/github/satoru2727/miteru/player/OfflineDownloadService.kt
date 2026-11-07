package io.github.satoru2727.miteru.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.satoru2727.miteru.MiteruApplication
import io.github.satoru2727.miteru.R
import io.github.satoru2727.miteru.data.OfflineStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * オフライン保存の進捗を表示するフォアグラウンドサービス（dataSync）。
 * ダウンロード自体は OfflineDownloader が持つ。ここは通知とプロセスの寿命だけを担う。
 */
class OfflineDownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(null, 0f, true))
        val container = (application as MiteruApplication).container
        scope.launch {
            container.offline.items.collect { items ->
                val active = items.firstOrNull { it.status == OfflineStatus.DOWNLOADING }
                val pending = items.any { it.isActive }
                if (!pending) {
                    stopSelf()
                    return@collect
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(
                    NOTIFICATION_ID,
                    buildNotification(active?.title, active?.progress ?: 0f, active == null),
                )
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.offline_notif_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String?, progress: Float, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title ?: getString(R.string.offline_notif_title))
            .setContentText(getString(R.string.offline_notif_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (progress * 100).toInt(), indeterminate)
            .build()

    companion object {
        private const val CHANNEL_ID = "offline_download"
        private const val NOTIFICATION_ID = 41
    }
}
