package com.yuwhisper.account.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yuwhisper.account.ui.confirm.ConfirmPaymentActivity
import java.util.Locale

/**
 * Status-bar fallback when the floating confirm card cannot be shown.
 * Tap opens [ConfirmPaymentActivity]; never uses full-screen intent (that jumps out of WeChat).
 */
object PendingPaymentNotifier {

    const val CHANNEL_ID = "pending_ledger"
    private const val CHANNEL_NAME = "待入账"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            // Upgrade importance if an older install created a low channel.
            if (existing.importance < NotificationManager.IMPORTANCE_HIGH) {
                manager.deleteNotificationChannel(CHANNEL_ID)
            } else {
                return
            }
        }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "付款待确认入账提醒"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            },
        )
    }

    fun show(
        context: Context,
        pendingId: Long,
        amountCents: Long?,
        merchant: String,
        source: String,
    ) {
        ensureChannel(context)
        val intent = ConfirmPaymentActivity.createIntent(context, pendingId).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            pendingId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val amountText = formatYuan(amountCents)
        val title = "待入账 · $amountText"
        val body = buildString {
            append(sourceLabel(source))
            if (merchant.isNotBlank()) {
                append(" · ")
                append(merchant)
            }
            append(" — 点击确认入账")
        }
        val publicTitle = "惜夏记 · 待确认"
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(publicTitle)
                    .setContentText("有一笔付款待确认入账")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build(),
            )
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        // Never setFullScreenIntent — it launches ConfirmPaymentActivity and leaves WeChat.
        val notification = builder.build()
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            Log.w(TAG, "notifications disabled; skip pendingId=$pendingId")
            CaptureDebug.note("待入账通知被系统关闭 pendingId=$pendingId")
            return
        }
        runCatching {
            nm.notify(notificationId(pendingId), notification)
        }.onFailure {
            Log.w(TAG, "notify pending failed pendingId=$pendingId", it)
            CaptureDebug.note("待入账通知发送失败: ${it.message}")
        }
    }

    fun cancel(context: Context, pendingId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(pendingId))
    }

    fun notificationId(pendingId: Long): Int =
        (PENDING_NOTIF_BASE + pendingId).toInt()

    private fun formatYuan(amountCents: Long?): String {
        if (amountCents == null) return "金额待填"
        return String.format(Locale.CHINA, "¥%.2f", amountCents / 100.0)
    }

    private fun sourceLabel(source: String): String = when (source) {
        "wechat" -> "微信"
        "alipay" -> "支付宝"
        "manual" -> "模拟"
        else -> source.ifBlank { "未知来源" }
    }

    private const val PENDING_NOTIF_BASE = 71_000
    private const val TAG = "PendingNotifier"
}
