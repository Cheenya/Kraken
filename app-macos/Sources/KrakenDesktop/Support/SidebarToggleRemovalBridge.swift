import AppKit
import SwiftUI

struct SidebarToggleRemovalBridge: NSViewRepresentable {
    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeNSView(context: Context) -> NSView {
        let view = NSView(frame: .zero)
        context.coordinator.attach(view)
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        context.coordinator.attach(nsView)
    }

    final class Coordinator {
        private weak var view: NSView?
        private var timer: Timer?

        func attach(_ view: NSView) {
            self.view = view
            removeNow()
            guard timer == nil else { return }
            timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
                self?.removeNow()
            }
        }

        deinit {
            timer?.invalidate()
        }

        private func removeNow() {
            DispatchQueue.main.async { [weak self] in
                SidebarToggleRemovalBridge.configureWindow(self?.view?.window)
            }
        }
    }

    private static func configureWindow(_ window: NSWindow?) {
        guard let window else { return }
        window.titleVisibility = .hidden
        window.titlebarAppearsTransparent = true
        window.styleMask.insert(.fullSizeContentView)
        window.toolbar = nil
        window.isMovableByWindowBackground = true
        removeSidebarToggle(from: window)
    }

    private static func removeSidebarToggle(from window: NSWindow?) {
        guard let toolbar = window?.toolbar else { return }
        for index in toolbar.items.indices.reversed() {
            let item = toolbar.items[index]
            if isSidebarToggle(item) {
                toolbar.removeItem(at: index)
            }
        }
    }

    private static func isSidebarToggle(_ item: NSToolbarItem) -> Bool {
        if item.itemIdentifier == .toggleSidebar {
            return true
        }
        let rawIdentifier = item.itemIdentifier.rawValue.lowercased()
        if rawIdentifier.contains("togglesidebar") || rawIdentifier.contains("toggle-sidebar") {
            return true
        }
        let label = item.label.lowercased()
        return label.contains("sidebar") || label.contains("боков") || label.contains("панел")
    }
}
