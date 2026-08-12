package com.yuwhisper.account.capture

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yuwhisper.account.AccountApp
import com.yuwhisper.account.domain.Candidate
import com.yuwhisper.account.domain.PaymentParser
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Captures payment notifications from enabled WatchApp packages,
 * parses them, and forwards to [ConfirmDispatcher].
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return
        if (packageName == applicationContext.packageName) {
            // Ignore our own pending / status notifications.
            return
        }

        val app = applicationContext as? AccountApp ?: return
        app.applicationScope.launch {
            runCatching {
                app.ensureSeeded()
                val enabled = app.ledgerRepository.isWatchAppEnabled(packageName)
                if (!enabled) {
                    Log.d(TAG, "skip disabled package=$packageName")
                    return@runCatching
                }

                val (title, text) = extractTitleAndText(sbn.notification)
                val rawBlob = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
                if (rawBlob.isBlank()) {
                    Log.d(TAG, "empty notification body package=$packageName")
                    return@runCatching
                }

                val parsed = PaymentParser.parse(packageName, title, text)
                if (parsed == null) {
                    Log.d(TAG, "parse miss package=$packageName")
                    return@runCatching
                }

                ConfirmDispatcher.onPaymentDetected(
                    context = app,
                    candidate = Candidate(
                        source = parsed.source,
                        amountCents = parsed.amountCents,
                        merchant = parsed.merchant,
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
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val infoText = extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString().orEmpty()
        val body = listOf(text, bigText, subText, infoText)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")
        return title to body
    }

    companion object {
        private const val TAG = "PaymentNotifListener"
        const val CHANNEL = "notification"
    }
}
