import LedgerCore
import SwiftUI

struct ManualEntryScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.dismiss) private var dismiss
    @State private var amount = ""
    @State private var merchant = ""
    @State private var note = ""
    @State private var type = "expense"
    @State private var categoryId = ""

    var body: some View {
        NavigationStack {
            Form {
                TextField("金额", text: $amount)
                    .keyboardType(.decimalPad)
                TextField("商户", text: $merchant)
                Picker("收支", selection: $type) {
                    Text("支出").tag("expense")
                    Text("收入").tag("income")
                }
                Picker("分类", selection: $categoryId) {
                    Text("不分类").tag("")
                    ForEach(store.snapshot.categories) { category in
                        Text(category.name).tag(category.id)
                    }
                }
                TextField("备注", text: $note)
            }
            .navigationTitle("手动记账")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") { save() }
                        .disabled(Money.cents(from: amount) == nil)
                }
            }
            .onAppear {
                if categoryId.isEmpty {
                    categoryId = store.snapshot.categories.first?.id ?? ""
                }
            }
        }
    }

    private func save() {
        guard let cents = Money.cents(from: amount) else { return }
        store.addManual(
            amountCents: cents,
            merchant: merchant.trimmingCharacters(in: .whitespacesAndNewlines),
            categoryId: categoryId.isEmpty ? nil : categoryId,
            note: note.trimmingCharacters(in: .whitespacesAndNewlines),
            type: type
        )
        dismiss()
    }
}
