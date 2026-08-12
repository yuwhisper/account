package com.yuwhisper.account.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yuwhisper.account.ui.confirm.ConfirmPaymentActivity
import java.util.Locale

/**
 * Status-bar fallback when the centered confirm card cannot be shown.
 * Tap opens the same [ConfirmPaymentActivity].
 */
object PendingPaymentNotifier {

    const val CHANNEL_ID = "pending_ledger"
    private const val CHANNEL_NAME = "待入账"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "付款待确认入账提醒"
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(pendingId), notification)
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
}
