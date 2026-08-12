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
 * Parses WeChat / Alipay payment notifications into amount + merchant.
 */
object PaymentParser {

    private val AMOUNT_PATTERNS = listOf(
        Regex("""支付金额[：:]\s*[¥￥]\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""金额[：:]\s*[¥￥]\s*([\d,]+(?:\.\d{1,2})?)"""),
        Regex("""[¥￥]\s*([\d,]+(?:\.\d{1,2})?)"""),
    )

    private val WECHAT_MERCHANT_PATTERNS = listOf(
        Regex("""收款方商家[：:]\s*(.+)"""),
        Regex("""商户[：:]\s*(.+)"""),
        Regex("""收款方[：:]\s*(.+)"""),
    )

    private val ALIPAY_MERCHANT_PATTERNS = listOf(
        Regex("""商户[：:]\s*(.+)"""),
        Regex("""收款方[：:]\s*(.+)"""),
        Regex("""付款给\s*(.+)"""),
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
            else -> null
        }
    }

    private fun parseFields(
        raw: ParsedRaw,
        source: String,
        merchantPatterns: List<Regex>,
    ): ParsedPayment? {
        val blob = listOf(raw.title, raw.text).joinToString("\n")
        val amountCents = extractAmountCents(blob) ?: return null
        val merchant = extractMerchant(blob, merchantPatterns).orEmpty()
        return ParsedPayment(
            amountCents = amountCents,
            merchant = merchant,
            source = source,
        )
    }

    private fun extractAmountCents(blob: String): Int? {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(blob) ?: continue
            val yuan = match.groupValues[1].replace(",", "")
            val parts = yuan.split('.')
            val yuanPart = parts[0].toIntOrNull() ?: continue
            val fenPart = when (parts.size) {
                1 -> 0
                else -> parts[1].padEnd(2, '0').take(2).toIntOrNull() ?: continue
            }
            return yuanPart * 100 + fenPart
        }
        return null
    }

    private fun extractMerchant(blob: String, patterns: List<Regex>): String? {
        for (pattern in patterns) {
            val match = pattern.find(blob) ?: continue
            val value = match.groupValues[1].trim().lineSequence().firstOrNull()?.trim().orEmpty()
            if (value.isNotEmpty()) return value
        }
        return null
    }
}
