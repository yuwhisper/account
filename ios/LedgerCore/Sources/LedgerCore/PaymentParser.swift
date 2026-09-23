import Foundation

public struct ParsedRaw: Equatable {
    public var title: String
    public var text: String

    public init(title: String, text: String) {
        self.title = title
        self.text = text
    }
}

public struct ParsedPayment: Equatable {
    public var amountCents: Int
    public var merchant: String
    public var source: String

    public init(amountCents: Int, merchant: String, source: String) {
        self.amountCents = amountCents
        self.merchant = merchant
        self.source = source
    }
}

public enum WatchApps {
    public static let wechat = "com.tencent.mm"
    public static let alipay = "com.eg.android.AlipayGphone"
    public static let unionPay = "com.unionpay"
}

/// WeChat / Alipay payment text parser. Behavior matches the Android `PaymentParser`.
public enum PaymentParser {
    private static let strongSuccessHints = [
        "支付成功", "付款成功", "交易成功", "支付完成", "你已成功付款",
        "转账成功", "已转账", "待朋友确认收款", "待确认收款",
        "红包发送成功", "已发送红包", "红包已发送",
        "你发了一个红包", "你发了一个拼手气红包",
        "看看大家的手气", "未领取的红包", "发出红包", "红包已发出",
    ]

    private static let incomeHints = [
        "收款成功", "已存入零钱", "收到红包", "红包已领取",
        "收款到账", "退款成功", "退款入账", "二维码收款",
    ]

    private static let successBannerExact: Set<String> = [
        "支付成功", "付款成功", "交易成功", "支付完成", "转账成功", "已转账", "红包发送成功", "红包已发送", "已发送红包",
    ]

    private static let redPacketEditorHints = ["塞钱进红包", "塞进红包", "单个金额", "红包个数"]

    private static let weakSuccessHints = [
        "付款给", "成功向", "支付金额", "付款金额", "交易金额", "支付凭证",
        "收款方商家", "对外支付", "微信支付凭证", "发红包", "微信红包", "拼手气红包", "普通红包",
    ]

    private static let amountPatterns = [
        #"支付金额[：:]\s*[¥]?\s*([\d,]+(?:\.\d{1,2})?)"#,
        #"付款金额[：:]\s*[¥]?\s*([\d,]+(?:\.\d{1,2})?)"#,
        #"交易金额[：:]\s*[¥]?\s*([\d,]+(?:\.\d{1,2})?)"#,
        #"红包金额[：:]\s*[¥]?\s*([\d,]+(?:\.\d{1,2})?)"#,
        #"金额[：:]\s*[¥]?\s*([\d,]+(?:\.\d{1,2})?)"#,
        #"[¥]\s*([\d,]+(?:\.\d{1,2})?)"#,
        #"([\d,]+(?:\.\d{1,2})?)\s*元"#,
        #"RMB\s*([\d,]+(?:\.\d{1,2})?)"#,
    ]

    private static let wechatMerchantPatterns = [
        #"收款方商家[：:]\s*(.+)"#,
        #"商户全称[：:]\s*(.+)"#,
        #"商户[：:]\s*(.+)"#,
        #"收款方[：:]\s*(.+)"#,
        #"付款给\s*(.+)"#,
        #"向\s*(.+?)\s*付款"#,
        #"给\s*(.+?)\s*的红包"#,
        #"发给\s*(.+)"#,
        #"转账给\s*(.+)"#,
        #"等待\s*(.+?)\s*领取"#,
    ]

    private static let alipayMerchantPatterns = [
        #"商户[：:]\s*(.+)"#,
        #"收款方[：:]\s*(.+)"#,
        #"付款给\s*(.+)"#,
        #"向\s*(.+?)\s*付款"#,
    ]

    public static func parseWechat(_ raw: ParsedRaw) -> ParsedPayment? {
        parseFields(raw, source: "wechat", merchantPatterns: wechatMerchantPatterns)
    }

    public static func parseAlipay(_ raw: ParsedRaw) -> ParsedPayment? {
        parseFields(raw, source: "alipay", merchantPatterns: alipayMerchantPatterns)
    }

    public static func parse(packageName: String, title: String, text: String) -> ParsedPayment? {
        let raw = ParsedRaw(title: title, text: text)
        switch packageName {
        case WatchApps.wechat: return parseWechat(raw)
        case WatchApps.alipay: return parseAlipay(raw)
        default: return parseGeneric(packageName, raw)
        }
    }

    public static func parseAccessibility(packageName: String, texts: [String]) -> ParsedPayment? {
        if texts.isEmpty { return nil }
        let normalized = texts.map(normalizeMoneyText).filter { !$0.isEmpty }
        let spaced = normalized.joined(separator: " ")
        let lined = normalized.joined(separator: "\n")
        guard let amount = extractAmountCents(spaced)
            ?? extractAmountCents(lined)
            ?? extractAmountFromNodes(normalized) else { return nil }
        let merchant = extractMerchant(spaced, patterns: merchantPatterns(for: packageName)) ?? ""
        return ParsedPayment(amountCents: amount, merchant: merchant, source: sourceForPackage(packageName))
    }

    /// OCR text from a payment screenshot. Refuses chat, home, and income pages.
    public static func parseScreenshot(_ text: String) -> ParsedPayment? {
        let lines = text
            .split(whereSeparator: \.isNewline)
            .map { normalizeMoneyText(String($0)) }
            .filter { !$0.isEmpty }
        let blob = lines.joined(separator: "\n")
        if blob.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return nil }
        if looksLikeIncome(blob) { return nil }
        let packageName = packageName(forScreenshot: blob)
        let offer = shouldOfferConfirm(packageName: packageName, title: "", text: blob, accessibilityMode: true)
            || shouldOfferConfirmFromNodes(packageName: packageName, texts: lines)
        if !offer { return nil }
        return parseAccessibility(packageName: packageName, texts: lines)
            ?? parse(packageName: packageName, title: "", text: blob)
    }

    public static func looksLikePaymentSuccess(_ blob: String) -> Bool {
        strongSuccessHints.contains { blob.contains($0) } || weakSuccessHints.contains { blob.contains($0) }
    }

    public static func looksLikeIncome(_ blob: String) -> Bool {
        if blob.contains("已存入对方零钱") { return false }
        return incomeHints.contains { blob.contains($0) }
    }

    public static func looksLikeStrongPaymentSuccess(_ blob: String) -> Bool {
        if looksLikeIncome(blob) { return false }
        if strongSuccessHints.contains(where: { blob.contains($0) }) { return true }
        if blob.contains("转账") &&
            (blob.contains("确认收款") || blob.contains("已转账") || blob.contains("转账成功")) &&
            extractAmountCents(blob) != nil {
            return true
        }
        if blob.contains("红包") && looksLikeRedPacketSendCopy(blob) && extractAmountCents(blob) != nil {
            return true
        }
        return false
    }

    public static func hasDedicatedSuccessBanner(_ texts: [String]) -> Bool {
        texts.contains { raw in
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if successBannerExact.contains(trimmed) { return true }
            if trimmed.contains("待朋友确认收款") || trimmed.contains("未领取的红包") ||
                trimmed.contains("看看大家的手气") || trimmed.contains("你已成功付款") {
                return true
            }
            if trimmed.contains("红包") &&
                (trimmed.contains("已发送") || trimmed.contains("发送成功") || trimmed.contains("已发出")) {
                return true
            }
            return false
        }
    }

    public static func looksLikeWeChatChatShell(_ texts: [String]) -> Bool {
        texts.contains {
            $0 == "按住 说话" || $0 == "按住说话" || $0.contains("切换到键盘") || $0.contains("切换到语音")
        }
    }

    public static func looksLikeAlipayHome(_ texts: [String]) -> Bool {
        if hasDedicatedSuccessBanner(texts) { return false }
        let tabs = ["首页", "理财", "消息", "我的"]
        let tabHits = tabs.filter { tab in texts.contains { $0.trimmingCharacters(in: .whitespacesAndNewlines) == tab } }.count
        if tabHits >= 3 { return true }
        let homeBits = ["余额", "花呗", "余额宝", "银行卡"].filter { key in texts.contains { $0.contains(key) } }.count
        let shortcuts = texts.contains {
            let trimmed = $0.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed == "转账" || trimmed == "出行" || trimmed == "信用卡" || trimmed == "买单"
        }
        return homeBits >= 2 && shortcuts
    }

    public static func looksLikeRedPacketEditor(_ texts: [String]) -> Bool {
        let blob = texts.joined(separator: " ")
        return redPacketEditorHints.contains { blob.contains($0) }
    }

    public static func looksLikeRedPacketSendResult(_ texts: [String]) -> Bool {
        if looksLikeRedPacketEditor(texts) || looksLikeWeChatChatShell(texts) { return false }
        let blob = texts.joined(separator: " ")
        if !blob.contains("红包") || looksLikeIncome(blob) { return false }
        let hasAmount = extractAmountCents(blob) != nil || extractAmountFromNodes(texts) != nil
        if !hasAmount { return false }
        return looksLikeRedPacketSendCopy(blob) ||
            texts.contains {
                let trimmed = $0.trimmingCharacters(in: .whitespacesAndNewlines)
                return trimmed == "已发送" || trimmed == "完成"
            }
    }

    public static func shouldOfferConfirmFromNodes(packageName: String, texts: [String]) -> Bool {
        if texts.isEmpty { return false }
        let blob = texts.joined(separator: " ")
        if looksLikeIncome(blob) { return false }
        if packageName == WatchApps.alipay && looksLikeAlipayHome(texts) { return false }
        if looksLikeRedPacketEditor(texts) { return false }
        if packageName == WatchApps.wechat && looksLikeWeChatChatShell(texts) && !hasDedicatedSuccessBanner(texts) {
            return false
        }
        if hasDedicatedSuccessBanner(texts) { return true }
        if looksLikeRedPacketSendResult(texts) { return true }
        return false
    }

    public static func shouldOfferConfirm(
        packageName: String,
        title: String,
        text: String,
        accessibilityMode: Bool = false
    ) -> Bool {
        let blob = [title, text]
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .joined(separator: "\n")
        if blob.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return false }
        if looksLikeIncome(blob) { return false }
        if accessibilityMode || packageName == WatchApps.alipay {
            return looksLikeStrongPaymentSuccess(blob)
        }
        if parse(packageName: packageName, title: title, text: text) != nil {
            if looksLikePaymentSuccess(blob) ||
                title.contains("微信支付") || title.contains("支付凭证") || title.contains("微信红包") ||
                title.contains("红包") || title.contains("付款成功") || title.contains("转账") {
                return true
            }
        }
        if looksLikePaymentSuccess(blob) { return true }
        if packageName == WatchApps.wechat {
            if title.contains("微信支付") || title.contains("支付凭证") || title.contains("红包") || title.contains("转账") {
                return extractAmountCents(blob) != nil || looksLikePaymentSuccess(blob)
            }
        }
        return false
    }

    public static func extractAmountCents(_ blob: String) -> Int? {
        let normalized = normalizeMoneyText(blob)
        for (index, pattern) in amountPatterns.enumerated() {
            let options: NSRegularExpression.Options = index == amountPatterns.count - 1 ? [.caseInsensitive] : []
            guard let groups = firstMatch(pattern, in: normalized, options: options), groups.count > 1 else { continue }
            if let cents = parseYuanToCents(groups[1]) { return cents }
        }
        return nil
    }

    public static func extractAmountFromNodes(_ texts: [String]) -> Int? {
        var currencyCandidates: [Int] = []
        var contextualCandidates: [(Int, Int)] = []
        var bareCandidates: [(Int, Int)] = []
        let successIndex = texts.firstIndex {
            $0.contains("支付成功") || $0.contains("付款成功") || $0.contains("交易成功") ||
                $0.contains("转账成功") || $0.contains("待朋友确认收款") || $0.contains("待确认收款") ||
                $0.contains("红包") || $0.contains("已发送") || $0.contains("已发出") ||
                $0.contains("手气") || $0.contains("领取") || $0.contains("你发了")
        }
        for index in texts.indices {
            let raw = texts[index]
            let text = normalizeMoneyText(raw)
            if let money = wholeMatch(#"[¥]?\s*([\d,]+)\.(\d{1,2})\s*"#, in: text), money.count > 2 {
                if let cents = parseYuanToCents("\(money[1]).\(money[2])") {
                    if text.hasPrefix("¥") { currencyCandidates.append(cents) }
                    let previous = index > 0 ? texts[index - 1] : ""
                    if previous.contains("金额") || previous == "¥" || previous == "￥" {
                        contextualCandidates.append((0, cents))
                    }
                    let distance = successIndex.map { abs(index - $0) } ?? Int.max
                    bareCandidates.append((distance, cents))
                }
                continue
            }
            if let yuanOnly = wholeMatch(#"[¥]\s*([\d,]+(?:\.\d{1,2})?)\s*"#, in: text), yuanOnly.count > 1 {
                if let cents = parseYuanToCents(yuanOnly[1]) { currencyCandidates.append(cents) }
                continue
            }
            if let decimal = wholeMatch(#"([\d,]+)\.(\d{1,2})"#, in: text), decimal.count > 2 {
                if let cents = parseYuanToCents("\(decimal[1]).\(decimal[2])") {
                    let previous = index > 0 ? texts[index - 1] : ""
                    if previous.contains("金额") || previous == "¥" || previous == "￥" {
                        contextualCandidates.append((0, cents))
                    }
                }
                continue
            }
            if text == "¥" || text == "￥" {
                let next = texts.indices.contains(index + 1) ? normalizeMoneyText(texts[index + 1]) : ""
                let next2 = texts.indices.contains(index + 2) ? normalizeMoneyText(texts[index + 2]) : ""
                if let cents = parseYuanToCents(next) { currencyCandidates.append(cents) }
                if next2.hasPrefix("."), let cents = parseYuanToCents(next + next2) {
                    currencyCandidates.append(cents)
                }
            }
            let nextNode = texts.indices.contains(index + 1)
                ? texts[index + 1].trimmingCharacters(in: .whitespacesAndNewlines)
                : ""
            if (nextNode == "元" || nextNode == "块") && wholeMatch(#"[\d,]+(?:\.\d{1,2})?"#, in: text) != nil {
                if let cents = parseYuanToCents(text) { contextualCandidates.append((0, cents)) }
            }
        }
        func valid(_ value: Int) -> Bool { (1...10_000_000).contains(value) }
        return currencyCandidates.first(where: valid)
            ?? contextualCandidates.sorted { $0.0 < $1.0 }.map(\.1).first(where: valid)
            ?? bareCandidates.sorted { $0.0 < $1.0 }.map(\.1).first(where: valid)
    }

    public static func normalizeMoneyText(_ raw: String) -> String {
        if raw.isEmpty { return raw }
        var result = ""
        result.reserveCapacity(raw.count)
        for character in raw {
            switch character {
            case "￥", "¥", "圆":
                result.append("¥")
            case "０"..."９":
                let digit = character.unicodeScalars.first!.value - ("０" as Character).unicodeScalars.first!.value
                result.append(Character(UnicodeScalar(48 + digit)!))
            case "．", "。":
                result.append(".")
            case "，":
                result.append(",")
            default:
                result.append(character)
            }
        }
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func looksLikeRedPacketSendCopy(_ blob: String) -> Bool {
        blob.contains("已发送") || blob.contains("发送成功") || blob.contains("已发出") ||
            blob.contains("你发了") || blob.contains("手气") || blob.contains("未领取") ||
            blob.contains("等待对方领取")
    }

    private static func merchantPatterns(for packageName: String) -> [String] {
        packageName == WatchApps.alipay ? alipayMerchantPatterns : wechatMerchantPatterns
    }

    private static func sourceForPackage(_ packageName: String) -> String {
        switch packageName {
        case WatchApps.wechat: return "wechat"
        case WatchApps.alipay: return "alipay"
        case WatchApps.unionPay: return "unionpay"
        default:
            let suffix = packageName.split(separator: ".").last.map(String.init) ?? ""
            return suffix.isEmpty ? "other" : suffix
        }
    }

    private static func packageName(forScreenshot blob: String) -> String {
        if blob.contains("云闪付") { return WatchApps.unionPay }
        if blob.contains("支付宝") || blob.contains("花呗") { return WatchApps.alipay }
        if blob.contains("微信") { return WatchApps.wechat }
        return "screenshot"
    }

    private static func parseGeneric(_ packageName: String, _ raw: ParsedRaw) -> ParsedPayment? {
        let blob = [raw.title, raw.text].joined(separator: "\n")
        guard let amountCents = extractAmountCents(blob) else { return nil }
        let merchant = extractMerchant(blob, patterns: wechatMerchantPatterns + alipayMerchantPatterns) ?? ""
        return ParsedPayment(amountCents: amountCents, merchant: merchant, source: sourceForPackage(packageName))
    }

    private static func parseFields(
        _ raw: ParsedRaw,
        source: String,
        merchantPatterns: [String]
    ) -> ParsedPayment? {
        let blob = normalizeMoneyText([raw.title, raw.text].joined(separator: "\n"))
        guard let amountCents = extractAmountCents(blob) ?? extractBareAmountNearPayment(blob) else { return nil }
        let merchant = extractMerchant(blob, patterns: merchantPatterns) ?? ""
        return ParsedPayment(amountCents: amountCents, merchant: merchant, source: source)
    }

    private static func extractBareAmountNearPayment(_ blob: String) -> Int? {
        guard looksLikeStrongPaymentSuccess(blob) else { return nil }
        let pattern = #"(?<![\d.])(\d{1,7}\.\d{2})(?![\d])"#
        let matches = allMatches(pattern, in: blob).compactMap { groups -> Int? in
            groups.count > 1 ? parseYuanToCents(groups[1]) : nil
        }
        return matches.filter { (1...10_000_000).contains($0) }.max()
    }

    private static func parseYuanToCents(_ raw: String) -> Int? {
        let yuan = normalizeMoneyText(raw).replacingOccurrences(of: ",", with: "").replacingOccurrences(of: "¥", with: "")
        let parts = yuan.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        guard let yuanPart = Int(parts.first ?? "") else { return nil }
        let fenPart: Int
        if parts.count == 1 {
            fenPart = 0
        } else {
            let padded = parts[1] + "00"
            guard let fen = Int(String(padded.prefix(2))) else { return nil }
            fenPart = fen
        }
        return yuanPart * 100 + fenPart
    }

    private static func extractMerchant(_ blob: String, patterns: [String]) -> String? {
        for pattern in patterns {
            guard let groups = firstMatch(pattern, in: blob), groups.count > 1 else { continue }
            let value = groups[1]
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .split(whereSeparator: \.isNewline)
                .first
                .map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) } ?? ""
            if !value.isEmpty && value.count <= 40 { return value }
        }
        return nil
    }

    private static func firstMatch(
        _ pattern: String,
        in text: String,
        options: NSRegularExpression.Options = []
    ) -> [String]? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: options) else { return nil }
        let range = NSRange(text.startIndex..., in: text)
        guard let match = regex.firstMatch(in: text, options: [], range: range) else { return nil }
        return (0..<match.numberOfRanges).map { index in
            let matchRange = match.range(at: index)
            guard matchRange.location != NSNotFound, let swiftRange = Range(matchRange, in: text) else { return "" }
            return String(text[swiftRange])
        }
    }

    private static func wholeMatch(_ pattern: String, in text: String) -> [String]? {
        firstMatch("^(?:\(pattern))$", in: text)
    }

    private static func allMatches(_ pattern: String, in text: String) -> [[String]] {
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let range = NSRange(text.startIndex..., in: text)
        return regex.matches(in: text, options: [], range: range).map { match in
            (0..<match.numberOfRanges).map { index in
                let matchRange = match.range(at: index)
                guard matchRange.location != NSNotFound, let swiftRange = Range(matchRange, in: text) else { return "" }
                return String(text[swiftRange])
            }
        }
    }
}
