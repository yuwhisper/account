import LedgerCore
import SwiftUI

struct StatsScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .largeTitle) private var amountSize: CGFloat = 44
    @ScaledMetric(relativeTo: .title2) private var rowAmountSize: CGFloat = 20

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    LedgerPageHeading(
                        title: "统计",
                        subtitle: Date().formatted(.dateTime.year().month(.wide).locale(Locale(identifier: "zh_CN")))
                    )
                    .padding(.top, 12)
                    .padding(.bottom, 32)
                    monthlySummary
                        .padding(.bottom, 28)
                    rule
                    VStack(alignment: .leading, spacing: 0) {
                        HStack(alignment: .firstTextBaseline) {
                            Text("支出分类")
                                .font(LedgerTheme.heading(24))
                            Spacer()
                            Text("\(byCategory.count) 类")
                                .font(.footnote)
                                .foregroundStyle(LedgerTheme.muted)
                        }
                        .padding(.top, 28)
                        .padding(.bottom, 20)
                        if byCategory.isEmpty {
                            LedgerEmptyState(symbol: "chart.pie", title: "这个月还没有支出", message: "记下第一笔后，就能看到钱花在哪里。")
                        } else {
                            ForEach(byCategory, id: \.name) { row in
                                categoryRow(name: row.name, cents: row.cents, share: row.share)
                                rule
                            }
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 32)
            }
            .ledgerPage()
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private var monthlySummary: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("本月支出")
                .font(.subheadline)
                .foregroundStyle(LedgerTheme.muted)
            Text(Money.yuan(expense))
                .font(LedgerTheme.amount(amountSize))
                .monospacedDigit()
                .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                .minimumScaleFactor(dynamicTypeSize.isAccessibilitySize ? 1 : 0.65)
                .fixedSize(horizontal: false, vertical: true)
                .contentTransition(reduceMotion ? .identity : .numericText(value: Double(expense)))
                .animation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0), value: expense)
                .padding(.bottom, 16)
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline) {
                    Text("本月收入")
                    Spacer(minLength: 16)
                    Text(Money.yuan(income))
                        .font(LedgerTheme.amount(rowAmountSize))
                        .foregroundStyle(LedgerTheme.income)
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text("本月收入")
                    Text(Money.yuan(income))
                        .font(LedgerTheme.amount(rowAmountSize))
                        .foregroundStyle(LedgerTheme.income)
                }
            }
            .font(.subheadline)
            .foregroundStyle(LedgerTheme.muted)
            .monospacedDigit()
        }
    }

    private func categoryRow(name: String, cents: Int, share: Double) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline) {
                    Text(name).font(.body)
                    Spacer(minLength: 12)
                    Text(Money.yuan(cents)).font(LedgerTheme.amount(rowAmountSize))
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text(name).font(.body)
                    Text(Money.yuan(cents)).font(LedgerTheme.amount(rowAmountSize))
                }
            }
            .monospacedDigit()
            .contentTransition(reduceMotion ? .identity : .numericText(value: Double(cents)))
            .animation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0), value: cents)
            HStack(spacing: 16) {
                GeometryReader { proxy in
                    ZStack(alignment: .leading) {
                        Rectangle().fill(LedgerTheme.roseSoft)
                        Rectangle()
                            .fill(LedgerTheme.rose)
                            .frame(width: max(2, proxy.size.width * share))
                            .animation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0), value: share)
                    }
                }
                .frame(height: 3)
                .accessibilityHidden(true)
                Text(share.formatted(.percent.precision(.fractionLength(1))))
                    .font(.caption)
                    .monospacedDigit()
                    .foregroundStyle(LedgerTheme.muted)
                    .accessibilityLabel("占本月支出 \(share.formatted(.percent.precision(.fractionLength(1))))")
            }
        }
        .padding(.vertical, 18)
        .accessibilityElement(children: .combine)
    }

    private var rule: some View {
        Rectangle().fill(LedgerTheme.border).frame(height: 0.5)
    }

    private var monthTransactions: [TransactionRecord] {
        let calendar = Calendar.current
        let now = Date()
        return store.snapshot.transactions.filter {
            calendar.isDate($0.occurredAt, equalTo: now, toGranularity: .month)
        }
    }

    private var expense: Int {
        monthTransactions.filter { $0.type == "expense" }.reduce(0) { $0 + $1.amountCents }
    }

    private var income: Int {
        monthTransactions.filter { $0.type == "income" }.reduce(0) { $0 + $1.amountCents }
    }

    private var byCategory: [(name: String, cents: Int, share: Double)] {
        let expenses = monthTransactions.filter { $0.type == "expense" }
        let total = max(expenses.reduce(0) { $0 + $1.amountCents }, 1)
        let grouped = Dictionary(grouping: expenses) { transaction in
            store.snapshot.categories.first { $0.id == transaction.categoryId }?.name ?? "未分类"
        }
        return grouped
            .map { name, rows in
                let cents = rows.reduce(0) { $0 + $1.amountCents }
                return (name, cents, Double(cents) / Double(total))
            }
            .sorted { $0.cents > $1.cents }
    }
}
