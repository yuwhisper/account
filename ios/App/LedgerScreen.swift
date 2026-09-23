import LedgerCore
import PhotosUI
import SwiftUI

struct LedgerScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @Binding var presentedPending: PendingRoute?
    @State private var showManual = false
    @State private var showPaste = false
    @State private var pasteText = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var banner = ""

    var body: some View {
        NavigationStack {
            List {
                if !store.snapshot.pending.isEmpty {
                    Section {
                        Button {
                            presentedPending = store.snapshot.pending.first.map { PendingRoute(id: $0.id) }
                        } label: {
                            HStack {
                                Image(systemName: "checkmark.circle")
                                Text("\(store.snapshot.pending.count) 笔待确认")
                                Spacer()
                                Text(Money.yuan(store.snapshot.pending.first?.amountCents ?? 0))
                            }
                        }
                    }
                }
                if store.snapshot.transactions.isEmpty {
                    ContentUnavailableView("还没有账", systemImage: "tray", description: Text("付款后截一张图，或手动记一笔。"))
                } else {
                    ForEach(grouped.keys.sorted(by: >), id: \.self) { day in
                        Section(day.dayTitle) {
                            ForEach(grouped[day] ?? []) { transaction in
                                transactionRow(transaction)
                            }
                            .onDelete { offsets in
                                delete(day: day, offsets: offsets)
                            }
                        }
                    }
                }
            }
            .navigationTitle("流水")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    PhotosPicker(selection: $photoItem, matching: .images) {
                        Image(systemName: "photo")
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
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showManual) { ManualEntryScreen().environmentObject(store) }
            .sheet(isPresented: $showPaste) {
                NavigationStack {
                    TextEditor(text: $pasteText)
                        .padding()
                        .navigationTitle("粘贴付款文字")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) {
                                Button("取消") { showPaste = false }
                            }
                            ToolbarItem(placement: .confirmationAction) {
                                Button("识别") {
                                    showPaste = false
                                    ingest(text: pasteText)
                                }
                            }
                        }
                }
            }
            .onChange(of: photoItem) { _, item in
                guard let item else { return }
                Task { await ingest(photo: item) }
            }
            .overlay(alignment: .bottom) {
                if !banner.isEmpty {
                    Text(banner)
                        .font(.footnote)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.bottom, 8)
                }
            }
        }
    }

    private var grouped: [Date: [TransactionRecord]] {
        let calendar = Calendar.current
        let grouped = Dictionary(grouping: store.snapshot.transactions) { transaction in
            calendar.startOfDay(for: transaction.occurredAt)
        }
        return grouped.mapValues { $0.sorted { $0.occurredAt > $1.occurredAt } }
    }

    private func transactionRow(_ transaction: TransactionRecord) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(transaction.merchant.isEmpty ? "未填写商户" : transaction.merchant)
                Text(categoryName(transaction.categoryId) + " · " + SourceLabel.title(for: transaction.source))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Text(Money.signed(transaction.amountCents, type: transaction.type))
                .foregroundStyle(transaction.type == "income" ? LedgerTheme.income : LedgerTheme.expense)
        }
    }

    private func categoryName(_ id: String?) -> String {
        guard let id else { return "未分类" }
        return store.snapshot.categories.first { $0.id == id }?.name ?? "未分类"
    }

    private func delete(day: Date, offsets: IndexSet) {
        let rows = grouped[day] ?? []
            let ids = offsets.map { rows[$0].id }
            ids.forEach { store.deleteTransaction(id: $0) }
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
