package com.yuwhisper.account.capture

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yuwhisper.account.ui.MainActivity

/**
 * Optional status notification while the auto-bookkeeping master switch is ON.
 *
 * Prefer a plain notification when background FGS start is blocked (Android 12+).
 * On Android 15+, stop cleanly on dataSync FGS timeout instead of crashing.
 */
class AutoBookkeepingStatusService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val master = AutoBookkeepingPrefs.isMasterEnabled(this)
        val showStatus = AutoBookkeepingPrefs.isShowStatusNotification(this)
        if (!master || !showStatus) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val running = CaptureAvailability.isAnyCaptureChannelAvailable(this)
        val notification = buildNotification(this, running = running)
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        }.onFailure { t ->
            Log.w(TAG, "startForeground failed; falling back to plain notification", t)
            NotificationManagerCompat.from(this).notify(NOTIF_ID, notification)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 dataSync FGS time limit — exit cleanly to avoid RemoteServiceException.
        Log.w(TAG, "FGS timeout type=$fgsType; demoting to plain notification")
        val running = CaptureAvailability.isAnyCaptureChannelAvailable(this)
        NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(this, running))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val CHANNEL_ID = "auto_bookkeeping_status"
        private const val CHANNEL_NAME = "自动记账状态"
        const val NOTIF_ID = 9_001
        private const val TAG = "AutoBookkeepingStatus"

        fun refresh(context: Context) {
            val appContext = context.applicationContext
            ensureChannel(appContext)
            val intent = Intent(appContext, AutoBookkeepingStatusService::class.java)
            val want =
                AutoBookkeepingPrefs.isMasterEnabled(appContext) &&
                    AutoBookkeepingPrefs.isShowStatusNotification(appContext)
            if (!want) {
                appContext.stopService(intent)
                NotificationManagerCompat.from(appContext).cancel(NOTIF_ID)
                return
            }
            val running = CaptureAvailability.isAnyCaptureChannelAvailable(appContext)
            val notification = buildNotification(appContext, running)
            try {
                ContextCompat.startForegroundService(appContext, intent)
            } catch (t: Throwable) {
                val blocked = Build.VERSION.SDK_INT >= 31 &&
                    t is ForegroundServiceStartNotAllowedException
                Log.w(TAG, "startForegroundService blocked=$blocked; using plain notification", t)
                NotificationManagerCompat.from(appContext).notify(NOTIF_ID, notification)
            }
        }

        private fun buildNotification(context: Context, running: Boolean): Notification {
            val openApp = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val accessibilitySettings = PendingIntent.getActivity(
                context,
                1,
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notificationListenerSettings = PendingIntent.getActivity(
                context,
                2,
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            return if (running) {
                val body = buildString {
                    append("通知监听：")
                    append(if (CaptureAvailability.isNotificationListenerEnabled(context)) "开" else "关")
                    append(" · 无障碍：")
                    append(if (CaptureAvailability.isAccessibilityEnabled(context)) "开" else "关")
                }
                builder
                    .setContentTitle("自动记账运行中")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .build()
            } else {
                val body = "通知监听或无障碍未开启，点此打开设置重新启用。"
                builder
                    .setContentTitle("自动记账已停止")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .addAction(0, "无障碍设置", accessibilitySettings)
                    .addAction(0, "通知使用权", notificationListenerSettings)
                    .build()
            }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "自动记账运行状态"
                    setShowBadge(false)
                },
            )
        }
    }
}
