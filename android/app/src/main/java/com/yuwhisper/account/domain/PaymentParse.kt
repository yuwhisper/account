package com.yuwhisper.account.domain

/**
 * Raw notification / accessibility text snapshot before structured parse.
 */
data class ParsedRaw(
    val title: String,
    val text: String,
)

/**
 * Structured payment fields extracted from a payment notification or page.
 */
data class ParsedPayment(
    val amountCents: Int,
    val merchant: String,
    val source: String,
)

/**
 * Parses WeChat / Alipay payment notifications / success pages into amount + merchant.
 */
object PaymentParser {

    /** Strong signals that an *outgoing* payment just finished (safe for accessibility). */
    private val STRONG_SUCCESS_HINTS = listOf(
        "支付成功",
        "付款成功",
        "交易成功",
        "支付完成",
        "你已成功付款",
        "转账成功",
        "已转账",
        "待朋友确认收款",
        "待确认收款",
        "红包发送成功",
        "已发送红包",
        "红包已发送",
        "你发了一个红包",
        "你发了一个拼手气红包",
        "看看大家的手气",
        "未领取的红包",
        "发出红包",
        "红包已发出",
    )

    private val INCOME_HINTS = listOf(
        "收款成功",
        "已存入零钱",
        "收到红包",
        "红包已领取",
        "收款到账",
        "退款成功",
        "退款入账",
        "二维码收款",
    )

    private val SUCCESS_BANNER_EXACT = setOf(
        "支付成功",
        "付款成功",
        "交易成功",
        "支付完成",
        "转账成功",
        "已转账",
        "红包发送成功",
        "红包已发送",
        "已发送红包",
        "你发了一个红包",
        "你发了一个拼手气红包",
        "待朋友确认收款",
        "待确认收款",
    )

    private val RED_PACKET_EDITOR_HINTS = listOf(
        "塞钱进红包",
        "塞进红包",
        "单个金额",
        "红包个数",
    )

    /** Weaker copy — OK for notifications, too noisy alone on WeChat UI tree. */
    private val WEAK_SUCCESS_HINTS = listOf(
        "付款给",
        "成功向",
        "支付金额",
        "付款金额",
        "交易金额",
        "支付凭证",
        "收款方商家",
        "对外支付",
        "微信支付凭证",
        "发红包",
        "微信红包",
        "拼手气红包",
        "普通红包",
    )

    private val AMOUNT_PATTERNS = listOf(
        Regex("""支付金额[：:]\s*[¥￥]?\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""付款金额[：:]\s*[¥￥]?\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""交易金额[：:]\s*[¥￥]?\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""红包金额[：:]\s*[¥￥]?\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""金额[：:]\s*[¥￥]?\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""[¥￥]\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""([\d,]+(?:\.\d{1,2})?)\s*元"""),
        Regex("""RMB\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE),
    )

    private val NODE_MONEY = Regex("""^[¥￥]?\s*([\d,]+)\.(\d{1,2})\s*$""")
    private val NODE_YUAN_ONLY = Regex("""^[¥￥]\s*([\d,]+(?:\.\d{1,2})?)\s*$""")
    private val NODE_DECIMAL = Regex("""^([\d,]+)\.(\d{1,2})$""")

    private val WECHAT_MERCHANT_PATTERNS = listOf(
        Regex("""收款方商家[：:]\s*(.+)"""),
        Regex("""商户全称[：:]\s*(.+)"""),
        Regex("""商户[：:]\s*(.+)"""),
        Regex("""收款方[：:]\s*(.+)"""),
        Regex("""付款给\s*(.+)"""),
        Regex("""向\s*(.+?)\s*付款"""),
        Regex("""给\s*(.+?)\s*的红包"""),
        Regex("""发给\s*(.+)"""),
        Regex("""转账给\s*(.+)"""),
        Regex("""等待\s*(.+?)\s*领取"""),
    )

    private val ALIPAY_MERCHANT_PATTERNS = listOf(
        Regex("""商户[：:]\s*(.+)"""),
        Regex("""收款方[：:]\s*(.+)"""),
        Regex("""付款给\s*(.+)"""),
        Regex("""向\s*(.+?)\s*付款"""),
    )

    fun parseWechat(raw: ParsedRaw): ParsedPayment? =
        parseFields(raw, source = "wechat", merchantPatterns = WECHAT_MERCHANT_PATTERNS)

    fun parseAlipay(raw: ParsedRaw): ParsedPayment? =
        parseFields(raw, source = "alipay", merchantPatterns = ALIPAY_MERCHANT_PATTERNS)

    fun parse(packageName: String, title: String, text: String): ParsedPayment? {
        val raw = ParsedRaw(title = title, text = text)
        return when (packageName) {
            DefaultWatchApps.WECHAT -> parseWechat(raw)
            DefaultWatchApps.ALIPAY -> parseAlipay(raw)
            else -> parseGeneric(packageName, raw)
        }
    }

    fun parseAccessibility(packageName: String, texts: List<String>): ParsedPayment? {
        if (texts.isEmpty()) return null
        val normalized = texts.map { normalizeMoneyText(it) }.filter { it.isNotBlank() }
        val spaced = normalized.joinToString(" ")
        val lined = normalized.joinToString("\n")
        // Do not run the broad bare-number fallback over the whole accessibility tree:
        // dates, discounts, balances and version numbers can otherwise become the amount.
        val amount = extractAmountCents(spaced)
            ?: extractAmountCents(lined)
            ?: extractAmountFromNodes(normalized)
            ?: return null
        val merchant = extractMerchant(spaced, merchantPatternsFor(packageName)).orEmpty()
        return ParsedPayment(
            amountCents = amount,
            merchant = merchant,
            source = sourceForPackage(packageName),
        )
    }

    fun looksLikePaymentSuccess(blob: String): Boolean =
        STRONG_SUCCESS_HINTS.any { blob.contains(it) } ||
            WEAK_SUCCESS_HINTS.any { blob.contains(it) }

    fun looksLikeIncome(blob: String): Boolean {
        if (blob.contains("已存入对方零钱")) return false
        return INCOME_HINTS.any { blob.contains(it) }
    }

    fun looksLikeStrongPaymentSuccess(blob: String): Boolean {
        if (looksLikeIncome(blob)) return false
        if (STRONG_SUCCESS_HINTS.any { blob.contains(it) }) return true
        // Transfer result without the exact "转账成功" string. Do NOT use 零钱 —
        // Alipay home shows 转账入口 + 余额/零钱 + 金额, which would fire every visit.
        if (blob.contains("转账") && (
                blob.contains("确认收款") ||
                    blob.contains("已转账") ||
                    blob.contains("转账成功")
                ) && extractAmountCents(blob) != null
        ) {
            return true
        }
        if (blob.contains("红包") && looksLikeRedPacketSendCopy(blob) &&
            extractAmountCents(blob) != null
        ) {
            return true
        }
        return false
    }

    fun hasDedicatedSuccessBanner(texts: List<String>): Boolean =
        texts.any { t ->
            val trimmed = t.trim()
            trimmed in SUCCESS_BANNER_EXACT ||
                trimmed.contains("待朋友确认收款") ||
                trimmed.contains("未领取的红包") ||
                trimmed.contains("看看大家的手气") ||
                trimmed.contains("你已成功付款") ||
                (trimmed.contains("红包") && (
                    trimmed.contains("已发送") ||
                        trimmed.contains("发送成功") ||
                        trimmed.contains("已发出")
                    ))
        }

    fun looksLikeWeChatChatShell(texts: List<String>): Boolean =
        texts.any {
            it == "按住 说话" || it == "按住说话" ||
                it.contains("切换到键盘") || it.contains("切换到语音")
        }

    fun looksLikeAlipayHome(texts: List<String>): Boolean {
        if (hasDedicatedSuccessBanner(texts)) return false
        val tabs = listOf("首页", "理财", "消息", "我的")
        val tabHits = tabs.count { tab -> texts.any { it.trim() == tab } }
        if (tabHits >= 3) return true
        val homeBits = listOf("余额", "花呗", "余额宝", "银行卡").count { key ->
            texts.any { it.contains(key) }
        }
        val shortcuts = texts.any {
            val t = it.trim()
            t == "转账" || t == "出行" || t == "信用卡" || t == "买单"
        }
        return homeBits >= 2 && shortcuts
    }

    fun looksLikeRedPacketEditor(texts: List<String>): Boolean {
        val blob = texts.joinToString(" ")
        return RED_PACKET_EDITOR_HINTS.any { blob.contains(it) }
    }

    fun looksLikeRedPacketSendResult(texts: List<String>): Boolean {
        if (looksLikeRedPacketEditor(texts)) return false
        if (looksLikeWeChatChatShell(texts)) return false
        val blob = texts.joinToString(" ")
        if (!blob.contains("红包")) return false
        if (looksLikeIncome(blob)) return false
        val hasAmount = extractAmountCents(blob) != null || extractAmountFromNodes(texts) != null
        if (!hasAmount) return false
        return looksLikeRedPacketSendCopy(blob) ||
            texts.any { it.trim() == "已发送" || it.trim() == "完成" }
    }

    /**
     * Accessibility: only a dedicated success / send-result page, never home or chat.
     */
    fun shouldOfferConfirmFromNodes(packageName: String, texts: List<String>): Boolean {
        if (texts.isEmpty()) return false
        val blob = texts.joinToString(" ")
        if (looksLikeIncome(blob)) return false
        if (packageName == DefaultWatchApps.ALIPAY && looksLikeAlipayHome(texts)) return false
        if (looksLikeRedPacketEditor(texts)) return false
        if (packageName == DefaultWatchApps.WECHAT &&
            looksLikeWeChatChatShell(texts) &&
            !hasDedicatedSuccessBanner(texts)
        ) {
            return false
        }
        if (hasDedicatedSuccessBanner(texts)) return true
        if (looksLikeRedPacketSendResult(texts)) return true
        return false
    }

    /**
     * Notifications: strong/weak + WeChat title heuristics.
     * Alipay notifications require a real success phrase (not just title「支付宝」+ amount).
     */
    fun shouldOfferConfirm(
        packageName: String,
        title: String,
        text: String,
        accessibilityMode: Boolean = false,
    ): Boolean {
        val blob = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
        if (blob.isBlank()) return false
        if (looksLikeIncome(blob)) return false
        if (accessibilityMode) {
            return looksLikeStrongPaymentSuccess(blob)
        }
        if (packageName == DefaultWatchApps.ALIPAY) {
            return looksLikeStrongPaymentSuccess(blob)
        }
        if (parse(packageName, title, text) != null) {
            return looksLikePaymentSuccess(blob) ||
                title.contains("微信支付") ||
                title.contains("支付凭证") ||
                title.contains("微信红包") ||
                title.contains("红包") ||
                title.contains("付款成功") ||
                title.contains("转账")
        }
        if (looksLikePaymentSuccess(blob)) return true
        if (packageName == DefaultWatchApps.WECHAT) {
            if (title.contains("微信支付") || title.contains("支付凭证") ||
                title.contains("红包") || title.contains("转账")
            ) {
                return extractAmountCents(blob) != null || looksLikePaymentSuccess(blob)
            }
        }
        return false
    }

    private fun looksLikeRedPacketSendCopy(blob: String): Boolean =
        blob.contains("已发送") ||
            blob.contains("发送成功") ||
            blob.contains("已发出") ||
            blob.contains("你发了") ||
            blob.contains("手气") ||
            blob.contains("未领取") ||
            blob.contains("等待对方领取")

    private fun merchantPatternsFor(packageName: String): List<Regex> = when (packageName) {
        DefaultWatchApps.ALIPAY -> ALIPAY_MERCHANT_PATTERNS
        else -> WECHAT_MERCHANT_PATTERNS
    }

    private fun sourceForPackage(packageName: String): String = when (packageName) {
        DefaultWatchApps.WECHAT -> "wechat"
        DefaultWatchApps.ALIPAY -> "alipay"
        DefaultWatchApps.UNIONPAY -> "unionpay"
        else -> packageName.substringAfterLast('.').ifBlank { "other" }
    }

    private fun parseGeneric(packageName: String, raw: ParsedRaw): ParsedPayment? {
        val blob = listOf(raw.title, raw.text).joinToString("\n")
        val amountCents = extractAmountCents(blob) ?: return null
        val merchant = extractMerchant(
            blob,
            WECHAT_MERCHANT_PATTERNS + ALIPAY_MERCHANT_PATTERNS,
        ).orEmpty()
        return ParsedPayment(
            amountCents = amountCents,
            merchant = merchant,
            source = sourceForPackage(packageName),
        )
    }

    private fun parseFields(
        raw: ParsedRaw,
        source: String,
        merchantPatterns: List<Regex>,
    ): ParsedPayment? {
        val blob = normalizeMoneyText(listOf(raw.title, raw.text).joinToString("\n"))
        val amountCents = extractAmountCents(blob)
            ?: extractBareAmountNearPayment(blob)
            ?: return null
        val merchant = extractMerchant(blob, merchantPatterns).orEmpty()
        return ParsedPayment(
            amountCents = amountCents,
            merchant = merchant,
            source = source,
        )
    }

    fun extractAmountCents(blob: String): Int? {
        val normalized = normalizeMoneyText(blob)
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(normalized) ?: continue
            parseYuanToCents(match.groupValues[1])?.let { return it }
        }
        return null
    }

    /**
     * Prefer a money-looking node on the success page (e.g. large "12.34" / "¥12.34").
     */
    fun extractAmountFromNodes(texts: List<String>): Int? {
        val currencyCandidates = ArrayList<Int>()
        val contextualCandidates = ArrayList<Pair<Int, Int>>()
        val bareCandidates = ArrayList<Pair<Int, Int>>()
        val successIndex = texts.indexOfFirst {
            it.contains("支付成功") || it.contains("付款成功") || it.contains("交易成功") ||
                it.contains("转账成功") || it.contains("待朋友确认收款") || it.contains("待确认收款") ||
                it.contains("红包") || it.contains("已发送") ||
                it.contains("已发出") || it.contains("手气") || it.contains("领取") ||
                it.contains("你发了")
        }
        for (i in texts.indices) {
            val t = normalizeMoneyText(texts[i])
            val moneyMatch = NODE_MONEY.matchEntire(t)
            if (moneyMatch != null) {
                val cents = parseYuanToCents("${moneyMatch.groupValues[1]}.${moneyMatch.groupValues[2]}")
                if (cents != null) {
                    if (t.startsWith("¥")) currencyCandidates.add(cents)
                    val previous = texts.getOrNull(i - 1).orEmpty()
                    if (previous.contains("金额") || previous == "¥" || previous == "￥") {
                        contextualCandidates.add(0 to cents)
                    }
                    val distance = if (successIndex >= 0) kotlin.math.abs(i - successIndex) else Int.MAX_VALUE
                    bareCandidates.add(distance to cents)
                }
                continue
            }
            val yuanOnly = NODE_YUAN_ONLY.matchEntire(t)
            if (yuanOnly != null) {
                parseYuanToCents(yuanOnly.groupValues[1])?.let { currencyCandidates.add(it) }
                continue
            }
            val decimal = NODE_DECIMAL.matchEntire(t)
            if (decimal != null) {
                parseYuanToCents("${decimal.groupValues[1]}.${decimal.groupValues[2]}")?.let {
                    val previous = texts.getOrNull(i - 1).orEmpty()
                    if (previous.contains("金额") || previous == "¥" || previous == "￥") {
                        contextualCandidates.add(0 to it)
                    }
                }
                continue
            }
            // Adjacent: "¥" + "12.34" or "¥" + "12" + ".34" or "1" + "元"
            if (t == "¥" || t == "￥") {
                val next = texts.getOrNull(i + 1)?.let { normalizeMoneyText(it) }.orEmpty()
                val next2 = texts.getOrNull(i + 2)?.let { normalizeMoneyText(it) }.orEmpty()
                parseYuanToCents(next)?.let { currencyCandidates.add(it) }
                if (next2.startsWith(".")) {
                    parseYuanToCents(next + next2)?.let { currencyCandidates.add(it) }
                }
            }
            val nextNode = texts.getOrNull(i + 1)?.trim().orEmpty()
            if ((nextNode == "元" || nextNode == "块") && t.matches(Regex("""^[\d,]+(?:\.\d{1,2})?$"""))) {
                parseYuanToCents(t)?.let { contextualCandidates.add(0 to it) }
            }
        }
        fun valid(value: Int) = value in 1..10_000_000
        return currencyCandidates.firstOrNull(::valid)
            ?: contextualCandidates.sortedBy { it.first }.map { it.second }.firstOrNull(::valid)
            ?: bareCandidates.sortedBy { it.first }.map { it.second }.firstOrNull(::valid)
    }

    private fun extractBareAmountNearPayment(blob: String): Int? {
        if (!looksLikeStrongPaymentSuccess(blob)) return null
        val bare = Regex("""(?<![\d.])(\d{1,7}\.\d{2})(?![\d])""")
        val matches = bare.findAll(blob).mapNotNull { parseYuanToCents(it.groupValues[1]) }.toList()
        return matches.filter { it in 1..10_000_000 }.maxOrNull()
    }

    /** Fullwidth digits / exotic yen → ASCII for regex. */
    fun normalizeMoneyText(raw: String): String {
        if (raw.isEmpty()) return raw
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '￥', '¥', '圆' -> sb.append('¥')
                in '０'..'９' -> sb.append('0' + (ch - '０'))
                '．', '。' -> sb.append('.')
                '，' -> sb.append(',')
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    private fun parseYuanToCents(raw: String): Int? {
        val yuan = normalizeMoneyText(raw).replace(",", "").removePrefix("¥")
        val parts = yuan.split('.')
        val yuanPart = parts[0].toIntOrNull() ?: return null
        val fenPart = when (parts.size) {
            1 -> 0
            else -> parts[1].padEnd(2, '0').take(2).toIntOrNull() ?: return null
        }
        return yuanPart * 100 + fenPart
    }

    private fun extractMerchant(blob: String, patterns: List<Regex>): String? {
        for (pattern in patterns) {
            val match = pattern.find(blob) ?: continue
            val value = match.groupValues[1].trim().lineSequence().firstOrNull()?.trim().orEmpty()
            if (value.isNotEmpty() && value.length <= 40) return value
        }
        return null
    }
}
