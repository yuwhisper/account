package com.yuwhisper.account.capture

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yuwhisper.account.AccountApp
import com.yuwhisper.account.domain.Candidate
import com.yuwhisper.account.domain.PaymentParser
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Reads payment-success screens via accessibility tree text,
 * then forwards candidates to [ConfirmDispatcher] (shared dedupe with notification channel).
 *
 * Known limit: page structure / copy varies by App version; heuristics may miss or misfire.
 */
class PaymentAccessibilityService : AccessibilityService() {

    @Volatile
    private var lastFingerprint: String? = null

    @Volatile
    private var lastHandledAtMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoBookkeepingStatusService.refresh(applicationContext)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        AutoBookkeepingStatusService.refresh(applicationContext)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        AutoBookkeepingStatusService.refresh(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }
        if (!AutoBookkeepingPrefs.isMasterEnabled(this)) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return

        // Collect on the service (main) thread — rootInActiveWindow is not thread-safe.
        val root = rootInActiveWindow ?: return
        val texts = ArrayList<String>(64)
        collectTexts(root, texts, depth = 0)
        if (texts.isEmpty()) return

        val blob = texts.joinToString("\n")
        if (!PaymentParser.looksLikePaymentSuccess(blob)) return

        val app = applicationContext as? AccountApp ?: return
        app.applicationScope.launch {
            runCatching {
                app.ensureSeeded()
                if (!app.ledgerRepository.isWatchAppEnabled(packageName)) {
                    return@runCatching
                }

                val parsed = PaymentParser.parseAccessibility(packageName, texts) ?: return@runCatching
                val fingerprint = "$packageName|${parsed.amountCents}|${parsed.merchant}"
                val now = System.currentTimeMillis()
                if (fingerprint == lastFingerprint && now - lastHandledAtMs < LOCAL_DEBOUNCE_MS) {
                    return@runCatching
                }
                lastFingerprint = fingerprint
                lastHandledAtMs = now

                ConfirmDispatcher.onPaymentDetected(
                    context = app,
                    candidate = Candidate(
                        source = parsed.source,
                        amountCents = parsed.amountCents,
                        merchant = parsed.merchant,
                        occurredAt = Instant.now(),
                    ),
                    captureChannel = CHANNEL,
                    rawText = blob.take(2000),
                )
            }.onFailure { t ->
                Log.e(TAG, "onAccessibilityEvent failed", t)
            }
        }
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > MAX_DEPTH) return
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) out.add(text)
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty() && desc != text) out.add(desc)
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out, depth + 1)
        }
    }

    companion object {
        private const val TAG = "PaymentA11yService"
        const val CHANNEL = "accessibility"
        private const val MAX_DEPTH = 40
        private const val LOCAL_DEBOUNCE_MS = 2_500L
    }
}
