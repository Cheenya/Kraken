import SwiftUI

@main
struct KrakenIOSApp: App {
    @StateObject private var store: KrakenIOSStore
    @StateObject private var transport = IOSNearbyTransportAdapter()
    private let deepLinkRouter = KrakenDeepLinkRouter()

    init() {
        let arguments = ProcessInfo.processInfo.arguments
        let isDemoMode = arguments.contains("--kraken-demo")
            || ProcessInfo.processInfo.environment["KRAKEN_DEMO_MODE"] == "1"
        let initialStore: KrakenIOSStore
        if isDemoMode {
            initialStore = KrakenIOSFixtures.makeDemoStore()
        } else {
            initialStore = KrakenIOSStore(persistence: try? KrakenIOSPersistence.applicationDefault())
        }
        _store = StateObject(wrappedValue: initialStore)
    }

    var body: some Scene {
        WindowGroup {
            KrakenIOSRootView(store: store, transport: transport)
                .onOpenURL { url in
                    store.handleDeepLink(deepLinkRouter.route(url))
                }
        }
    }
}
