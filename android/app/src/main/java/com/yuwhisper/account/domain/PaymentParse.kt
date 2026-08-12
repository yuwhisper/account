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

    /** Strong signals that a payment just finished (safe for accessibility). */
    private val STRONG_SUCCESS_HINTS = listOf(
        "支付成功",
        "付款成功",
        "交易成功",
        "支付完成",
        "已支付",
        "收款成功",
        "你已成功付款",
        "扣款成功",
        "已付款",
        "支付成功，",
        "转账成功",
        "已转账",
        "待朋友确认收款",
        "待确认收款",
        "朋友已确认收款",
        // WeChat red packet (send / claim result pages)
        "红包发送成功",
        "已发送红包",
        "红包已发送",
        "你发了一个红包",
        "你发了一个拼手气红包",
        "看看大家的手气",
        "等待对方领取",
        "红包金额",
        "已存入对方零钱",
        "已存入零钱",
        "发出红包",
        "红包已发出",
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
    private val NODE_DECIMAL = Regex("""^([\d,]+)\.(\d{2})$""")

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

    fun looksLikeStrongPaymentSuccess(blob: String): Boolean {
        if (STRONG_SUCCESS_HINTS.any { blob.contains(it) }) return true
        // Transfer result without the exact "转账成功" string.
        if ((blob.contains("转账") && (
                blob.contains("确认收款") ||
                    blob.contains("已转账") ||
                    blob.contains("转账成功") ||
                    blob.contains("零钱")
                )) && extractAmountCents(blob) != null
        ) {
            return true
        }
        // Red-packet result page often shows "红包" without "支付成功".
        // Avoid pre-pay "塞钱进红包" editor and chat history noise.
        if (blob.contains("红包") && (
                blob.contains("已发送") ||
                    blob.contains("发送成功") ||
                    blob.contains("已发出") ||
                    blob.contains("你发了") ||
                    blob.contains("手气") ||
                    blob.contains("等待对方领取") ||
                    blob.contains("已存入")
                ) && extractAmountCents(blob) != null
        ) {
            return true
        }
        return false
    }

    /**
     * Accessibility: only strong success / red-packet success (or already-parsed amount on a pay page).
     * Notifications: strong/weak + WeChat/Alipay title heuristics.
     */
    fun shouldOfferConfirm(
        packageName: String,
        title: String,
        text: String,
        accessibilityMode: Boolean = false,
    ): Boolean {
        val blob = listOf(title, text).filter { it.isNotBlank() }.joinToString("\n")
        if (blob.isBlank()) return false
        if (accessibilityMode) {
            if (looksLikeStrongPaymentSuccess(blob)) return true
            // Parsed amount alone is not enough on a11y (chat noise); require pay/red-packet context.
            return false
        }
        if (parse(packageName, title, text) != null) {
            return looksLikePaymentSuccess(blob) ||
                title.contains("微信支付") ||
                title.contains("支付凭证") ||
                title.contains("微信红包") ||
                title.contains("红包") ||
                title.contains("支付宝") ||
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
        if (packageName == DefaultWatchApps.ALIPAY) {
            if (title.contains("支付宝") || title.contains("付款")) {
                return extractAmountCents(blob) != null || looksLikePaymentSuccess(blob)
            }
        }
        return false
    }

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
            // Adjacent: "¥" + "12.34" or "¥" + "12" + ".34"
            if (t == "¥" || t == "￥") {
                val next = texts.getOrNull(i + 1)?.let { normalizeMoneyText(it) }.orEmpty()
                val next2 = texts.getOrNull(i + 2)?.let { normalizeMoneyText(it) }.orEmpty()
                parseYuanToCents(next)?.let { currencyCandidates.add(it) }
                if (next2.startsWith(".")) {
                    parseYuanToCents(next + next2)?.let { currencyCandidates.add(it) }
                }
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
