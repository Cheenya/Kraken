import AppKit
import SwiftUI

@main
struct KrakenDesktopApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @StateObject private var store = KrakenDesktopStore()

    var body: some Scene {
        WindowGroup("Kraken Desktop") {
            ContentView()
                .environmentObject(store)
                .frame(minWidth: 980, maxWidth: .infinity, minHeight: 660, maxHeight: .infinity)
                .tint(KrakenColors.accent)
        }
        .windowStyle(.hiddenTitleBar)
        .commands {
            CommandGroup(after: .newItem) {
                Button("Новое устройство") {
                    store.importPeer(name: "Новое устройство")
                }
                .keyboardShortcut("n", modifiers: [.command, .shift])

                Button("Сменить маршрут") {
                    store.cycleSelectedRoute()
                }
                .keyboardShortcut("r", modifiers: [.command])
            }
        }
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationDidFinishLaunching(_ notification: Notification) {
        NSApp.setActivationPolicy(.regular)
        NSApp.activate(ignoringOtherApps: true)
    }
}
