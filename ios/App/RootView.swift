import LedgerCore
import SwiftUI

struct PendingRoute: Identifiable {
    let id: String
}

struct RootView: View {
    @EnvironmentObject private var store: LedgerStore
    @Environment(\.scenePhase) private var scenePhase
    @State private var presentedPending: PendingRoute?
    @State private var selectedTab = 0
    @AppStorage("ledgerAppearance", store: UserDefaults(suiteName: "group.com.yuwhisper.account")) private var appearance = "system"

    var body: some View {
        TabView(selection: $selectedTab) {
            LedgerScreen(presentedPending: $presentedPending)
                .tabItem { Label("流水", systemImage: "list.bullet.rectangle") }
                .tag(0)
            StatsScreen()
                .tabItem { Label("统计", systemImage: "chart.bar.xaxis") }
                .tag(1)
            CategoryScreen()
                .tabItem { Label("分类", systemImage: "square.grid.2x2") }
                .tag(2)
            SettingsScreen()
                .tabItem { Label("设置", systemImage: "gearshape") }
                .tag(3)
        }
        .tint(LedgerTheme.rose)
        .preferredColorScheme(appearance == "light" ? .light : appearance == "dark" ? .dark : nil)
        .toolbarBackground(LedgerTheme.surface, for: .tabBar)
        .toolbarBackground(.visible, for: .tabBar)
        .sensoryFeedback(.selection, trigger: selectedTab)
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
                .ledgerSheet()
        }
    }
}
