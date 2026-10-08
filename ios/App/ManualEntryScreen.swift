import LedgerCore
import SwiftUI

struct ManualEntryScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric(relativeTo: .largeTitle) private var amountSize: CGFloat = 40
    @FocusState private var focusedField: Field?
    @State private var amount = ""
    @State private var merchant = ""
    @State private var note = ""
    @State private var type = "expense"
    @State private var categoryId = ""

    private enum Field: Hashable { case amount, merchant, note }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    LedgerPageHeading(title: "手动记账", subtitle: "记下这一笔")
                    Picker("收支", selection: $type) {
                        Text("支出").tag("expense")
                        Text("收入").tag("income")
                    }
                    .pickerStyle(.segmented)
                    VStack(alignment: .leading, spacing: 8) {
                        fieldTitle("金额 · 元")
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text("¥")
                                .font(LedgerTheme.amount(26))
                                .foregroundStyle(LedgerTheme.muted)
                            TextField("0.00", text: $amount)
                                .font(LedgerTheme.amount(amountSize))
                                .keyboardType(.decimalPad)
                                .focused($focusedField, equals: .amount)
                                .accessibilityLabel("金额，单位元")
                                .padding(.vertical, 8)
                        }
                        fieldRule
                        if !amount.isEmpty && Money.cents(from: amount) == nil {
                            Text("请填写有效金额，最多两位小数")
                                .font(.footnote)
                                .foregroundStyle(LedgerTheme.expense)
                                .transition(.opacity)
                        }
                    }
                    VStack(alignment: .leading, spacing: 22) {
                        VStack(alignment: .leading, spacing: 4) {
                            fieldTitle("商户")
                            TextField("这笔花在哪里？", text: $merchant)
                                .focused($focusedField, equals: .merchant)
                                .submitLabel(.next)
                                .onSubmit { focusedField = .note }
                                .padding(.vertical, 13)
                            fieldRule
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            fieldTitle("分类")
                            Picker("分类", selection: $categoryId) {
                                Text("不分类").tag("")
                                ForEach(store.snapshot.categories) { category in
                                    Text(category.name).tag(category.id)
                                }
                            }
                            .pickerStyle(.menu)
                            .frame(maxWidth: .infinity, minHeight: 48, alignment: .leading)
                            fieldRule
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                fieldTitle("备注")
                                Spacer()
                                Text("可选")
                                    .font(.caption)
                                    .foregroundStyle(LedgerTheme.muted)
                            }
                            TextField("记下用途或说明", text: $note, axis: .vertical)
                                .lineLimit(2...4)
                                .focused($focusedField, equals: .note)
                                .padding(.vertical, 13)
                            fieldRule
                        }
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 12)
                .padding(.bottom, 28)
            }
            .scrollDismissesKeyboard(.interactively)
            .clipped()
            .safeAreaInset(edge: .bottom, spacing: 0) {
                saveFooter
            }
            .ledgerPage()
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                if focusedField != nil {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("完成") { focusedField = nil }
                    }
                }
            }
            .animation(reduceMotion ? nil : LedgerTheme.motion, value: type)
            .onAppear {
                if categoryId.isEmpty {
                    categoryId = store.snapshot.categories.first?.id ?? ""
                }
            }
        }
    }

    private var saveFooter: some View {
        VStack(spacing: 16) {
            fieldRule
            Button { save() } label: {
                Text("保存这笔")
            }
            .buttonStyle(LedgerPrimaryButtonStyle())
            .disabled(Money.cents(from: amount) == nil)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 12)
        .background(LedgerTheme.paper)
    }

    private func fieldTitle(_ title: String) -> some View {
        Text(title)
            .font(.subheadline)
            .foregroundStyle(LedgerTheme.muted)
    }

    private var fieldRule: some View {
        Rectangle()
            .fill(LedgerTheme.border)
            .frame(height: 0.5)
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
