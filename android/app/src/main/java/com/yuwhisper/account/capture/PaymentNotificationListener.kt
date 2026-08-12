package com.yuwhisper.account.capture

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yuwhisper.account.AccountApp
import com.yuwhisper.account.domain.Candidate
import com.yuwhisper.account.domain.DefaultWatchApps
import com.yuwhisper.account.domain.PaymentParser
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Captures payment notifications from enabled WatchApp packages.
 *
 * WeChat QR pays often skip a useful notification — accessibility is primary;
 * this still catches「微信支付」voucher / template notifications when present.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        AutoBookkeepingStatusService.refresh(applicationContext)
    }

    override fun onListenerDisconnected() {
        AutoBookkeepingStatusService.refresh(applicationContext)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!AutoBookkeepingPrefs.isMasterEnabled(this)) return
        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) return
        if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val app = applicationContext as? AccountApp ?: return
        val listener = this
        app.applicationScope.launch {
            runCatching {
                app.ensureSeeded()
                if (!app.ledgerRepository.isWatchAppEnabled(packageName)) {
                    Log.d(TAG, "skip disabled package=$packageName")
                    return@runCatching
                }

                val (title, text) = extractTitleAndText(sbn.notification)
                val rawBlob = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
                if (rawBlob.isBlank()) {
                    Log.d(TAG, "empty notification body package=$packageName")
                    return@runCatching
                }

                if (!PaymentParser.shouldOfferConfirm(packageName, title, text, accessibilityMode = false)) {
                    Log.d(TAG, "not payment-like package=$packageName title=$title")
                    return@runCatching
                }

                val parsed = PaymentParser.parse(packageName, title, text)
                val source = parsed?.source ?: sourceForPackage(packageName)
                val merchant = parsed?.merchant?.takeIf { it.isNotBlank() }
                    ?: title.takeIf {
                        it.isNotBlank() && !it.contains("微信") && !it.contains("支付宝")
                    }
                    ?: "未知商户"

                Log.i(TAG, "notif hit pkg=$packageName amount=${parsed?.amountCents} title=$title")
                ConfirmDispatcher.onPaymentDetected(
                    context = listener,
                    candidate = Candidate(
                        source = source,
                        amountCents = parsed?.amountCents,
                        merchant = merchant,
                        occurredAt = Instant.ofEpochMilli(sbn.postTime),
                    ),
                    captureChannel = CHANNEL,
                    rawText = rawBlob,
                )
            }.onFailure { t ->
                Log.e(TAG, "onNotificationPosted failed", t)
            }
        }
    }

    private fun extractTitleAndText(notification: Notification): Pair<String, String> {
        val extras = notification.extras
        val title = listOf(
            extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString(),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val infoText = extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
        val summary = extras?.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        val ticker = notification.tickerText?.toString().orEmpty()
        val lines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
            .orEmpty()

        val body = (listOf(text, bigText, subText, infoText, summary, ticker) + lines)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
        return title to body
    }

    private fun sourceForPackage(packageName: String): String = when (packageName) {
        DefaultWatchApps.WECHAT -> "wechat"
        DefaultWatchApps.ALIPAY -> "alipay"
        DefaultWatchApps.UNIONPAY -> "unionpay"
        else -> packageName.substringAfterLast('.').ifBlank { "other" }
    }

    companion object {
        private const val TAG = "PaymentNotifListener"
        const val CHANNEL = "notification"
    }
}
