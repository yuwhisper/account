package com.yuwhisper.account.capture

import android.content.Context
import android.content.Intent
import android.util.Log
import com.yuwhisper.account.AccountApp
import com.yuwhisper.account.data.local.entity.PendingPaymentEntity
import com.yuwhisper.account.domain.Candidate
import com.yuwhisper.account.domain.Dedupe
import com.yuwhisper.account.ui.confirm.ConfirmPaymentActivity
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Unified entry for payment candidates from notification / accessibility / debug.
 *
 * Flow: dedupe → write pending → try confirm UI → fallback「待入账」notification.
 * There is no ignore path; back from confirm keeps the pending row.
 */
object ConfirmDispatcher {

    const val DEDUPE_WINDOW_SECONDS = 120
    private const val TAG = "ConfirmDispatcher"

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
        app.applicationScope.launch {
            runCatching {
                handle(
                    app = app,
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
        candidate: Candidate,
        captureChannel: String,
        rawText: String,
    ) {
        app.ensureSeeded()
        val repo = app.ledgerRepository
        val existing = repo.listDedupeCandidates()
        if (Dedupe.isDuplicate(candidate, existing, DEDUPE_WINDOW_SECONDS)) {
            Log.i(TAG, "duplicate skipped: ${candidate.source} ${candidate.amountCents} ${candidate.merchant}")
            return
        }

        val now = Instant.now()
        val pendingId = repo.insertPendingPayment(
            PendingPaymentEntity(
                amountCents = candidate.amountCents.toLong(),
                merchant = candidate.merchant,
                source = candidate.source,
                captureChannel = captureChannel,
                rawText = rawText,
                dedupeKey = buildDedupeKey(candidate),
                occurredAt = candidate.occurredAt,
                createdAt = now,
            ),
        )

        val started = tryStartConfirm(app, pendingId)
        if (!started) {
            PendingPaymentNotifier.show(
                context = app,
                pendingId = pendingId,
                amountCents = candidate.amountCents.toLong(),
                merchant = candidate.merchant,
                source = candidate.source,
            )
        }
    }

    private fun tryStartConfirm(app: AccountApp, pendingId: Long): Boolean {
        val intent = ConfirmPaymentActivity.createIntent(app, pendingId).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return runCatching {
            app.startActivity(intent)
            true
        }.getOrElse { t ->
            Log.w(TAG, "start ConfirmPaymentActivity failed", t)
            false
        }
    }

    fun buildDedupeKey(candidate: Candidate): String =
        listOf(
            candidate.source,
            candidate.amountCents.toString(),
            candidate.merchant.trim().lowercase(),
            candidate.occurredAt.epochSecond.toString(),
        ).joinToString("|")
}
