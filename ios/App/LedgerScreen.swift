import LedgerCore
import PhotosUI
import SwiftUI

struct LedgerScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .largeTitle) private var summarySize: CGFloat = 44
    @ScaledMetric(relativeTo: .body) private var transactionAmountSize: CGFloat = 20
    @Binding var presentedPending: PendingRoute?
    @State private var showManual = false
    @State private var showPaste = false
    @State private var pasteText = ""
    @State private var pendingPasteText: String?
    @State private var photoItem: PhotosPickerItem?
    @State private var banner = ""
    @State private var deletingTransactions: [String] = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    LedgerPageHeading(
                        title: "流水",
                        subtitle: Date().formatted(.dateTime.year().month(.wide).locale(Locale(identifier: "zh_CN")))
                    )
                    .padding(.top, 12)
                    .padding(.bottom, 16)
                    .listRowInsets(EdgeInsets(top: 0, leading: 24, bottom: 0, trailing: 24))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    monthSummary
                        .listRowInsets(EdgeInsets(top: 0, leading: 24, bottom: 0, trailing: 24))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
                if !store.snapshot.pending.isEmpty {
                    Section {
                        Button {
                            presentedPending = store.snapshot.pending.first.map { PendingRoute(id: $0.id) }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "checkmark.circle")
                                    .font(.system(size: 20, weight: .regular))
                                    .foregroundStyle(LedgerTheme.rose)
                                Text("\(store.snapshot.pending.count) 笔待确认")
                                    .font(.body)
                                    .foregroundStyle(LedgerTheme.ink)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 12, weight: .regular))
                                    .foregroundStyle(LedgerTheme.muted)
                            }
                            .padding(.vertical, 16)
                            .contentShape(Rectangle())
                            .overlay(alignment: .bottom) { rule }
                        }
                        .listRowInsets(EdgeInsets(top: 0, leading: 24, bottom: 0, trailing: 24))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    }
                }
                if store.snapshot.transactions.isEmpty {
                    Section {
                        VStack(alignment: .leading, spacing: 20) {
                            LedgerEmptyState(symbol: "book.closed", title: "从第一笔开始", message: "识别付款截图，或手动记一笔。")
                            Button { showManual = true } label: {
                                Label("记一笔", systemImage: "plus")
                            }
                            .buttonStyle(LedgerPrimaryButtonStyle())
                        }
                        .padding(.bottom, 20)
                        .listRowInsets(EdgeInsets(top: 0, leading: 24, bottom: 0, trailing: 24))
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                    }
                } else {
                    ForEach(grouped.keys.sorted(by: >), id: \.self) { day in
                        Section {
                            ForEach(grouped[day] ?? []) { transaction in
                                transactionRow(transaction)
                                    .listRowInsets(EdgeInsets(top: 0, leading: 24, bottom: 0, trailing: 24))
                                    .listRowBackground(Color.clear)
                                    .listRowSeparatorTint(LedgerTheme.border)
                            }
                            .onDelete { offsets in
                                delete(day: day, offsets: offsets)
                            }
                        } header: {
                            Text(day.dayTitle)
                                .font(.subheadline)
                                .foregroundStyle(LedgerTheme.muted)
                                .textCase(nil)
                                .padding(.top, 12)
                                .padding(.bottom, 5)
                        }
                    }
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .ledgerPage()
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .animation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0), value: store.snapshot.transactions)
            .animation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0), value: store.snapshot.pending)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Label("识别截图", systemImage: "photo")
                            .labelStyle(.iconOnly)
                            .font(.system(size: 18, weight: .regular))
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button {
                            pasteText = ""
                            showPaste = true
                        } label: {
                            Label("粘贴付款文字", systemImage: "doc.on.clipboard")
                        }
                        Button { showManual = true } label: {
                            Label("手动记账", systemImage: "square.and.pencil")
                        }
                    } label: {
                        Label("添加流水", systemImage: "plus")
                            .labelStyle(.iconOnly)
                            .font(.system(size: 18, weight: .regular))
                    }
                }
            }
            .sheet(isPresented: $showManual) {
                ManualEntryScreen().environmentObject(store).ledgerSheet()
            }
            .confirmationDialog("删除这笔记录？", isPresented: Binding(
                get: { !deletingTransactions.isEmpty },
                set: { if !$0 { deletingTransactions = [] } }
            ), titleVisibility: .visible) {
                Button("删除记录", role: .destructive) {
                    withAnimation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0)) {
                        deletingTransactions.forEach { store.deleteTransaction(id: $0) }
                        deletingTransactions = []
                    }
                }
                Button("取消", role: .cancel) { deletingTransactions = [] }
            } message: {
                Text("删除后，这笔记录会从账本中移除。")
            }
            .sheet(isPresented: $showPaste, onDismiss: {
                if let text = pendingPasteText {
                    pendingPasteText = nil
                    ingest(text: text)
                }
            }) {
                PastePaymentSheet(text: $pasteText) {
                    pendingPasteText = pasteText
                    showPaste = false
                }
                .ledgerSheet()
            }
            .onChange(of: photoItem) { _, item in
                guard let item else { return }
                Task { await ingest(photo: item) }
            }
            .overlay(alignment: .bottom) {
                if !banner.isEmpty {
                    Label(banner, systemImage: "info.circle")
                        .font(.subheadline)
                        .foregroundStyle(LedgerTheme.ink)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 14)
                        .background(LedgerTheme.surface, in: RoundedRectangle(cornerRadius: 8))
                        .overlay { RoundedRectangle(cornerRadius: 8).stroke(LedgerTheme.border, lineWidth: 0.5) }
                        .padding(16)
                        .transition(reduceMotion ? .opacity : .move(edge: .bottom).combined(with: .opacity))
                }
            }
            .animation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0), value: banner)
        }
    }

    private var monthSummary: some View {
        ZStack(alignment: .topTrailing) {
            if !dynamicTypeSize.isAccessibilitySize {
                LedgerOrchidArt()
                    .frame(width: 174, height: 148)
                    .opacity(0.5)
                    .offset(x: 24, y: -20)
                    .accessibilityHidden(true)
                    .allowsHitTesting(false)
            }
            VStack(alignment: .leading, spacing: 16) {
                Text("本月支出")
                    .font(.subheadline)
                    .foregroundStyle(LedgerTheme.muted)
                Text(Money.yuan(monthExpense))
                    .font(LedgerTheme.amount(summarySize))
                    .monospacedDigit()
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
                    .minimumScaleFactor(dynamicTypeSize.isAccessibilitySize ? 1 : 0.65)
                    .fixedSize(horizontal: false, vertical: true)
                    .foregroundStyle(LedgerTheme.ink)
                    .contentTransition(reduceMotion ? .identity : .numericText(value: Double(monthExpense)))
                    .animation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0), value: monthExpense)
                    .padding(.bottom, 8)
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .firstTextBaseline) {
                        Text("收入 \(Money.yuan(monthIncome))")
                        Spacer(minLength: 16)
                        Text("\(monthTransactions.count) 笔")
                    }
                    VStack(alignment: .leading, spacing: 8) {
                        Text("收入 \(Money.yuan(monthIncome))")
                        Text("\(monthTransactions.count) 笔")
                    }
                }
                .font(.footnote)
                .foregroundStyle(LedgerTheme.muted)
                .monospacedDigit()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.top, 8)
        .padding(.bottom, 24)
        .overlay(alignment: .bottom) { rule }
    }

    private var rule: some View {
        Rectangle().fill(LedgerTheme.border).frame(height: 0.5)
    }

    private var monthTransactions: [TransactionRecord] {
        store.snapshot.transactions.filter {
            Calendar.current.isDate($0.occurredAt, equalTo: Date(), toGranularity: .month)
        }
    }

    private var monthExpense: Int {
        monthTransactions.filter { $0.type == "expense" }.reduce(0) { $0 + $1.amountCents }
    }

    private var monthIncome: Int {
        monthTransactions.filter { $0.type == "income" }.reduce(0) { $0 + $1.amountCents }
    }

    private var grouped: [Date: [TransactionRecord]] {
        let calendar = Calendar.current
        let grouped = Dictionary(grouping: store.snapshot.transactions) { transaction in
            calendar.startOfDay(for: transaction.occurredAt)
        }
        return grouped.mapValues { $0.sorted { $0.occurredAt > $1.occurredAt } }
    }

    private func transactionRow(_ transaction: TransactionRecord) -> some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 12) {
                        transactionIcon(transaction)
                        Text(transaction.merchant.isEmpty ? "未填写商户" : transaction.merchant)
                            .font(.body)
                    }
                    transactionAmount(transaction)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    transactionDetails(transaction)
                }
            } else {
                HStack(alignment: .center, spacing: 12) {
                    transactionIcon(transaction)
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(alignment: .firstTextBaseline, spacing: 8) {
                            Text(transaction.merchant.isEmpty ? "未填写商户" : transaction.merchant)
                                .font(.body)
                                .lineLimit(1)
                            Spacer(minLength: 6)
                            transactionAmount(transaction)
                        }
                        transactionDetails(transaction)
                    }
                }
            }
        }
        .padding(.vertical, 17)
        .accessibilityElement(children: .combine)
    }

    private func transactionIcon(_ transaction: TransactionRecord) -> some View {
        Image(systemName: LedgerTheme.categorySymbol(categoryName(transaction.categoryId)))
            .font(.system(size: 18, weight: .regular))
            .foregroundStyle(LedgerTheme.muted)
            .frame(width: 25, height: 28)
            .accessibilityHidden(true)
    }

    private func transactionDetails(_ transaction: TransactionRecord) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(categoryName(transaction.categoryId) + " · " + SourceLabel.title(for: transaction.source))
                .font(.footnote)
                .foregroundStyle(LedgerTheme.muted)
            if !transaction.note.isEmpty {
                Text(transaction.note)
                    .font(.caption)
                    .foregroundStyle(LedgerTheme.muted)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
            }
        }
    }

    private func transactionAmount(_ transaction: TransactionRecord) -> some View {
        Text(Money.signed(transaction.amountCents, type: transaction.type))
            .font(LedgerTheme.amount(transactionAmountSize))
            .monospacedDigit()
            .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)
            .minimumScaleFactor(dynamicTypeSize.isAccessibilitySize ? 1 : 0.6)
            .fixedSize(horizontal: false, vertical: true)
            .foregroundStyle(transaction.type == "income" ? LedgerTheme.income : LedgerTheme.ink)
    }

    private func categoryName(_ id: String?) -> String {
        guard let id else { return "未分类" }
        return store.snapshot.categories.first { $0.id == id }?.name ?? "未分类"
    }

    private func delete(day: Date, offsets: IndexSet) {
        let rows = grouped[day] ?? []
        deletingTransactions = offsets.map { rows[$0].id }
    }

    private func ingest(text: String) {
        switch store.ingest(text: text) {
        case .added(let pending):
            presentedPending = PendingRoute(id: pending.id)
            show(message: "已放入待确认")
        case .duplicate:
            show(message: "这笔已经在待确认里")
        case .notPayment:
            show(message: "没有识别到付款成功")
        }
    }

    private func ingest(photo: PhotosPickerItem) async {
        defer { photoItem = nil }
        do {
            guard let data = try await photo.loadTransferable(type: Data.self),
                  let image = UIImage(data: data),
                  let cgImage = image.ledgerCGImage() else {
                show(message: "没有读到图片")
                return
            }
            switch try CapturePipeline.ingest(image: cgImage, into: store) {
            case .added(let pending):
                presentedPending = PendingRoute(id: pending.id)
                show(message: "已放入待确认")
            case .duplicate:
                show(message: "这笔已经在待确认里")
            case .notPayment:
                show(message: "没有识别到付款成功")
            }
        } catch {
            show(message: "识别失败")
        }
    }

    private func show(message: String) {
        banner = message
        Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            if banner == message { banner = "" }
        }
    }
}

private struct PastePaymentSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var text: String
    let recognize: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    LedgerPageHeading(title: "付款文字", subtitle: "识别后，核对金额与分类。")
                    LedgerField("付款文字") {
                        ZStack(alignment: .topLeading) {
                            if text.isEmpty {
                                Text("例如：微信支付成功，向商户付款 ¥28.00")
                                    .foregroundStyle(LedgerTheme.muted)
                                    .padding(.horizontal, 5)
                                    .padding(.top, 8)
                                    .allowsHitTesting(false)
                            }
                            TextEditor(text: $text)
                                .scrollContentBackground(.hidden)
                                .frame(minHeight: 220)
                                .accessibilityLabel("付款文字")
                        }
                        .ledgerInput()
                    }
                }
                .padding(24)
            }
            .scrollDismissesKeyboard(.interactively)
            .ledgerPage()
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                Button(action: recognize) {
                    Label("识别并核对", systemImage: "text.viewfinder")
                }
                .buttonStyle(LedgerPrimaryButtonStyle())
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .padding(20)
                .background(LedgerTheme.paper)
            }
        }
    }
}
