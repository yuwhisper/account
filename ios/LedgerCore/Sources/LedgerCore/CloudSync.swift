import Foundation

public enum SyncFailure: Error, LocalizedError {
    case notLoggedIn
    case badURL
    case http(Int, String)
    case decoding(String)

    public var errorDescription: String? {
        switch self {
        case .notLoggedIn: return "还没有登录"
        case .badURL: return "API 地址不对"
        case .http(let code, let message): return message.isEmpty ? "服务器返回 \(code)" : message
        case .decoding(let message): return message
        }
    }
}

public enum CloudSync {
    public static func login(email: String, password: String, apiBaseURL: String) async throws -> String {
        let api = try APIClient(baseURL: apiBaseURL, token: nil)
        let token = try await api.login(email: email, password: password)
        return token
    }

    public static func register(email: String, password: String, apiBaseURL: String) async throws {
        let api = try APIClient(baseURL: apiBaseURL, token: nil)
        try await api.register(email: email, password: password)
    }

    public static func sync(_ store: LedgerStore) async throws {
        let start = store.snapshot
        guard start.isLoggedIn else { throw SyncFailure.notLoggedIn }
        let api = try APIClient(baseURL: start.apiBaseURL, token: start.accessToken)
        try await pushCategoryDeletions(api, store)
        try await pushTransactionDeletions(api, store)
        let remoteCategories = try await api.listCategories()
        store.apply { $0.mergeCategories(remoteCategories) }
        try await pushLocalCategories(api, store)
        try await pushPendingTransactions(api, store)
        let since = store.snapshot.meta[LedgerMeta.lastPull] ?? SyncTime.format(Date(timeIntervalSince1970: 0))
        let pull = try await api.pullTransactions(since: since)
        store.apply {
            $0.mergeTransactions(pull.transactions)
            $0.meta[LedgerMeta.lastPull] = SyncTime.format(pull.serverTime)
        }
    }

    private static func pushCategoryDeletions(_ api: APIClient, _ store: LedgerStore) async throws {
        let tombstones = store.snapshot.meta.filter { $0.key.hasPrefix("cat_del_server:") }
        for (key, value) in tombstones {
            guard let serverId = Int(value) else {
                store.apply { $0.meta[key] = nil }
                continue
            }
            do {
                try await api.deleteCategory(serverId)
                store.apply { $0.meta[key] = nil }
            } catch SyncFailure.http(let code, _) where code == 404 {
                store.apply { $0.meta[key] = nil }
            }
        }
    }

    private static func pushTransactionDeletions(_ api: APIClient, _ store: LedgerStore) async throws {
        let tombstones = store.snapshot.meta.filter { $0.key.hasPrefix("tx_del_server:") }
        for (key, value) in tombstones {
            guard let serverId = Int(value) else {
                store.apply { $0.meta[key] = nil }
                continue
            }
            do {
                try await api.deleteTransaction(serverId)
                store.apply { $0.meta[key] = nil }
            } catch SyncFailure.http(let code, _) where code == 404 {
                store.apply { $0.meta[key] = nil }
            }
        }
    }

    private static func pushLocalCategories(_ api: APIClient, _ store: LedgerStore) async throws {
        let pending = store.snapshot.categories.filter { $0.pendingSync || $0.serverId == nil }
        for category in pending {
            let saved = try await ensureServerCategory(api, store, category)
            if category.pendingSync, let serverId = saved.serverId {
                let updated = try await api.updateCategory(
                    serverId,
                    name: saved.name,
                    sortOrder: saved.sortOrder,
                    clientId: saved.id
                )
                store.apply { snapshot in
                    guard let index = snapshot.categories.firstIndex(where: { $0.id == category.id }) else { return }
                    snapshot.categories[index].serverId = updated.id
                    snapshot.categories[index].updatedAt = updated.updatedAt
                    snapshot.categories[index].pendingSync = false
                }
            }
        }
    }

    private static func ensureServerCategory(
        _ api: APIClient,
        _ store: LedgerStore,
        _ category: CategoryRecord
    ) async throws -> CategoryRecord {
        if let current = store.snapshot.categories.first(where: { $0.id == category.id }), current.serverId != nil {
            return current
        }
        do {
            let created = try await api.createCategory(name: category.name, sortOrder: category.sortOrder, clientId: category.id)
            store.apply { snapshot in
                guard let index = snapshot.categories.firstIndex(where: { $0.id == category.id }) else { return }
                snapshot.categories[index].serverId = created.id
                snapshot.categories[index].updatedAt = created.updatedAt
                if !snapshot.categories[index].pendingSync {
                    snapshot.categories[index].pendingSync = false
                }
            }
            return store.snapshot.categories.first { $0.id == category.id } ?? category
        } catch SyncFailure.http(let code, _) where code == 409 {
            let remote = try await api.listCategories()
            store.apply { $0.mergeCategories(remote) }
            if let matched = store.snapshot.categories.first(where: { $0.id == category.id }) ??
                store.snapshot.categories.first(where: { $0.name == category.name }) {
                return matched
            }
            throw SyncFailure.http(409, "分类已存在")
        }
    }

    private static func pushPendingTransactions(_ api: APIClient, _ store: LedgerStore) async throws {
        let pending = store.snapshot.transactions.filter(\.pendingSync)
        if pending.isEmpty { return }
        let categories = Dictionary(uniqueKeysWithValues: store.snapshot.categories.map { ($0.id, $0) })
        let items = pending.map { transaction in
            TransactionPush(
                clientId: transaction.id,
                amountCents: transaction.amountCents,
                merchant: transaction.merchant,
                source: transaction.source,
                categoryId: transaction.categoryId.flatMap { categories[$0]?.serverId },
                note: transaction.note,
                occurredAt: SyncTime.format(transaction.occurredAt),
                updatedAt: SyncTime.format(transaction.updatedAt),
                type: transaction.type
            )
        }
        for chunk in stride(from: 0, to: items.count, by: 500).map({ Array(items[$0..<min($0 + 500, items.count)]) }) {
            try await api.pushTransactions(chunk)
        }
        let ids = Set(pending.map(\.id))
        store.apply { snapshot in
            for index in snapshot.transactions.indices where ids.contains(snapshot.transactions[index].id) {
                snapshot.transactions[index].pendingSync = false
            }
        }
    }
}

struct APIClient {
    let baseURL: URL
    let token: String?
    private let session: URLSession
    private let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        return encoder
    }()
    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return decoder
    }()

    init(baseURL: String, token: String?) throws {
        let trimmed = baseURL.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url = URL(string: trimmed), url.scheme == "http" || url.scheme == "https" else {
            throw SyncFailure.badURL
        }
        self.baseURL = url
        self.token = token
        self.session = URLSession(configuration: .ephemeral)
    }

    func register(email: String, password: String) async throws {
        let _: EmptyJSON = try await send("POST", path: "/api/auth/register", body: AuthBody(email: email, password: password), allowEmpty: true)
    }

    func login(email: String, password: String) async throws -> String {
        let token: TokenDTO = try await send("POST", path: "/api/auth/login", body: AuthBody(email: email, password: password))
        return token.accessToken
    }

    func listCategories() async throws -> [RemoteCategory] {
        let rows: [CategoryDTO] = try await send("GET", path: "/api/categories")
        return rows.map { $0.remote }
    }

    func createCategory(name: String, sortOrder: Int, clientId: String) async throws -> RemoteCategory {
        let row: CategoryDTO = try await send(
            "POST",
            path: "/api/categories",
            body: CategoryWrite(name: name, sortOrder: sortOrder, clientId: clientId)
        )
        return row.remote
    }

    func updateCategory(_ id: Int, name: String, sortOrder: Int, clientId: String) async throws -> RemoteCategory {
        let row: CategoryDTO = try await send(
            "PATCH",
            path: "/api/categories/\(id)",
            body: CategoryWrite(name: name, sortOrder: sortOrder, clientId: clientId)
        )
        return row.remote
    }

    func deleteCategory(_ id: Int) async throws {
        let _: EmptyJSON = try await send("DELETE", path: "/api/categories/\(id)", allowEmpty: true)
    }

    func deleteTransaction(_ id: Int) async throws {
        let _: EmptyJSON = try await send("DELETE", path: "/api/transactions/\(id)", allowEmpty: true)
    }

    func pushTransactions(_ items: [TransactionPush]) async throws {
        let _: PushOK = try await send("POST", path: "/api/transactions/sync/push", body: TransactionPushBody(transactions: items))
    }

    func pullTransactions(since: String) async throws -> (transactions: [RemoteTransaction], serverTime: Date) {
        let row: PullDTO = try await send("GET", path: "/api/transactions/sync/pull", query: ["since": since])
        return (row.transactions.map { $0.remote }, SyncTime.parse(row.serverTime))
    }

    private func send<Response: Decodable>(
        _ method: String,
        path: String,
        query: [String: String] = [:],
        body: Encodable? = nil,
        allowEmpty: Bool = false
    ) async throws -> Response {
        guard let raw = URL(string: baseURL.absoluteString + path) else { throw SyncFailure.badURL }
        var components = URLComponents(url: raw, resolvingAgainstBaseURL: false)
        if !query.isEmpty {
            components?.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        }
        guard let url = components?.url else { throw SyncFailure.badURL }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let token, !token.isEmpty {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(AnyEncodable(body))
        }
        let (data, response) = try await session.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        if !(200..<300).contains(status) {
            throw SyncFailure.http(status, Self.message(from: data))
        }
        if allowEmpty || data.isEmpty || Response.self == EmptyJSON.self {
            return EmptyJSON() as! Response
        }
        do {
            return try decoder.decode(Response.self, from: data)
        } catch {
            throw SyncFailure.decoding("没能解析服务器返回")
        }
    }

    private static func message(from data: Data) -> String {
        struct Detail: Decodable { let detail: String? }
        if let detail = try? JSONDecoder().decode(Detail.self, from: data).detail, !detail.isEmpty {
            return detail
        }
        return String(data: data, encoding: .utf8) ?? ""
    }
}

private struct EmptyJSON: Decodable {}
private struct AnyEncodable: Encodable {
    let value: Encodable
    init(_ value: Encodable) { self.value = value }
    func encode(to encoder: Encoder) throws { try value.encode(to: encoder) }
}
private struct AuthBody: Encodable { var email: String; var password: String }
private struct TokenDTO: Decodable { var accessToken: String }
private struct PushOK: Decodable { var ok: Bool? }
private struct CategoryWrite: Encodable { var name: String; var sortOrder: Int; var clientId: String }
private struct TransactionPushBody: Encodable { var transactions: [TransactionPush] }

struct TransactionPush: Encodable {
    var clientId: String
    var amountCents: Int
    var merchant: String
    var source: String
    var categoryId: Int?
    var note: String
    var occurredAt: String
    var updatedAt: String
    var type: String
}

private struct CategoryDTO: Decodable {
    var id: Int
    var name: String
    var sortOrder: Int
    var updatedAt: String
    var clientId: String?
    var remote: RemoteCategory {
        RemoteCategory(id: id, name: name, sortOrder: sortOrder, updatedAt: SyncTime.parse(updatedAt), clientId: clientId)
    }
}

private struct TransactionDTO: Decodable {
    var id: Int
    var clientId: String
    var amountCents: Int
    var merchant: String
    var source: String
    var categoryId: Int?
    var note: String
    var occurredAt: String
    var updatedAt: String
    var type: String
    var remote: RemoteTransaction {
        RemoteTransaction(
            id: id,
            clientId: clientId,
            amountCents: amountCents,
            merchant: merchant,
            source: source,
            categoryServerId: categoryId,
            note: note,
            occurredAt: SyncTime.parse(occurredAt),
            updatedAt: SyncTime.parse(updatedAt),
            type: type
        )
    }
}

private struct PullDTO: Decodable {
    var transactions: [TransactionDTO]
    var serverTime: String
}
