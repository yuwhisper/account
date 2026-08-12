package com.yuwhisper.account.capture

import android.content.Context
import android.util.Log
import com.yuwhisper.account.AccountApp
import com.yuwhisper.account.data.local.entity.PendingPaymentEntity
import com.yuwhisper.account.domain.Candidate
import com.yuwhisper.account.domain.Dedupe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Unified entry for payment candidates from notification / accessibility / debug.
 *
 * Prefer accessibility overlay → SYSTEM_ALERT_WINDOW overlay. Always posts a
 * 「待入账」notification as a safety net, but never auto-opens this app.
 */
object ConfirmDispatcher {

    // Notification and accessibility usually report the same payment within seconds.
    // A longer window drops legitimate repeat purchases with the same amount/merchant.
    const val DEDUPE_WINDOW_SECONDS = 8
    private const val TAG = "ConfirmDispatcher"
    private val handleMutex = Mutex()

    /**
     * @param captureChannel notification | accessibility | manual
     */
    fun onPaymentDetected(
        context: Context,
        candidate: Candidate,
        captureChannel: String,
        rawText: String = "",
    ) {
        val app = context.applicationContext as AccountApp
        // Keep the calling context when it is a bound service (notification / a11y) —
        // Application Context alone is more likely to hit background-activity blocks.
        val startContext = context
        app.applicationScope.launch {
            runCatching {
                handle(
                    app = app,
                    startContext = startContext,
                    candidate = candidate,
                    captureChannel = captureChannel,
                    rawText = rawText,
                )
            }.onFailure { t ->
                Log.e(TAG, "onPaymentDetected failed", t)
            }
        }
    }

    private suspend fun handle(
        app: AccountApp,
        startContext: Context,
        candidate: Candidate,
        captureChannel: String,
        rawText: String,
    ) {
        app.ensureSeeded()
        val pendingId = handleMutex.withLock {
            val repo = app.ledgerRepository

            // Unconfirmed pending for the same pay → re-show UI (do not silently drop).
            val existingPendingId = repo.findMatchingPendingId(candidate, DEDUPE_WINDOW_SECONDS)
            if (existingPendingId != null) {
                Log.i(TAG, "re-show pendingId=$existingPendingId channel=$captureChannel")
                return@withLock existingPendingId
            }

            val existing = repo.listDedupeCandidates()
            if (Dedupe.isDuplicate(candidate, existing, DEDUPE_WINDOW_SECONDS)) {
                Log.i(TAG, "duplicate skipped: ${candidate.source} ${candidate.amountCents} ${candidate.merchant}")
                return
            }

            val now = Instant.now()
            repo.insertPendingPayment(
                PendingPaymentEntity(
                    amountCents = candidate.amountCents?.toLong(),
                    merchant = candidate.merchant,
                    source = candidate.source,
                    captureChannel = captureChannel,
                    rawText = rawText,
                    dedupeKey = buildDedupeKey(candidate),
                    occurredAt = candidate.occurredAt,
                    createdAt = now,
                ),
            )
        }

        withContext(Dispatchers.Main) {
            showConfirmUi(startContext.applicationContext, startContext, pendingId, candidate)
        }
    }

    private fun showConfirmUi(
        appContext: Context,
        startContext: Context,
        pendingId: Long,
        candidate: Candidate,
    ) {
        PendingPaymentNotifier.show(
            context = appContext,
            pendingId = pendingId,
            amountCents = candidate.amountCents?.toLong(),
            merchant = candidate.merchant,
            source = candidate.source,
        )

        // 1) Accessibility overlay — stays over WeChat, no SYSTEM_ALERT_WINDOW needed.
        val a11yService = PaymentAccessibilityService.instance
        val a11yShown = a11yService?.showConfirmOverlay(pendingId) == true
        if (a11yShown) {
            CaptureDebug.note("已弹无障碍悬浮层 pendingId=$pendingId")
            Log.i(TAG, "a11y overlay shown pendingId=$pendingId")
            return
        }

        // 2) SYSTEM_ALERT_WINDOW overlay — start from service context when possible.
        val overlayCtx = a11yService ?: startContext
        val overlayStarted = ConfirmOverlayService.show(overlayCtx, pendingId)
        if (overlayStarted) {
            CaptureDebug.note("已启动系统悬浮窗 pendingId=$pendingId")
            Log.i(TAG, "overlay service started pendingId=$pendingId")
            return
        }

        // Do not start ConfirmPaymentActivity automatically: it would switch away
        // from WeChat. The user may explicitly tap the pending notification instead.
        CaptureDebug.note("悬浮层不可用，已保留待入账通知 pendingId=$pendingId")
        Log.w(TAG, "no overlay available; notification-only pendingId=$pendingId")
    }

    fun buildDedupeKey(candidate: Candidate): String =
        listOf(
            candidate.source,
            candidate.amountCents?.toString().orEmpty(),
            candidate.merchant.trim().lowercase(),
            candidate.occurredAt.epochSecond.toString(),
        ).joinToString("|")
}
