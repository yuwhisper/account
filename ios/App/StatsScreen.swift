import LedgerCore
import SwiftUI

struct StatsScreen: View {
    @EnvironmentObject private var store: LedgerStore

    var body: some View {
        NavigationStack {
            List {
                Section("本月") {
                    LabeledContent("支出", value: Money.yuan(expense))
                    LabeledContent("收入", value: Money.yuan(income))
                }
                Section("支出分类") {
                    if byCategory.isEmpty {
                        Text("这个月还没有支出")
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach(byCategory, id: \.name) { row in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(row.name)
                                    Spacer()
                                    Text(Money.yuan(row.cents))
                                }
                                .font(.subheadline)
                                GeometryReader { proxy in
                                    Capsule()
                                        .fill(LedgerTheme.pine.opacity(0.85))
                                        .frame(width: max(8, proxy.size.width * row.share))
                                }
                                .frame(height: 8)
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }
            .navigationTitle("统计")
        }
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
