package com.yuwhisper.account.capture

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.yuwhisper.account.AccountApp
import com.yuwhisper.account.domain.Candidate
import com.yuwhisper.account.domain.PaymentParser
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Reads payment / red-packet / transfer success screens via accessibility tree text.
 *
 * Hosts [AccessibilityConfirmOverlay] so confirm UI stays over WeChat without jumping
 * into the ledger app (TYPE_ACCESSIBILITY_OVERLAY).
 *
 * Important: only scan the active window, and skip WeChat chat shells — chat history
 * often contains 「等待对方领取 / 红包 / ¥」and would false-trigger + dedupe real pays.
 */
class PaymentAccessibilityService : AccessibilityService() {

    @Volatile
    private var lastEmitPackage: String? = null

    @Volatile
    private var lastEmitAmount: Int? = null

    @Volatile
    private var lastEmitAtMs: Long = 0L

    @Volatile
    private var pendingPackage: String? = null

    @Volatile
    private var lastScanPkg: String? = null

    @Volatile
    private var scanGeneration: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var initialScanScheduled = false
    private var confirmOverlay: AccessibilityConfirmOverlay? = null

    private val scanRunnable = Runnable {
        initialScanScheduled = false
        val pkg = pendingPackage ?: return@Runnable
        pendingPackage = null
        lastScanPkg = pkg
        val generation = ++scanGeneration
        listOf(0L, 350L, 800L, 1500L, 2500L, 4000L).forEachIndexed { pass, delayMs ->
            mainHandler.postDelayed(
                {
                    if (generation != scanGeneration) return@postDelayed
                    lastScanPkg?.let { scanPackage(it, pass) }
                },
                delayMs,
            )
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        confirmOverlay = AccessibilityConfirmOverlay(this)
        AutoBookkeepingStatusService.refresh(applicationContext)
        Log.i(TAG, "accessibility connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        mainHandler.removeCallbacksAndMessages(null)
        confirmOverlay?.destroy()
        confirmOverlay = null
        if (instance === this) instance = null
        AutoBookkeepingStatusService.refresh(applicationContext)
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        AutoBookkeepingStatusService.refresh(applicationContext)
    }

    fun showConfirmOverlay(pendingId: Long): Boolean {
        val host = confirmOverlay ?: return false
        return host.show(pendingId)
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

        // CONTENT_CHANGED floods chat typing; only continue when the event itself looks payment-like.
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            !eventLooksPaymentRelated(event)
        ) {
            return
        }

        if (lastScanPkg != null && packageName != lastScanPkg && initialScanScheduled) {
            return
        }

        pendingPackage = packageName
        if (!initialScanScheduled) {
            initialScanScheduled = true
            val delay = if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) 100L else 280L
            mainHandler.postDelayed(scanRunnable, delay)
        }
    }

    private fun eventLooksPaymentRelated(event: AccessibilityEvent): Boolean {
        val parts = ArrayList<String>(8)
        event.text?.forEach { parts.add(it?.toString().orEmpty()) }
        event.contentDescription?.toString()?.let { parts.add(it) }
        event.className?.toString()?.let { parts.add(it) }
        val blob = parts.filter { it.isNotBlank() }.joinToString(" ")
        if (blob.isBlank()) return true // empty → still allow (amount may appear later)
        return PaymentParser.looksLikePaymentSuccess(blob) ||
            blob.contains("¥") ||
            blob.contains("￥") ||
            blob.contains("成功") ||
            blob.contains("红包") ||
            blob.contains("转账") ||
            blob.contains("领取") ||
            blob.contains("确认收款")
    }

    private fun scanPackage(packageName: String, pass: Int) {
        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString() ?: packageName
        if (rootPkg == applicationContext.packageName) return
        // Event may be WeChat while a system dialog is briefly active — only scan matching pkg.
        if (rootPkg != packageName) return
        scanRoot(rootPkg, root, pass)
    }

    private fun scanRoot(packageName: String, root: AccessibilityNodeInfo, pass: Int) {
        val texts = ArrayList<String>(96)
        collectTexts(root, texts, depth = 0)
        if (texts.isEmpty()) return

        // Chat history often contains old 红包/转账 text. Only skip when it looks like a
        // normal chat AND there is no dedicated success banner on screen.
        if (looksLikeWeChatChatShell(texts) && !hasDedicatedSuccessBanner(texts)) {
            CaptureDebug.note("跳过聊天页 pkg=$packageName nodes=${texts.size}")
            return
        }

        val spaced = texts.joinToString(" ")
        if (!PaymentParser.shouldOfferConfirm(packageName, "", spaced, accessibilityMode = true)) {
            if (pass == 0 || pass == 3) {
                CaptureDebug.note(
                    "未达成功条件 pass=$pass pkg=$packageName sample=${spaced.take(120)}",
                )
            }
            return
        }

        val parsed = PaymentParser.parseAccessibility(packageName, texts)
        if (parsed == null) {
            CaptureDebug.note("有成功文案但未解析到金额 pass=$pass pkg=$packageName")
            return
        }

        val merchant = parsed.merchant.takeIf { it.isNotBlank() }
            ?: guessMerchant(texts)
            ?: "未知商户"
        val amountCents = parsed.amountCents
        val now = System.currentTimeMillis()
        if (lastEmitPackage == packageName &&
            lastEmitAmount == amountCents &&
            now - lastEmitAtMs < AMOUNT_COOLDOWN_MS
        ) {
            CaptureDebug.note("同金额冷却中 amount=$amountCents")
            return
        }

        lastEmitPackage = packageName
        lastEmitAmount = amountCents
        lastEmitAtMs = now
        scanGeneration++

        val app = applicationContext as? AccountApp ?: return
        val service = this
        app.applicationScope.launch {
            runCatching {
                app.ensureSeeded()
                if (!app.ledgerRepository.isWatchAppEnabled(packageName)) {
                    CaptureDebug.note("识别场景未开启 pkg=$packageName")
                    return@runCatching
                }

                CaptureDebug.note("捕获成功 pass=$pass amount=$amountCents merchant=$merchant")
                ConfirmDispatcher.onPaymentDetected(
                    context = service,
                    candidate = Candidate(
                        source = parsed.source,
                        amountCents = amountCents,
                        merchant = merchant,
                        occurredAt = Instant.now(),
                    ),
                    captureChannel = CHANNEL,
                    rawText = spaced.take(2000),
                )
            }.onFailure { t ->
                Log.e(TAG, "scanRoot failed", t)
                CaptureDebug.note("捕获异常: ${t.message}")
            }
        }
    }

    private fun looksLikeWeChatChatShell(texts: List<String>): Boolean {
        // Only treat as chat when the voice/keyboard input bar is clearly present.
        return texts.any {
            it == "按住 说话" || it == "按住说话" ||
                it.contains("切换到键盘") || it.contains("切换到语音")
        }
    }

    /** Phrases that appear on result pages, not as ordinary chat bubbles. */
    private fun hasDedicatedSuccessBanner(texts: List<String>): Boolean {
        return texts.any { t ->
            t.contains("支付成功") ||
                t.contains("付款成功") ||
                t.contains("转账成功") ||
                t.contains("待朋友确认收款") ||
                t.contains("待确认收款") ||
                t.contains("看看大家的手气") ||
                t.contains("红包发送成功") ||
                t.contains("你发了一个红包") ||
                t.contains("你发了一个拼手气红包") ||
                t.contains("红包已发送") ||
                t == "已发送"
        }
    }

    private fun guessMerchant(texts: List<String>): String? {
        for (t in texts) {
            Regex("""给\s*(.+?)\s*的红包""").find(t)?.groupValues?.getOrNull(1)
                ?.trim()?.takeIf { it.length in 1..20 }?.let { return it }
            Regex("""发给\s*(.+)""").find(t)?.groupValues?.getOrNull(1)
                ?.trim()?.takeIf { it.length in 1..20 && !it.contains("红包") }?.let { return it }
            Regex("""转账给\s*(.+)""").find(t)?.groupValues?.getOrNull(1)
                ?.trim()?.takeIf { it.length in 1..20 }?.let { return it }
            Regex("""待\s*(.+?)\s*确认收款""").find(t)?.groupValues?.getOrNull(1)
                ?.trim()?.takeIf { it.length in 1..20 }?.let { return it }
        }
        val idx = texts.indexOfFirst {
            it.contains("收款方") || it == "商户" || it.contains("商家") ||
                it.contains("转账") || it.contains("好友")
        }
        if (idx >= 0) {
            texts.getOrNull(idx + 1)?.trim()
                ?.takeIf {
                    it.length in 2..30 &&
                        !it.contains("成功") &&
                        !it.contains("红包") &&
                        !it.contains("确认")
                }
                ?.let { return it }
        }
        return null
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > MAX_DEPTH) return
        val text = node.text?.toString()?.trim().orEmpty()
        if (text.isNotEmpty()) out.add(text)
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        if (desc.isNotEmpty() && desc != text) out.add(desc)
        if (Build.VERSION.SDK_INT >= 26) {
            val hint = node.hintText?.toString()?.trim().orEmpty()
            if (hint.isNotEmpty() && hint != text && hint != desc) out.add(hint)
        }
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out, depth + 1)
        }
    }

    companion object {
        private const val TAG = "PaymentA11yService"
        const val CHANNEL = "accessibility"
        private const val MAX_DEPTH = 48
        private const val AMOUNT_COOLDOWN_MS = 6_000L

        @Volatile
        var instance: PaymentAccessibilityService? = null
            private set
    }
}
