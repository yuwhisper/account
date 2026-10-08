import Foundation
import LedgerCore
import SwiftUI
import UIKit

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
    static let paper = adaptive(0xF4F5EE, dark: 0x101513)
    static let surface = adaptive(0xF9FAF5, dark: 0x19231D)
    static let inset = adaptive(0xEFF1E8, dark: 0x16201A)
    static let rose = adaptive(0x273F35, dark: 0x9DB7A3)
    static let roseSoft = adaptive(0xE9EDE2, dark: 0x26352B)
    static let ink = adaptive(0x273F35, dark: 0xDCE5D8)
    static let muted = adaptive(0x617064, dark: 0xA5B49F)
    static let border = adaptive(0xC5CDC1, dark: 0x303D35)
    static let expense = adaptive(0xA54B3B, dark: 0xD9A28F)
    static let income = adaptive(0x426952, dark: 0xB5CDB6)
    static let onRose = adaptive(0xF4F5EE, dark: 0x101513)
    static let seal = adaptive(0xA54B3B, dark: 0xC99985)
    static let motion = Animation.snappy(duration: 0.28, extraBounce: 0)

    static func heading(_ size: CGFloat) -> Font {
        .custom("YuWhisperOrchidSerif-Regular", size: size, relativeTo: .largeTitle)
    }

    static func amount(_ size: CGFloat) -> Font {
        .custom("Georgia", fixedSize: size)
    }

    private static func adaptive(_ light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            let value = traits.userInterfaceStyle == .dark ? dark : light
            return UIColor(
                red: CGFloat((value >> 16) & 0xFF) / 255,
                green: CGFloat((value >> 8) & 0xFF) / 255,
                blue: CGFloat(value & 0xFF) / 255,
                alpha: 1
            )
        })
    }

    static func categorySymbol(_ name: String) -> String {
        switch name {
        case "餐饮": return "fork.knife"
        case "交通": return "tram.fill"
        case "购物": return "bag.fill"
        case "住房": return "house.fill"
        case "娱乐": return "gamecontroller.fill"
        case "医疗": return "cross.case.fill"
        case "教育": return "book.fill"
        default: return "square.grid.2x2.fill"
        }
    }
}

struct LedgerPrimaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 52)
            .foregroundStyle(LedgerTheme.onRose)
            .background(LedgerTheme.rose.opacity(isEnabled ? 1 : 0.38), in: RoundedRectangle(cornerRadius: 12))
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.975 : 1)
            .opacity(configuration.isPressed ? 0.86 : 1)
            .animation(reduceMotion ? nil : .smooth(duration: 0.22), value: configuration.isPressed)
    }
}

struct LedgerSecondaryButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.body.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 50)
            .foregroundStyle(LedgerTheme.rose.opacity(isEnabled ? 1 : 0.45))
            .background(LedgerTheme.roseSoft.opacity(configuration.isPressed ? 0.65 : 1), in: RoundedRectangle(cornerRadius: 12))
            .scaleEffect(configuration.isPressed && !reduceMotion ? 0.98 : 1)
            .animation(reduceMotion ? nil : .smooth(duration: 0.22), value: configuration.isPressed)
    }
}

struct LedgerField<Content: View>: View {
    let title: String
    let hint: String?
    let content: Content

    init(_ title: String, hint: String? = nil, @ViewBuilder content: () -> Content) {
        self.title = title
        self.hint = hint
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(LedgerTheme.ink)
            content
            if let hint {
                Text(hint)
                    .font(.footnote)
                    .foregroundStyle(LedgerTheme.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct LedgerEmptyState: View {
    let symbol: String
    let title: String
    let message: String

    var body: some View {
        VStack(spacing: 14) {
            LedgerOrchidArt()
                .frame(width: 220, height: 146)
                .opacity(0.72)
            Text(title)
                .font(LedgerTheme.heading(24))
                .foregroundStyle(LedgerTheme.ink)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(LedgerTheme.muted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 28)
        .padding(.horizontal, 20)
    }
}

extension View {
    func ledgerSurface() -> some View {
        self
            .padding(20)
            .background(LedgerTheme.surface, in: RoundedRectangle(cornerRadius: 12))
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .stroke(LedgerTheme.border.opacity(0.65), lineWidth: 0.5)
            }
    }

    func ledgerInput() -> some View {
        self
            .font(.body)
            .foregroundStyle(LedgerTheme.ink)
            .padding(14)
            .frame(minHeight: 50)
            .background(LedgerTheme.inset, in: RoundedRectangle(cornerRadius: 8))
            .overlay {
                RoundedRectangle(cornerRadius: 8)
                    .stroke(LedgerTheme.border, lineWidth: 0.5)
            }
    }

    func ledgerPage() -> some View {
        self
            .foregroundStyle(LedgerTheme.ink)
            .background(LedgerTheme.paper)
            .toolbarBackground(LedgerTheme.paper, for: .navigationBar)
            .tint(LedgerTheme.rose)
    }

    func ledgerSheet() -> some View {
        self
            .presentationDragIndicator(.visible)
            .presentationCornerRadius(24)
            .presentationBackground(LedgerTheme.paper)
            .tint(LedgerTheme.rose)
    }
}

struct LedgerOrchidArt: View {
    var body: some View {
        Image("OrchidArtwork")
            .resizable()
            .scaledToFit()
            .accessibilityHidden(true)
            .allowsHitTesting(false)
    }
}

struct LedgerSeal: View {
    var body: some View {
        Text("兰")
            .font(LedgerTheme.heading(18))
            .foregroundStyle(LedgerTheme.seal)
            .frame(width: 28, height: 32)
            .overlay { Rectangle().stroke(LedgerTheme.seal, lineWidth: 0.7) }
            .overlay { Rectangle().inset(by: 3).stroke(LedgerTheme.seal.opacity(0.65), lineWidth: 0.5) }
            .accessibilityHidden(true)
    }
}

struct LedgerPageHeading: View {
    let title: String
    var subtitle: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let subtitle {
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(LedgerTheme.muted)
            }
            Text(title)
                .font(LedgerTheme.heading(36))
                .tracking(2)
                .foregroundStyle(LedgerTheme.ink)
                .accessibilityAddTraits(.isHeader)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 10)
    }
}

extension Date {
    var dayTitle: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.setLocalizedDateFormatFromTemplate("MMMd EEE")
        return formatter.string(from: self)
    }
}
