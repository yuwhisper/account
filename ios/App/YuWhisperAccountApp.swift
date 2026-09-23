import LedgerCore
import SwiftUI

@main
struct YuWhisperAccountApp: App {
    @StateObject private var store = LedgerStore(fileURL: LedgerLocations.storeURL())

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
        }
    }
}
