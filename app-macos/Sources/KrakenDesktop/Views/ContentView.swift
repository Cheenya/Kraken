import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: KrakenDesktopStore
    @SceneStorage("kraken.sidebar.width") private var sidebarWidth: Double = 340
    @State private var identityQrVisible = false
    @State private var qrScannerVisible = false
    @State private var realmQr: DesktopRealm?

    var body: some View {
        let palette = store.effectivePalette

        ZStack {
            if store.state.localIdentity == nil {
                WelcomeIdentityView()
            } else if store.startGateVisible {
                ReturningIdentityView(qrAction: { identityQrVisible = true })
            } else {
                ManualSplitView(sidebarWidth: $sidebarWidth) {
                    SidebarView(onShowIdentityQr: { identityQrVisible = true })
                } detail: {
                    DetailContainer(
                        section: store.selectedSection,
                        onShowIdentityQr: { identityQrVisible = true },
                        onScanQr: { qrScannerVisible = true },
                        onShowRealmQr: { realmQr = $0 }
                    )
                }
            }

            if identityQrVisible, let identity = store.state.localIdentity {
                Color.black.opacity(0.38)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        identityQrVisible = false
                    }

                IdentityQrSheet(
                    identity: identity,
                    closeAction: { identityQrVisible = false }
                )
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(palette.accent.opacity(0.22), lineWidth: 1)
                }
                .shadow(color: .black.opacity(0.35), radius: 24, y: 12)
                .padding(24)
                .contentShape(RoundedRectangle(cornerRadius: 12))
                .onTapGesture { }
                .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }

            if let realmQr {
                Color.black.opacity(0.38)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        self.realmQr = nil
                    }

                RealmQrSheet(
                    realm: realmQr,
                    identity: store.state.localIdentity,
                    closeAction: { self.realmQr = nil }
                )
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(palette.accent.opacity(0.22), lineWidth: 1)
                }
                .shadow(color: .black.opacity(0.35), radius: 24, y: 12)
                .padding(24)
                .contentShape(RoundedRectangle(cornerRadius: 12))
                .onTapGesture { }
                .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }

            if qrScannerVisible {
                Color.black.opacity(0.38)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        qrScannerVisible = false
                    }

                QrScannerSheet(
                    closeAction: { qrScannerVisible = false },
                    importAction: store.importScannedInvite(_:)
                )
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(palette.accent.opacity(0.22), lineWidth: 1)
                }
                .shadow(color: .black.opacity(0.35), radius: 24, y: 12)
                .padding(24)
                .contentShape(RoundedRectangle(cornerRadius: 12))
                .onTapGesture { }
                .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }

            if let confirmation = store.pendingHandshakeConfirmation {
                Color.black.opacity(0.38)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        store.pendingHandshakeConfirmation = nil
                    }

                HandshakeConfirmationQrSheet(
                    confirmation: confirmation,
                    closeAction: { store.pendingHandshakeConfirmation = nil }
                )
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
                .overlay {
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(palette.accent.opacity(0.22), lineWidth: 1)
                }
                .shadow(color: .black.opacity(0.35), radius: 24, y: 12)
                .padding(24)
                .contentShape(RoundedRectangle(cornerRadius: 12))
                .onTapGesture { }
                .transition(.opacity.combined(with: .scale(scale: 0.98)))
            }
        }
        .environment(\.krakenPalette, palette)
        .animation(.easeOut(duration: 0.15), value: identityQrVisible)
        .animation(.easeOut(duration: 0.15), value: qrScannerVisible)
        .animation(.easeOut(duration: 0.15), value: realmQr?.id)
        .animation(.easeOut(duration: 0.15), value: store.pendingHandshakeConfirmation != nil)
        .background(palette.windowBackground)
        .tint(palette.accent)
        .preferredColorScheme(store.selectedTheme.preferredColorScheme)
        .ignoresSafeArea(.container, edges: .top)
        .overlay(alignment: .topLeading) {
            SidebarToggleRemovalBridge()
                .frame(width: 0, height: 0)
                .allowsHitTesting(false)
        }
    }
}

private struct ManualSplitView<Sidebar: View, Detail: View>: View {
    @Environment(\.krakenPalette) private var palette
    @Binding var sidebarWidth: Double
    @ViewBuilder var sidebar: Sidebar
    @ViewBuilder var detail: Detail

    private let minSidebarWidth: Double = 300
    private let defaultSidebarWidth: Double = 340
    private let maxSidebarWidth: Double = 420

    var body: some View {
        GeometryReader { proxy in
            let availableMax = max(minSidebarWidth, min(maxSidebarWidth, Double(proxy.size.width) * 0.46))
            let width = min(max(sidebarWidth, minSidebarWidth), availableMax)

            HStack(alignment: .top, spacing: 0) {
                sidebar
                    .frame(width: CGFloat(width))
                    .frame(maxHeight: .infinity, alignment: .top)
                    .clipped()

                SidebarResizeHandle(
                    sidebarWidth: $sidebarWidth,
                    minWidth: minSidebarWidth,
                    defaultWidth: min(defaultSidebarWidth, availableMax),
                    maxWidth: availableMax
                )

                detail
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(palette.detailBackground)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .onAppear {
                sidebarWidth = width
            }
        }
    }
}

private struct SidebarResizeHandle: View {
    @Environment(\.krakenPalette) private var palette
    @Binding var sidebarWidth: Double
    let minWidth: Double
    let defaultWidth: Double
    let maxWidth: Double
    @State private var dragStartWidth: Double?
    @State private var hovering = false

    var body: some View {
        Rectangle()
            .fill(hovering ? palette.accent.opacity(0.42) : palette.separator)
            .frame(width: hovering ? 4 : 1)
            .frame(width: 8)
            .frame(maxHeight: .infinity)
            .contentShape(Rectangle())
            .onHover { hovering = $0 }
            .onTapGesture(count: 2) {
                sidebarWidth = defaultWidth
            }
            .gesture(
                DragGesture()
                    .onChanged { value in
                        let start = dragStartWidth ?? sidebarWidth
                        dragStartWidth = start
                        sidebarWidth = min(max(start + Double(value.translation.width), minWidth), maxWidth)
                    }
                    .onEnded { _ in
                        dragStartWidth = nil
                    }
            )
    }
}

private struct DetailContainer: View {
    let section: DesktopSection
    let onShowIdentityQr: () -> Void
    let onScanQr: () -> Void
    let onShowRealmQr: (DesktopRealm) -> Void

    var body: some View {
        switch section {
        case .chat:
            ChatView(
                onShowIdentityQr: onShowIdentityQr,
                onScanQr: onScanQr
            )
        case .contacts:
            ContactsView()
        case .mesh:
            RealmsView(onShowRealmQr: onShowRealmQr)
        case .research:
            ResearchGateView()
        case .settings:
            SettingsView(onShowIdentityQr: onShowIdentityQr, onScanQr: onScanQr)
        }
    }
}
