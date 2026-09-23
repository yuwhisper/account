import Foundation
import LedgerCore
import SwiftUI

enum Money {
    static func yuan(_ cents: Int) -> String {
        String(format: "¥%.2f", Double(cents) / 100)
    }

    static func signed(_ cents: Int, type: String) -> String {
        let body = String(format: "%.2f", Double(cents) / 100)
        return type == "income" ? "+¥\(body)" : "−¥\(body)"
    }

    static func cents(from input: String) -> Int? {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return PaymentParser.extractAmountCents("¥\(trimmed)")
    }
}

enum LedgerTheme {
    static let ink = Color(red: 0.13, green: 0.22, blue: 0.18)
    static let pine = Color(red: 0.16, green: 0.42, blue: 0.32)
    static let paper = Color(red: 0.96, green: 0.95, blue: 0.91)
    static let expense = Color(red: 0.55, green: 0.24, blue: 0.18)
    static let income = Color(red: 0.12, green: 0.40, blue: 0.30)
}

extension Date {
    var dayTitle: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.setLocalizedDateFormatFromTemplate("MMMd EEE")
        return formatter.string(from: self)
    }
}
