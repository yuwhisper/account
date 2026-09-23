import Foundation
import LedgerCore

func date(_ raw: String) -> Date {
    ISO8601DateFormatter().date(from: raw)!
}

func paymentParserChecks() {
    let wechat = PaymentParser.parseWechat(ParsedRaw(title: "微信支付", text: "收款方商家：瑞幸咖啡\n支付金额：¥36.50"))
    Check.eq(wechat?.amountCents, 3650)
    Check.yes(wechat?.merchant.contains("瑞幸") == true)

    let alipay = PaymentParser.parseAlipay(ParsedRaw(title: "支付宝", text: "付款成功\n商户：星巴克咖啡\n金额：￥28.00"))
    Check.eq(alipay?.amountCents, 2800)
    Check.yes(alipay?.merchant.contains("星巴克") == true)

    let routed = PaymentParser.parse(
        packageName: "com.tencent.mm",
        title: "微信支付",
        text: "收款方商家：瑞幸咖啡\n支付金额：¥36.50"
    )
    Check.eq(routed?.source, "wechat")
    let routedAlipay = PaymentParser.parse(
        packageName: "com.eg.android.AlipayGphone",
        title: "支付宝",
        text: "付款成功\n商户：星巴克咖啡\n金额：￥28.00"
    )
    Check.eq(routedAlipay?.amountCents, 2800)
    Check.eq(routedAlipay?.source, "alipay")

    Check.eq(PaymentParser.parse(packageName: "com.example.unknown", title: "付款", text: "金额：¥10.00")?.amountCents, 1000)
    Check.eq(
        PaymentParser.parseAccessibility(packageName: "com.tencent.mm", texts: ["支付成功", "¥", "12.34", "完成"])?.amountCents,
        1234
    )
    Check.eq(
        PaymentParser.parseAccessibility(
            packageName: "com.tencent.mm",
            texts: ["红包", "已发送", "¥", "0.30", "给小明的红包", "看看大家的手气"]
        )?.amountCents,
        30
    )
    Check.yes(PaymentParser.shouldOfferConfirm(packageName: "com.tencent.mm", title: "", text: "你发了一个红包 ¥0.30 等待对方领取", accessibilityMode: true))
    Check.no(PaymentParser.shouldOfferConfirm(packageName: "com.tencent.mm", title: "", text: "塞钱进红包 ¥0.30 金额", accessibilityMode: true))
    Check.yes(PaymentParser.shouldOfferConfirm(packageName: "com.tencent.mm", title: "", text: "待朋友确认收款 ¥1.00", accessibilityMode: true))
    Check.yes(PaymentParser.shouldOfferConfirm(packageName: "com.tencent.mm", title: "", text: "转账成功 ¥2.00 转账给小红", accessibilityMode: true))
    Check.yes(PaymentParser.shouldOfferConfirm(packageName: "com.tencent.mm", title: "", text: "支付成功 ¥3.00", accessibilityMode: true))
    Check.no(PaymentParser.shouldOfferConfirm(packageName: "com.tencent.mm", title: "", text: "微信支付 聊天列表", accessibilityMode: true))

    let home = ["首页", "理财", "消息", "我的", "余额", "花呗", "转账", "出行", "扣款", "¥12.00", "消费"]
    Check.no(PaymentParser.shouldOfferConfirmFromNodes(packageName: "com.eg.android.AlipayGphone", texts: home))
    Check.yes(PaymentParser.shouldOfferConfirmFromNodes(packageName: "com.eg.android.AlipayGphone", texts: ["支付成功", "¥28.00", "星巴克", "完成"]))
    Check.no(PaymentParser.shouldOfferConfirmFromNodes(packageName: "com.tencent.mm", texts: ["已存入零钱", "¥5.00", "红包"]))
    Check.no(PaymentParser.shouldOfferConfirm(packageName: "com.tencent.mm", title: "微信支付", text: "收款成功 ¥5.00", accessibilityMode: true))

    let redPacket = ["已发送", "红包", "¥", "0.3", "未领取的红包将于24小时后发起退款", "完成"]
    Check.yes(PaymentParser.shouldOfferConfirmFromNodes(packageName: "com.tencent.mm", texts: redPacket))
    Check.eq(PaymentParser.parseAccessibility(packageName: "com.tencent.mm", texts: redPacket)?.amountCents, 30)
    Check.no(PaymentParser.shouldOfferConfirm(packageName: "com.eg.android.AlipayGphone", title: "支付宝", text: "账户扣款 ¥12.00"))
    Check.yes(PaymentParser.shouldOfferConfirm(packageName: "com.eg.android.AlipayGphone", title: "支付宝", text: "付款成功 商户：星巴克 金额：¥28.00"))

    Check.eq(PaymentParser.parseScreenshot("支付成功\n瑞幸咖啡\n¥36.50\n完成")?.amountCents, 3650)
    Check.isNil(PaymentParser.parseScreenshot("按住 说话\n你好\n¥36.50"))
    Check.isNil(PaymentParser.parseScreenshot("已存入零钱\n¥5.00"))
    Check.eq(PaymentParser.parseWechat(ParsedRaw(title: "微信支付", text: "支付金额：￥１２．５０"))?.amountCents, 1250)
}

func dedupeChecks() {
    let first = Candidate(source: "wechat", amountCents: 3650, merchant: "瑞幸", occurredAt: date("2026-08-12T02:00:00Z"))
    let second = Candidate(source: "wechat", amountCents: 3650, merchant: "瑞幸", occurredAt: date("2026-08-12T02:00:20Z"))
    Check.yes(Dedupe.isDuplicate(first, existing: [first], windowSeconds: 120))
    Check.yes(Dedupe.isDuplicate(second, existing: [first], windowSeconds: 120))
    let later = Candidate(source: "wechat", amountCents: 3650, merchant: "瑞幸", occurredAt: date("2026-08-12T02:05:00Z"))
    Check.no(Dedupe.isDuplicate(later, existing: [first], windowSeconds: 120))
    let small = Candidate(source: "wechat", amountCents: 100, merchant: "未知商户", occurredAt: date("2026-08-12T02:00:00Z"))
    let muchLater = Candidate(source: "wechat", amountCents: 100, merchant: "未知商户", occurredAt: date("2026-08-12T02:10:00Z"))
    Check.no(Dedupe.isDuplicate(muchLater, existing: [small], windowSeconds: 8))
    let same = Candidate(source: "wechat", amountCents: 100, merchant: "未知商户", occurredAt: small.occurredAt)
    Check.yes(Dedupe.isDuplicate(same, existing: [small], windowSeconds: 8))
}

func storeChecks() throws {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
    try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
    let store = LedgerStore(fileURL: directory.appendingPathComponent("ledger.json"))
    let text = "微信支付\n支付成功\n收款方商家：瑞幸咖啡\n支付金额：¥36.50"
    guard case .added(let pending) = store.ingest(text: text, at: date("2026-08-12T02:00:00Z")) else {
        Check.yes(false)
        return
    }
    Check.eq(pending.amountCents, 3650)
    Check.eq(store.ingest(text: text, at: date("2026-08-12T02:00:05Z")), .duplicate)
    Check.eq(store.snapshot.pending.count, 1)

    let plain = "支付成功\n¥12.00\n完成"
    guard case .added(let second) = store.ingest(text: plain) else {
        Check.yes(false)
        return
    }
    let categoryId = store.snapshot.categories[0].id
    store.confirm(pendingId: second.id, categoryId: categoryId, note: "咖啡", type: "expense")
    Check.yes(store.snapshot.pending.contains { $0.id == pending.id })
    Check.eq(store.snapshot.transactions.first?.amountCents, 1200)
    Check.eq(store.snapshot.transactions.first?.pendingSync, true)
    store.reload()
    Check.eq(store.snapshot.transactions.first?.amountCents, 1200)
    Check.eq(store.snapshot.categories.count, 8)

    var snapshot = LedgerSnapshot()
    snapshot.categories = [CategoryRecord(id: "cat", name: "餐饮", sortOrder: 0, updatedAt: date("2026-08-12T00:00:00Z"), serverId: 7)]
    snapshot.transactions = [
        TransactionRecord(
            id: "tx", amountCents: 100, merchant: "旧", source: "wechat", categoryId: "cat", note: "",
            occurredAt: date("2026-08-12T01:00:00Z"), updatedAt: date("2026-08-12T01:00:00Z"),
            type: "expense", pendingSync: true
        ),
    ]
    snapshot.mergeTransactions([
        RemoteTransaction(
            id: 9, clientId: "tx", amountCents: 250, merchant: "新", source: "wechat", categoryServerId: 7,
            note: "云", occurredAt: date("2026-08-12T01:00:00Z"), updatedAt: date("2026-08-12T03:00:00Z"), type: "expense"
        ),
    ])
    Check.eq(snapshot.transactions.first?.merchant, "新")
    Check.eq(snapshot.transactions.first?.serverId, 9)
    Check.eq(snapshot.transactions.first?.pendingSync, false)

    var deleted = LedgerSnapshot()
    deleted.meta[LedgerMeta.transactionDeleteClient("tx")] = "1"
    deleted.mergeTransactions([
        RemoteTransaction(
            id: 9, clientId: "tx", amountCents: 250, merchant: "新", source: "wechat", categoryServerId: nil,
            note: "", occurredAt: date("2026-08-12T01:00:00Z"), updatedAt: date("2026-08-12T03:00:00Z"), type: "expense"
        ),
    ])
    Check.eq(deleted.transactions.count, 0)
}

paymentParserChecks()
dedupeChecks()
do {
    try storeChecks()
} catch {
    Check.failures += 1
    print("FAIL store checks threw \(error)")
}
if Check.failures == 0 {
    print("ok")
} else {
    print("\(Check.failures) failed")
    exit(1)
}
