import Foundation

public struct Candidate: Equatable {
    public var source: String
    public var amountCents: Int?
    public var merchant: String
    public var occurredAt: Date

    public init(source: String, amountCents: Int?, merchant: String, occurredAt: Date) {
        self.source = source
        self.amountCents = amountCents
        self.merchant = merchant
        self.occurredAt = occurredAt
    }
}

/// Same window rules as the Android client.
public enum Dedupe {
    /// Notification and screenshot of one payment usually arrive together.
    /// A longer window drops a real repeat purchase with the same amount.
    public static let captureWindowSeconds = 8

    public static func isDuplicate(
        _ candidate: Candidate,
        existing: [Candidate],
        windowSeconds: Int
    ) -> Bool {
        let keyMerchant = normalizeMerchant(candidate.merchant)
        if let amount = candidate.amountCents {
            return existing.contains { other in
                other.source == candidate.source &&
                    other.amountCents == amount &&
                    normalizeMerchant(other.merchant) == keyMerchant &&
                    wholeSeconds(between: other.occurredAt, and: candidate.occurredAt) <= windowSeconds
            }
        }
        return existing.contains { other in
            other.source == candidate.source &&
                other.amountCents == nil &&
                normalizeMerchant(other.merchant) == keyMerchant &&
                wholeSeconds(between: other.occurredAt, and: candidate.occurredAt) <= 15
        }
    }

    private static func normalizeMerchant(_ merchant: String) -> String {
        merchant.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private static func wholeSeconds(between start: Date, and end: Date) -> Int {
        Int(abs(end.timeIntervalSince(start)))
    }
}
