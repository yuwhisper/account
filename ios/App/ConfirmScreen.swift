import LedgerCore
import SwiftUI

struct ConfirmScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.dismiss) private var dismiss
    let pendingId: String

    @State private var categoryId = ""
    @State private var note = ""
    @State private var type = "expense"

    private var pending: PendingPayment? {
        store.snapshot.pending.first { $0.id == pendingId }
    }

    var body: some View {
        NavigationStack {
            if let pending {
                Form {
                    Section {
                        Text(Money.yuan(pending.amountCents))
                            .font(.system(size: 40, weight: .semibold, design: .rounded))
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding(.vertical, 8)
                        LabeledContent("商户", value: pending.merchant.isEmpty ? "未识别" : pending.merchant)
                        LabeledContent("来源", value: SourceLabel.title(for: pending.source))
                        Picker("收支", selection: $type) {
                            Text("支出").tag("expense")
                            Text("收入").tag("income")
                        }
                        .pickerStyle(.segmented)
                    }
                    Section("分类") {
                        if store.snapshot.categories.isEmpty {
                            Text("先在分类页加一个分类")
                        } else {
                            Picker("分类", selection: $categoryId) {
                                ForEach(store.snapshot.categories) { category in
                                    Text(category.name).tag(category.id)
                                }
                            }
                            .pickerStyle(.inline)
                            .labelsHidden()
                        }
                    }
                    Section("备注") {
                        TextField("可选", text: $note)
                    }
                }
                .navigationTitle("确认入账")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("稍后") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("入账") {
                            store.confirm(pendingId: pending.id, categoryId: categoryId, note: note, type: type)
                            dismiss()
                        }
                        .disabled(categoryId.isEmpty)
                    }
                    ToolbarItem(placement: .bottomBar) {
                        Button("不是这笔", role: .destructive) {
                            store.dismissPending(id: pending.id)
                            dismiss()
                        }
                    }
                }
                .onAppear {
                    if categoryId.isEmpty {
                        categoryId = store.snapshot.categories.first?.id ?? ""
                    }
                }
            } else {
                ContentUnavailableView("这条待确认已经处理过了", systemImage: "checkmark")
            }
        }
    }
}
