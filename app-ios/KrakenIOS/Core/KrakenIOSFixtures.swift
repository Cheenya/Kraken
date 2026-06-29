import Foundation

public enum KrakenIOSFixtures {
    public static let fixedDate = Date(timeIntervalSince1970: 1_800_000_000)

    @MainActor
    public static func makeDemoStore() -> KrakenIOSStore {
        let ids = DeterministicIDSequence([
            "identity",
            "public",
            "fingerprint-local",
            "relationship",
            "realm",
            "message-one",
            "message-two",
        ])
        let simulator = KrakenIOSSimulator(
            now: { fixedDate },
            makeId: { ids.next() }
        )
        let store = KrakenIOSStore(simulator: simulator)
        store.createIdentity(displayName: "Kraken")
        store.importPeer(displayName: "Android Xiaomi", fingerprint: "ANDROID-FP-7A91")
        store.createRealm(name: "Локальный круг")
        store.sendMessage("Тестовое сообщение из Kraken")
        if let messageId = store.state.messages.last?.messageId {
            store.markTransportFailure(messageId: messageId, error: "demo-peer-offline")
            store.retryMessage(messageId: messageId)
        }
        store.recordDiagnosticEvent("Демо-режим: фиксированные данные для скриншотов")
        return store
    }
}

private final class DeterministicIDSequence: @unchecked Sendable {
    private let lock = NSLock()
    private var values: [String]

    init(_ values: [String]) {
        self.values = values
    }

    func next() -> String {
        lock.lock()
        defer { lock.unlock() }
        guard !values.isEmpty else { return "fallback" }
        return values.removeFirst()
    }
}
