import Combine
import Foundation
import MultipeerConnectivity

public struct IOSNearbyPeer: Identifiable, Equatable, Sendable {
    public var id: String { displayName }
    public var displayName: String
}

public enum IOSNearbyTransportState: Equatable, Sendable {
    case stopped
    case searching(localPeer: String)
    case connected(localPeer: String, peers: [String])
    case failed(String)

    public var title: String {
        switch self {
        case .stopped:
            "остановлен"
        case .searching(let localPeer):
            "поиск: \(localPeer)"
        case .connected(_, let peers):
            "подключено: \(peers.joined(separator: ", "))"
        case .failed(let error):
            "ошибка: \(error)"
        }
    }
}

public enum IOSNearbyTransportError: Error, Equatable, Sendable {
    case noConnectedPeers
    case peerNotConnected(String)
}

public struct IOSNearbyTransportDescriptor: Equatable, Sendable {
    public let serviceType = "kraken-ios"
    public let transportId = "ios-multipeerconnectivity"
    public let routeKind: PeerRouteKind = .appleNearby
    public let boundaryNote = "Kraken использует Apple MultipeerConnectivity для локальной связи на устройствах Apple."

    public init() {}
}

@MainActor
public final class IOSNearbyTransportAdapter: NSObject, ObservableObject {
    @Published public private(set) var state: IOSNearbyTransportState = .stopped
    @Published public private(set) var discoveredPeers: [IOSNearbyPeer] = []
    @Published public private(set) var events: [String] = []

    public let descriptor: IOSNearbyTransportDescriptor
    public var onReceiveData: ((Data, String) -> Void)?

    private var peerID: MCPeerID?
    private var session: MCSession?
    private var advertiser: MCNearbyServiceAdvertiser?
    private var browser: MCNearbyServiceBrowser?

    public init(descriptor: IOSNearbyTransportDescriptor = IOSNearbyTransportDescriptor()) {
        self.descriptor = descriptor
        super.init()
    }

    public func start(displayName: String) {
        stop()
        let localName = displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "Kraken"
            : displayName.trimmingCharacters(in: .whitespacesAndNewlines)
        let peerID = MCPeerID(displayName: localName)
        let session = MCSession(peer: peerID, securityIdentity: nil, encryptionPreference: .required)
        let advertiser = MCNearbyServiceAdvertiser(
            peer: peerID,
            discoveryInfo: ["transport": descriptor.transportId],
            serviceType: descriptor.serviceType
        )
        let browser = MCNearbyServiceBrowser(peer: peerID, serviceType: descriptor.serviceType)
        session.delegate = self
        advertiser.delegate = self
        browser.delegate = self
        advertiser.startAdvertisingPeer()
        browser.startBrowsingForPeers()

        self.peerID = peerID
        self.session = session
        self.advertiser = advertiser
        self.browser = browser
        state = .searching(localPeer: localName)
        record("Локальная связь запущена: \(localName)")
    }

    public func stop() {
        advertiser?.stopAdvertisingPeer()
        browser?.stopBrowsingForPeers()
        session?.disconnect()
        advertiser = nil
        browser = nil
        session = nil
        peerID = nil
        discoveredPeers = []
        state = .stopped
    }

    public func send(_ data: Data) throws {
        guard let session, !session.connectedPeers.isEmpty else {
            throw IOSNearbyTransportError.noConnectedPeers
        }
        try session.send(data, toPeers: session.connectedPeers, with: .reliable)
        record("Отправлено \(data.count) байт через локальную связь")
    }

    public func send(_ data: Data, toPeerNamed peerDisplayName: String) throws {
        guard let session, !session.connectedPeers.isEmpty else {
            throw IOSNearbyTransportError.noConnectedPeers
        }
        let targetName = peerDisplayName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let targetPeer = session.connectedPeers.first(where: { $0.displayName == targetName }) else {
            throw IOSNearbyTransportError.peerNotConnected(targetName)
        }
        try session.send(data, toPeers: [targetPeer], with: .reliable)
        record("Отправлено \(data.count) байт на \(targetName) через локальную связь")
    }

    private func record(_ event: String) {
        events.insert(event, at: 0)
        if events.count > 50 {
            events.removeLast(events.count - 50)
        }
    }

    private func updateConnectedPeers(_ peers: [String]) {
        let localName = peerID?.displayName ?? "Kraken"
        state = peers.isEmpty ? .searching(localPeer: localName) : .connected(localPeer: localName, peers: peers)
    }
}

extension IOSNearbyTransportAdapter: MCSessionDelegate {
    nonisolated public func session(
        _ session: MCSession,
        peer peerID: MCPeerID,
        didChange state: MCSessionState
    ) {
        let connectedPeers = session.connectedPeers.map(\.displayName)
        let peerName = peerID.displayName
        let status: String = switch state {
        case .notConnected: "not-connected"
        case .connecting: "connecting"
        case .connected: "connected"
        @unknown default: "unknown"
        }
        Task { @MainActor [weak self] in
            self?.updateConnectedPeers(connectedPeers)
            self?.record("Устройство \(peerName): \(status)")
        }
    }

    nonisolated public func session(
        _ session: MCSession,
        didReceive data: Data,
        fromPeer peerID: MCPeerID
    ) {
        let peerName = peerID.displayName
        let byteCount = data.count
        Task { @MainActor [weak self] in
            self?.record("Получено \(byteCount) байт от \(peerName)")
            self?.onReceiveData?(data, peerName)
        }
    }

    nonisolated public func session(
        _ session: MCSession,
        didReceive stream: InputStream,
        withName streamName: String,
        fromPeer peerID: MCPeerID
    ) {}

    nonisolated public func session(
        _ session: MCSession,
        didStartReceivingResourceWithName resourceName: String,
        fromPeer peerID: MCPeerID,
        with progress: Progress
    ) {}

    nonisolated public func session(
        _ session: MCSession,
        didFinishReceivingResourceWithName resourceName: String,
        fromPeer peerID: MCPeerID,
        at localURL: URL?,
        withError error: Error?
    ) {}
}

extension IOSNearbyTransportAdapter: MCNearbyServiceAdvertiserDelegate {
    nonisolated public func advertiser(
        _ advertiser: MCNearbyServiceAdvertiser,
        didReceiveInvitationFromPeer peerID: MCPeerID,
        withContext context: Data?,
        invitationHandler: @escaping (Bool, MCSession?) -> Void
    ) {
        let peerName = peerID.displayName
        Task { @MainActor [weak self] in
            self?.record("Принято приглашение от \(peerName)")
            invitationHandler(true, self?.session)
        }
    }

    nonisolated public func advertiser(
        _ advertiser: MCNearbyServiceAdvertiser,
        didNotStartAdvertisingPeer error: Error
    ) {
        Task { @MainActor [weak self] in
            self?.state = .failed(error.localizedDescription)
            self?.record("advertising failed: \(error.localizedDescription)")
        }
    }
}

extension IOSNearbyTransportAdapter: MCNearbyServiceBrowserDelegate {
    nonisolated public func browser(
        _ browser: MCNearbyServiceBrowser,
        foundPeer peerID: MCPeerID,
        withDiscoveryInfo info: [String: String]?
    ) {
        let peerName = peerID.displayName
        Task { @MainActor [weak self] in
            guard let self else { return }
            guard info?["transport"] == descriptor.transportId else {
                record("Пропущено несовместимое устройство: \(peerName)")
                return
            }
            if !discoveredPeers.contains(where: { $0.displayName == peerName }) {
                discoveredPeers.append(IOSNearbyPeer(displayName: peerName))
            }
            record("Найдено локальное устройство: \(peerName)")
            if let session {
                browser.invitePeer(peerID, to: session, withContext: nil, timeout: 20)
            }
        }
    }

    nonisolated public func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        let peerName = peerID.displayName
        Task { @MainActor [weak self] in
            self?.discoveredPeers.removeAll { $0.displayName == peerName }
            self?.record("Устройство потеряно: \(peerName)")
        }
    }

    nonisolated public func browser(
        _ browser: MCNearbyServiceBrowser,
        didNotStartBrowsingForPeers error: Error
    ) {
        Task { @MainActor [weak self] in
            self?.state = .failed(error.localizedDescription)
            self?.record("browsing failed: \(error.localizedDescription)")
        }
    }
}
