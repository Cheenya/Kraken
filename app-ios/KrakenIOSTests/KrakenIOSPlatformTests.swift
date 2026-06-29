import XCTest
@testable import KrakenIOS

@MainActor
final class KrakenIOSPlatformTests: XCTestCase {
    func testDeepLinkRouterImportsQrPayload() throws {
        let payload = "kraken://payload"
        let encoded = try XCTUnwrap(payload.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed))
        let action = KrakenDeepLinkRouter().route(try XCTUnwrap(URL(string: "kraken://import?payload=\(encoded)")))

        XCTAssertEqual(action, .importQrPayload(payload))
    }

    func testEvidenceExporterWritesJsonArtifact() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let url = try KrakenEvidenceExporter(directory: directory).writeEvidence("{\"ok\":true}")

        XCTAssertEqual(try String(contentsOf: url, encoding: .utf8), "{\"ok\":true}")
    }

    func testPersistenceRoundTripsStoreSnapshot() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let persistence = KrakenIOSPersistence(fileURL: directory.appendingPathComponent("state.json"))
        let store = KrakenIOSFixtures.makeDemoStore()
        let snapshot = KrakenIOSStoreSnapshot(
            state: store.state,
            selectedRelationshipId: store.selectedRelationshipId,
            outboxRecords: store.outboxRecords.values.sorted { $0.messageId < $1.messageId },
            diagnosticEvents: store.diagnosticEvents
        )

        try persistence.save(snapshot)
        let loaded = try XCTUnwrap(persistence.load())

        XCTAssertEqual(loaded.state.localIdentity?.displayName, "Kraken")
        XCTAssertEqual(loaded.state.relationships.first?.peerDisplayName, "Android Xiaomi")
        XCTAssertEqual(loaded.outboxRecords.first?.messageId, store.outboxRecords.values.first?.messageId)
    }

    func testDemoStoreIsDeterministicForScreenshots() {
        let first = KrakenIOSFixtures.makeDemoStore()
        let second = KrakenIOSFixtures.makeDemoStore()

        XCTAssertEqual(first.state, second.state)
        XCTAssertEqual(first.outboxRecords, second.outboxRecords)
        XCTAssertTrue(first.diagnosticEvents.contains("Демо-режим: фиксированные данные для скриншотов"))
    }
}
