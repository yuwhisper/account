import LedgerCore
import SwiftUI

struct CategoryScreen: View {
    @EnvironmentObject private var store: LedgerStore
    @State private var newName = ""
    @State private var renaming: String?
    @State private var renameText = ""

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        TextField("新分类", text: $newName)
                        Button("添加") {
                            store.addCategory(name: newName)
                            newName = ""
                        }
                        .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
                Section {
                    ForEach(store.snapshot.categories) { category in
                        Button {
                            renaming = category.id
                            renameText = category.name
                        } label: {
                            Text(category.name)
                                .foregroundStyle(.primary)
                        }
                    }
                    .onDelete { offsets in
                        let ids = offsets.map { store.snapshot.categories[$0].id }
                        ids.forEach { store.deleteCategory(id: $0) }
                    }
                }
            }
            .navigationTitle("分类")
            .alert("重命名", isPresented: Binding(
                get: { renaming != nil },
                set: { if !$0 { renaming = nil } }
            )) {
                TextField("名称", text: $renameText)
                Button("保存") {
                    if let renaming {
                        store.renameCategory(id: renaming, name: renameText)
                    }
                    renaming = nil
                }
                Button("取消", role: .cancel) { renaming = nil }
            }
        }
    }
}
