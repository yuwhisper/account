import LedgerCore
import SwiftUI

struct PendingRoute: Identifiable {
    let id: String
}

struct RootView: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.scenePhase) private var scenePhase
    @State private var presentedPending: PendingRoute?

    var body: some View {
        TabView {
            LedgerScreen(presentedPending: $presentedPending)
                .tabItem { Label("流水", systemImage: "list.bullet") }
            StatsScreen()
                .tabItem { Label("统计", systemImage: "chart.bar") }
            CategoryScreen()
                .tabItem { Label("分类", systemImage: "square.grid.2x2") }
            SettingsScreen()
                .tabItem { Label("设置", systemImage: "gearshape") }
        }
        .tint(LedgerTheme.pine)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { store.reload() }
        }
        .onOpenURL { url in
            guard url.scheme == "yuwhisper" else { return }
            store.reload()
            presentedPending = store.snapshot.pending.first.map { PendingRoute(id: $0.id) }
        }
        .sheet(item: $presentedPending) { route in
            ConfirmScreen(pendingId: route.id)
                .environmentObject(store)
        }
    }
}
