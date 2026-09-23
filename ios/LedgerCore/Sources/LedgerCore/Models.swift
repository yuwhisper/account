import Foundation

public enum SeedCategories {
    public static let names = ["餐饮", "交通", "购物", "住房", "娱乐", "医疗", "教育", "其他"]
}

public struct CategoryRecord: Codable, Identifiable, Equatable {
    public var id: String
    public var name: String
    public var sortOrder: Int
    public var updatedAt: Date
    public var serverId: Int?
    public var pendingSync: Bool

    public init(
        id: String,
        name: String,
        sortOrder: Int,
        updatedAt: Date,
        serverId: Int? = nil,
        pendingSync: Bool = false
    ) {
        self.id = id
        self.name = name
        self.sortOrder = sortOrder
        self.updatedAt = updatedAt
        self.serverId = serverId
        self.pendingSync = pendingSync
    }
}

public struct TransactionRecord: Codable, Identifiable, Equatable {
    public var id: String
    public var amountCents: Int
    public var merchant: String
    public var source: String
    public var categoryId: String?
    public var note: String
    public var occurredAt: Date
    public var updatedAt: Date
    /// `expense` or `income`
    public var type: String
    public var pendingSync: Bool
    public var serverId: Int?

    public init(
        id: String,
        amountCents: Int,
        merchant: String,
        source: String,
        categoryId: String?,
        note: String,
        occurredAt: Date,
        updatedAt: Date,
        type: String,
        pendingSync: Bool,
        serverId: Int? = nil
    ) {
        self.id = id
        self.amountCents = amountCents
        self.merchant = merchant
        self.source = source
        self.categoryId = categoryId
        self.note = note
        self.occurredAt = occurredAt
        self.updatedAt = updatedAt
        self.type = type
        self.pendingSync = pendingSync
        self.serverId = serverId
    }
}

public struct PendingPayment: Codable, Identifiable, Equatable {
    public var id: String
    public var amountCents: Int
    public var merchant: String
    public var source: String
    public var rawText: String
    public var occurredAt: Date

    public init(
        id: String = UUID().uuidString,
        amountCents: Int,
        merchant: String,
        source: String,
        rawText: String,
        occurredAt: Date
    ) {
        self.id = id
        self.amountCents = amountCents
        self.merchant = merchant
        self.source = source
        self.rawText = rawText
        self.occurredAt = occurredAt
    }
}

public struct RemoteCategory: Equatable {
    public var id: Int
    public var name: String
    public var sortOrder: Int
    public var updatedAt: Date
    public var clientId: String?

    public init(id: Int, name: String, sortOrder: Int, updatedAt: Date, clientId: String?) {
        self.id = id
        self.name = name
        self.sortOrder = sortOrder
        self.updatedAt = updatedAt
        self.clientId = clientId
    }
}

public struct RemoteTransaction: Equatable {
    public var id: Int
    public var clientId: String
    public var amountCents: Int
    public var merchant: String
    public var source: String
    public var categoryServerId: Int?
    public var note: String
    public var occurredAt: Date
    public var updatedAt: Date
    public var type: String

    public init(
        id: Int,
        clientId: String,
        amountCents: Int,
        merchant: String,
        source: String,
        categoryServerId: Int?,
        note: String,
        occurredAt: Date,
        updatedAt: Date,
        type: String
    ) {
        self.id = id
        self.clientId = clientId
        self.amountCents = amountCents
        self.merchant = merchant
        self.source = source
        self.categoryServerId = categoryServerId
        self.note = note
        self.occurredAt = occurredAt
        self.updatedAt = updatedAt
        self.type = type
    }
}

public enum IngestResult: Equatable {
    case added(PendingPayment)
    case duplicate
    case notPayment
}

public enum LedgerMeta {
    public static let lastPull = "last_pull"
    public static func categoryDelete(_ clientId: String) -> String { "cat_del_server:\(clientId)" }
    public static func transactionDeleteServer(_ clientId: String) -> String { "tx_del_server:\(clientId)" }
    public static func transactionDeleteClient(_ clientId: String) -> String { "tx_del_client:\(clientId)" }
}

public struct LedgerSnapshot: Codable, Equatable {
    public var categories: [CategoryRecord]
    public var transactions: [TransactionRecord]
    public var pending: [PendingPayment]
    public var meta: [String: String]
    public var accessToken: String
    public var email: String
    public var apiBaseURL: String

    public init(
        categories: [CategoryRecord] = [],
        transactions: [TransactionRecord] = [],
        pending: [PendingPayment] = [],
        meta: [String: String] = [:],
        accessToken: String = "",
        email: String = "",
        apiBaseURL: String = "http://127.0.0.1:8000"
    ) {
        self.categories = categories
        self.transactions = transactions
        self.pending = pending
        self.meta = meta
        self.accessToken = accessToken
        self.email = email
        self.apiBaseURL = apiBaseURL
    }

    public var isLoggedIn: Bool { !accessToken.isEmpty }

    public mutating func seedIfNeeded(now: Date = Date()) {
        guard categories.isEmpty else { return }
        categories = SeedCategories.names.enumerated().map { index, name in
            CategoryRecord(
                id: UUID().uuidString,
                name: name,
                sortOrder: index,
                updatedAt: now,
                pendingSync: false
            )
        }
    }

    public mutating func mergeCategories(_ remote: [RemoteCategory]) {
        for dto in remote {
            let clientId = dto.clientId?.trimmingCharacters(in: .whitespacesAndNewlines)
            let byClient = clientId.flatMap { id in
                id.isEmpty ? nil : categories.firstIndex { $0.id == id }
            }
            let index = byClient ?? categories.firstIndex { $0.name == dto.name }
            if let index {
                var existing = categories[index]
                let takeRemote = !existing.pendingSync || dto.updatedAt >= existing.updatedAt
                if takeRemote {
                    existing.name = dto.name
                    existing.sortOrder = dto.sortOrder
                    existing.pendingSync = false
                }
                existing.updatedAt = max(existing.updatedAt, dto.updatedAt)
                existing.serverId = dto.id
                categories[index] = existing
            } else {
                let id = (clientId?.isEmpty == false ? clientId! : UUID().uuidString)
                categories.append(
                    CategoryRecord(
                        id: id,
                        name: dto.name,
                        sortOrder: dto.sortOrder,
                        updatedAt: dto.updatedAt,
                        serverId: dto.id,
                        pendingSync: false
                    )
                )
            }
        }
        categories.sort { $0.sortOrder < $1.sortOrder }
    }

    public mutating func mergeTransactions(_ remote: [RemoteTransaction]) {
        let categoryByServer = Dictionary(uniqueKeysWithValues: categories.compactMap { category -> (Int, String)? in
            guard let serverId = category.serverId else { return nil }
            return (serverId, category.id)
        })
        for dto in remote {
            if meta[LedgerMeta.transactionDeleteClient(dto.clientId)] != nil { continue }
            let categoryId = dto.categoryServerId.flatMap { categoryByServer[$0] }
            if let index = transactions.firstIndex(where: { $0.id == dto.clientId }) {
                var existing = transactions[index]
                if dto.updatedAt >= existing.updatedAt {
                    existing.amountCents = dto.amountCents
                    existing.merchant = dto.merchant
                    existing.source = dto.source
                    existing.categoryId = dto.categoryServerId == nil ? nil : (categoryId ?? existing.categoryId)
                    existing.note = dto.note
                    existing.occurredAt = dto.occurredAt
                    existing.updatedAt = dto.updatedAt
                    existing.type = dto.type
                    existing.pendingSync = false
                    existing.serverId = dto.id
                } else if existing.serverId == nil {
                    existing.serverId = dto.id
                }
                transactions[index] = existing
            } else {
                transactions.append(
                    TransactionRecord(
                        id: dto.clientId,
                        amountCents: dto.amountCents,
                        merchant: dto.merchant,
                        source: dto.source,
                        categoryId: categoryId,
                        note: dto.note,
                        occurredAt: dto.occurredAt,
                        updatedAt: dto.updatedAt,
                        type: dto.type,
                        pendingSync: false,
                        serverId: dto.id
                    )
                )
            }
        }
        transactions.sort { $0.occurredAt > $1.occurredAt }
    }
}

public enum SyncTime {
    public static func format(_ date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.string(from: date)
    }

    public static func parse(_ raw: String) -> Date {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.isEmpty { return Date(timeIntervalSince1970: 0) }
        let internet = ISO8601DateFormatter()
        internet.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = internet.date(from: value) { return date }
        internet.formatOptions = [.withInternetDateTime]
        if let date = internet.date(from: value) { return date }
        let fractional = DateFormatter()
        fractional.locale = Locale(identifier: "en_US_POSIX")
        fractional.timeZone = TimeZone(secondsFromGMT: 0)
        for format in ["yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss"] {
            fractional.dateFormat = format
            if let date = fractional.date(from: value) { return date }
        }
        return Date(timeIntervalSince1970: 0)
    }
}

public enum SourceLabel {
    public static func title(for source: String) -> String {
        switch source {
        case "wechat": return "微信"
        case "alipay": return "支付宝"
        case "unionpay": return "云闪付"
        case "screenshot": return "截图"
        case "manual": return "手动"
        default: return source.isEmpty ? "其他" : source
        }
    }
}
