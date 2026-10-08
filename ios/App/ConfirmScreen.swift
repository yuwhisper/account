import LedgerCore
import SwiftUI

struct ConfirmScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric(relativeTo: .largeTitle) private var amountSize: CGFloat = 42
    @FocusState private var noteFocused: Bool
    let pendingId: String

    @State private var categoryId = ""
    @State private var note = ""
    @State private var type = "expense"
    @State private var showIgnore = false

    private var pending: PendingPayment? {
        store.snapshot.pending.first { $0.id == pendingId }
    }

    var body: some View {
        NavigationStack {
            if let pending {
                ScrollView {
                    VStack(alignment: .leading, spacing: 28) {
                        LedgerPageHeading(title: "确认入账", subtitle: "核对这一笔")
                        VStack(alignment: .leading, spacing: 12) {
                            Text(Money.yuan(pending.amountCents))
                                .font(LedgerTheme.amount(amountSize))
                                .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                                .minimumScaleFactor(dynamicTypeSize.isAccessibilitySize ? 1 : 0.65)
                                .fixedSize(horizontal: false, vertical: true)
                                .accessibilityLabel("金额 \(Money.yuan(pending.amountCents))")
                            Text(pending.merchant.isEmpty ? "未识别商户" : pending.merchant)
                                .font(.body.weight(.medium))
                            Text(SourceLabel.title(for: pending.source) + " · " + pending.occurredAt.formatted(date: .abbreviated, time: .shortened))
                                .font(.footnote)
                                .foregroundStyle(LedgerTheme.muted)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Picker("收支", selection: $type) {
                            Text("支出").tag("expense")
                            Text("收入").tag("income")
                        }
                        .pickerStyle(.segmented)
                        fieldRule
                        VStack(alignment: .leading, spacing: 14) {
                            Text("分类")
                                .font(.subheadline)
                                .foregroundStyle(LedgerTheme.muted)
                            if store.snapshot.categories.isEmpty {
                                Text("暂时没有分类，请先回到分类页添加。")
                                    .font(.subheadline)
                                    .foregroundStyle(LedgerTheme.muted)
                                    .padding(.vertical, 10)
                            } else {
                                LazyVGrid(columns: categoryColumns, spacing: 10) {
                                    ForEach(store.snapshot.categories) { category in
                                        categoryButton(category)
                                    }
                                }
                                .animation(reduceMotion ? nil : LedgerTheme.motion, value: categoryId)
                            }
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("备注")
                                    .font(.subheadline)
                                Spacer()
                                Text("可选")
                                    .font(.caption)
                            }
                            .foregroundStyle(LedgerTheme.muted)
                            TextField("记下用途或说明", text: $note, axis: .vertical)
                                .lineLimit(2...4)
                                .focused($noteFocused)
                                .padding(.vertical, 13)
                            fieldRule
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, 12)
                    .padding(.bottom, 28)
                }
                .scrollDismissesKeyboard(.interactively)
                .clipped()
                .safeAreaInset(edge: .bottom, spacing: 0) {
                    confirmationFooter(pending)
                }
                .ledgerPage()
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("稍后") { dismiss() }
                    }
                    if noteFocused {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button("完成") { noteFocused = false }
                        }
                    }
                }
                .sensoryFeedback(.selection, trigger: categoryId)
                .confirmationDialog("忽略这笔付款？", isPresented: $showIgnore, titleVisibility: .visible) {
                    Button("忽略这笔", role: .destructive) {
                        store.dismissPending(id: pending.id)
                        dismiss()
                    }
                    Button("继续核对", role: .cancel) { }
                } message: {
                    Text("这笔会从待确认中移除，不会记入账本。")
                }
                .onAppear {
                    if categoryId.isEmpty {
                        categoryId = store.snapshot.categories.first?.id ?? ""
                    }
                }
            } else {
                VStack {
                    LedgerEmptyState(symbol: "checkmark", title: "这笔已经处理好了", message: "回到账本，查看你的记录。")
                    Button("返回账本") { dismiss() }
                        .buttonStyle(LedgerPrimaryButtonStyle())
                        .padding(.horizontal, 24)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .ledgerPage()
            }
        }
    }

    private func confirmationFooter(_ pending: PendingPayment) -> some View {
        VStack(spacing: 12) {
            fieldRule
            Button {
                store.confirm(pendingId: pending.id, categoryId: categoryId, note: note, type: type)
                dismiss()
            } label: {
                Text("确认入账")
            }
            .buttonStyle(LedgerPrimaryButtonStyle())
            .disabled(categoryId.isEmpty)
            Button("忽略这笔", role: .destructive) {
                showIgnore = true
            }
            .font(.subheadline)
            .foregroundStyle(LedgerTheme.expense)
            .frame(minHeight: 44)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 8)
        .background(LedgerTheme.paper)
    }

    private var fieldRule: some View {
        Rectangle()
            .fill(LedgerTheme.border)
            .frame(height: 0.5)
    }

    private var categoryColumns: [GridItem] {
        dynamicTypeSize.isAccessibilitySize
            ? [GridItem(.flexible())]
            : [GridItem(.adaptive(minimum: 120), spacing: 10)]
    }

    private func categoryButton(_ category: CategoryRecord) -> some View {
        let selected = categoryId == category.id
        return Button { categoryId = category.id } label: {
            HStack(spacing: 8) {
                Image(systemName: LedgerTheme.categorySymbol(category.name))
                    .accessibilityHidden(true)
                Text(category.name)
                    .multilineTextAlignment(.leading)
                Spacer(minLength: 0)
                if selected {
                    Image(systemName: "checkmark")
                        .font(.caption.weight(.semibold))
                        .accessibilityHidden(true)
                }
            }
            .font(.subheadline.weight(selected ? .medium : .regular))
            .foregroundStyle(selected ? LedgerTheme.rose : LedgerTheme.ink)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 48)
            .background(selected ? LedgerTheme.roseSoft : Color.clear, in: RoundedRectangle(cornerRadius: 10))
            .overlay {
                RoundedRectangle(cornerRadius: 10)
                    .stroke(selected ? LedgerTheme.rose : LedgerTheme.border, lineWidth: 0.5)
            }
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
