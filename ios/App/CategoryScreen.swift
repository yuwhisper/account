import LedgerCore
import SwiftUI

struct CategoryScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var newName = ""
    @State private var renaming: String?
    @State private var renameText = ""
    @FocusState private var nameFocused: Bool
    @State private var deletingCategories: [String] = []

    var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 28) {
                        LedgerPageHeading(title: "分类", subtitle: "整理日常收支")
                        LedgerField("新分类") {
                            HStack(spacing: 12) {
                                TextField("分类名称", text: $newName)
                                    .focused($nameFocused)
                                    .submitLabel(.done)
                                    .onSubmit { addCategory() }
                                    .ledgerInput()
                                Button("添加") { addCategory() }
                                    .font(.body)
                                    .buttonStyle(.bordered)
                                    .controlSize(.large)
                                    .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                            }
                        }
                    }
                    .padding(.top, 12)
                    .padding(.bottom, 28)
                    .overlay(alignment: .bottom) { rule }
                    .listRowInsets(EdgeInsets(top: 0, leading: 24, bottom: 0, trailing: 24))
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
                Section {
                    if store.snapshot.categories.isEmpty {
                        LedgerEmptyState(symbol: "square.grid.2x2", title: "还没有分类", message: "添加一个常用分类，开始整理账本。")
                            .listRowInsets(EdgeInsets(top: 0, leading: 24, bottom: 0, trailing: 24))
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
                    } else {
                        ForEach(store.snapshot.categories) { category in
                            Button {
                                nameFocused = false
                                renaming = category.id
                                renameText = category.name
                            } label: {
                                HStack(spacing: 14) {
                                    Image(systemName: LedgerTheme.categorySymbol(category.name))
                                        .font(.system(size: 18, weight: .regular))
                                        .foregroundStyle(LedgerTheme.muted)
                                        .frame(width: 25, height: 28)
                                        .accessibilityHidden(true)
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(category.name)
                                            .font(.body)
                                            .foregroundStyle(LedgerTheme.ink)
                                        Text("\(transactionCount(category.id)) 笔记录")
                                            .font(.footnote)
                                            .foregroundStyle(LedgerTheme.muted)
                                    }
                                    Spacer()
                                    Image(systemName: "pencil")
                                        .font(.system(size: 16, weight: .regular))
                                        .foregroundStyle(LedgerTheme.muted)
                                        .accessibilityHidden(true)
                                }
                                .padding(.vertical, 17)
                                .contentShape(Rectangle())
                            }
                            .accessibilityLabel("\(category.name)，编辑名称")
                            .listRowInsets(EdgeInsets(top: 0, leading: 24, bottom: 0, trailing: 24))
                            .listRowBackground(Color.clear)
                            .listRowSeparatorTint(LedgerTheme.border)
                        }
                        .onDelete { offsets in
                            deletingCategories = offsets.map { store.snapshot.categories[$0].id }
                        }
                    }
                } header: {
                    HStack(alignment: .firstTextBaseline) {
                        Text("我的分类")
                            .font(LedgerTheme.heading(24))
                            .foregroundStyle(LedgerTheme.ink)
                        Spacer()
                        Text("\(store.snapshot.categories.count) 类")
                            .font(.footnote)
                            .foregroundStyle(LedgerTheme.muted)
                    }
                    .textCase(nil)
                    .padding(.top, 24)
                    .padding(.bottom, 10)
                } footer: {
                    Text("轻点改名，向左滑动删除。")
                        .font(.footnote)
                        .foregroundStyle(LedgerTheme.muted)
                        .padding(.top, 16)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .ledgerPage()
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .animation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0), value: store.snapshot.categories)
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完成") { nameFocused = false }
                }
            }
            .sheet(isPresented: Binding(
                get: { renaming != nil },
                set: { if !$0 { renaming = nil } }
            )) {
                RenameCategorySheet(name: $renameText) {
                    if let renaming {
                        withAnimation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0)) {
                            store.renameCategory(id: renaming, name: renameText)
                        }
                    }
                    renaming = nil
                }
                .presentationDetents([.medium, .large])
                .ledgerSheet()
            }
            .confirmationDialog("删除这个分类？", isPresented: Binding(
                get: { !deletingCategories.isEmpty },
                set: { if !$0 { deletingCategories = [] } }
            ), titleVisibility: .visible) {
                Button("删除分类", role: .destructive) {
                    withAnimation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0)) {
                        deletingCategories.forEach { store.deleteCategory(id: $0) }
                        deletingCategories = []
                    }
                }
                Button("取消", role: .cancel) { deletingCategories = [] }
            } message: {
                Text("已有流水会保留，并显示为未分类。")
            }
        }
    }

    private func addCategory() {
        guard !newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        withAnimation(reduceMotion ? nil : .snappy(duration: 0.28, extraBounce: 0)) {
            store.addCategory(name: newName)
        }
        newName = ""
        nameFocused = false
    }

    private func transactionCount(_ id: String) -> Int {
        store.snapshot.transactions.filter { $0.categoryId == id }.count
    }

    private var rule: some View {
        Rectangle().fill(LedgerTheme.border).frame(height: 0.5)
    }
}

private struct RenameCategorySheet: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var name: String
    let save: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    LedgerPageHeading(title: "修改分类")
                    LedgerField("分类名称") {
                        TextField("例如：餐饮", text: $name)
                            .submitLabel(.done)
                            .onSubmit { if validName { save() } }
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
                Button("保存名称", action: save)
                    .buttonStyle(LedgerPrimaryButtonStyle())
                    .disabled(!validName)
                    .padding(20)
                    .background(LedgerTheme.paper)
            }
        }
    }

    private var validName: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}
