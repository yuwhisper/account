import Combine
import Foundation

public enum LedgerLocations {
    public static let appGroupID = "group.com.yuwhisper.account"

    public static func storeURL(fileManager: FileManager = .default) -> URL {
        let base = fileManager.containerURL(forSecurityApplicationGroupIdentifier: appGroupID)
            ?? fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        let directory = base.appendingPathComponent("YuWhisperAccount", isDirectory: true)
        try? fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
        return directory.appendingPathComponent("ledger.json")
    }
}

public final class LedgerStore: ObservableObject {
    @Published public private(set) var snapshot: LedgerSnapshot
    private let fileURL: URL
    private let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()
    private let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()

    public init(fileURL: URL) {
        self.fileURL = fileURL
        var loaded = LedgerStore.read(fileURL, decoder: {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return decoder
        }())
        if loaded.categories.isEmpty {
            loaded.seedIfNeeded()
            snapshot = loaded
            persist(loaded)
        } else {
            snapshot = loaded
        }
    }

    public func reload() {
        var loaded = LedgerStore.read(fileURL, decoder: decoder)
        if loaded.categories.isEmpty {
            loaded.seedIfNeeded()
            persist(loaded)
        }
        snapshot = loaded
    }

    @discardableResult
    public func ingest(text: String, at date: Date = Date()) -> IngestResult {
        guard let parsed = PaymentParser.parseScreenshot(text) else { return .notPayment }
        return ingest(parsed, rawText: text, at: date)
    }

    @discardableResult
    public func ingest(_ payment: ParsedPayment, rawText: String, at date: Date = Date()) -> IngestResult {
        var next = snapshot
        let candidate = Candidate(
            source: payment.source,
            amountCents: payment.amountCents,
            merchant: payment.merchant,
            occurredAt: date
        )
        let existing = next.pending.map {
            Candidate(source: $0.source, amountCents: $0.amountCents, merchant: $0.merchant, occurredAt: $0.occurredAt)
        } + next.transactions.map {
            Candidate(source: $0.source, amountCents: $0.amountCents, merchant: $0.merchant, occurredAt: $0.occurredAt)
        }
        if Dedupe.isDuplicate(candidate, existing: existing, windowSeconds: Dedupe.captureWindowSeconds) {
            return .duplicate
        }
        let pending = PendingPayment(
            amountCents: payment.amountCents,
            merchant: payment.merchant,
            source: payment.source,
            rawText: rawText,
            occurredAt: date
        )
        next.pending.insert(pending, at: 0)
        snapshot = next
        persist(next)
        return .added(pending)
    }

    public func confirm(pendingId: String, categoryId: String, note: String, type: String, at date: Date = Date()) {
        var next = snapshot
        guard let index = next.pending.firstIndex(where: { $0.id == pendingId }) else { return }
        let pending = next.pending.remove(at: index)
        let transaction = TransactionRecord(
            id: UUID().uuidString,
            amountCents: pending.amountCents,
            merchant: pending.merchant,
            source: pending.source,
            categoryId: categoryId,
            note: note,
            occurredAt: pending.occurredAt,
            updatedAt: date,
            type: type,
            pendingSync: true
        )
        next.transactions.insert(transaction, at: 0)
        snapshot = next
        persist(next)
    }

    public func dismissPending(id: String) {
        var next = snapshot
        next.pending.removeAll { $0.id == id }
        snapshot = next
        persist(next)
    }

    public func addManual(
        amountCents: Int,
        merchant: String,
        categoryId: String?,
        note: String,
        type: String,
        at date: Date = Date()
    ) {
        var next = snapshot
        let transaction = TransactionRecord(
            id: UUID().uuidString,
            amountCents: amountCents,
            merchant: merchant,
            source: "manual",
            categoryId: categoryId,
            note: note,
            occurredAt: date,
            updatedAt: date,
            type: type,
            pendingSync: true
        )
        next.transactions.insert(transaction, at: 0)
        snapshot = next
        persist(next)
    }

    public func addCategory(name: String, now: Date = Date()) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        var next = snapshot
        let order = (next.categories.map(\.sortOrder).max() ?? -1) + 1
        next.categories.append(
            CategoryRecord(id: UUID().uuidString, name: trimmed, sortOrder: order, updatedAt: now, pendingSync: true)
        )
        snapshot = next
        persist(next)
    }

    public func renameCategory(id: String, name: String, now: Date = Date()) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        var next = snapshot
        guard let index = next.categories.firstIndex(where: { $0.id == id }) else { return }
        next.categories[index].name = trimmed
        next.categories[index].updatedAt = now
        next.categories[index].pendingSync = true
        snapshot = next
        persist(next)
    }

    public func deleteCategory(id: String) {
        var next = snapshot
        guard let index = next.categories.firstIndex(where: { $0.id == id }) else { return }
        let category = next.categories.remove(at: index)
        if let serverId = category.serverId {
            next.meta[LedgerMeta.categoryDelete(category.id)] = String(serverId)
        }
        for txIndex in next.transactions.indices where next.transactions[txIndex].categoryId == id {
            next.transactions[txIndex].categoryId = nil
            next.transactions[txIndex].pendingSync = true
            next.transactions[txIndex].updatedAt = Date()
        }
        snapshot = next
        persist(next)
    }

    public func deleteTransaction(id: String) {
        var next = snapshot
        guard let index = next.transactions.firstIndex(where: { $0.id == id }) else { return }
        let transaction = next.transactions.remove(at: index)
        next.meta[LedgerMeta.transactionDeleteClient(transaction.id)] = "1"
        if let serverId = transaction.serverId {
            next.meta[LedgerMeta.transactionDeleteServer(transaction.id)] = String(serverId)
        }
        snapshot = next
        persist(next)
    }

    public func updateSession(token: String, email: String, apiBaseURL: String) {
        var next = snapshot
        next.accessToken = token
        next.email = email
        next.apiBaseURL = apiBaseURL.trimmingCharacters(in: .whitespacesAndNewlines)
        snapshot = next
        persist(next)
    }

    public func updateAPIBaseURL(_ url: String) {
        var next = snapshot
        next.apiBaseURL = url.trimmingCharacters(in: .whitespacesAndNewlines)
        snapshot = next
        persist(next)
    }

    public func logout() {
        var next = snapshot
        next.accessToken = ""
        next.email = ""
        snapshot = next
        persist(next)
    }

    public func apply(_ body: (inout LedgerSnapshot) -> Void) {
        var next = snapshot
        body(&next)
        snapshot = next
        persist(next)
    }

    private func persist(_ snapshot: LedgerSnapshot) {
        let data = try? encoder.encode(snapshot)
        let coordinator = NSFileCoordinator()
        var error: NSError?
        coordinator.coordinate(writingItemAt: fileURL, options: .forReplacing, error: &error) { url in
            try? FileManager.default.createDirectory(
                at: url.deletingLastPathComponent(),
                withIntermediateDirectories: true
            )
            try? data?.write(to: url, options: .atomic)
        }
    }

    private static func read(_ fileURL: URL, decoder: JSONDecoder) -> LedgerSnapshot {
        let coordinator = NSFileCoordinator()
        var error: NSError?
        var snapshot = LedgerSnapshot()
        coordinator.coordinate(readingItemAt: fileURL, options: [], error: &error) { url in
            guard let data = try? Data(contentsOf: url),
                  let decoded = try? decoder.decode(LedgerSnapshot.self, from: data) else { return }
            snapshot = decoded
        }
        return snapshot
    }
}
