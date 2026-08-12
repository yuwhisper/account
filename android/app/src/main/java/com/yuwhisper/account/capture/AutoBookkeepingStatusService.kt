package com.yuwhisper.account.capture

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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yuwhisper.account.ui.MainActivity

/**
 * Persistent status notification while the auto-bookkeeping master switch is ON.
 *
 * - Capture channel available →「自动记账运行中」
 * - Master ON but channels unavailable →「自动记账已停止」+ jump to settings
 * - Master OFF → stop service / clear notification
 */
class AutoBookkeepingStatusService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        if (!AutoBookkeepingPrefs.isMasterEnabled(this)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val running = CaptureAvailability.isAnyCaptureChannelAvailable(this)
        val notification = buildNotification(running = running)
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
        return START_STICKY
    }

    private fun buildNotification(running: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val accessibilitySettings = PendingIntent.getActivity(
            this,
            1,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificationListenerSettings = PendingIntent.getActivity(
            this,
            2,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)

        return if (running) {
            val body = buildString {
                append("通知监听：")
                append(if (CaptureAvailability.isNotificationListenerEnabled(this@AutoBookkeepingStatusService)) "开" else "关")
                append(" · 无障碍：")
                append(if (CaptureAvailability.isAccessibilityEnabled(this@AutoBookkeepingStatusService)) "开" else "关")
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

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
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

    companion object {
        const val CHANNEL_ID = "auto_bookkeeping_status"
        private const val CHANNEL_NAME = "自动记账状态"
        const val NOTIF_ID = 9_001

        fun refresh(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, AutoBookkeepingStatusService::class.java)
            if (AutoBookkeepingPrefs.isMasterEnabled(appContext)) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.stopService(intent)
            }
        }
    }
}
