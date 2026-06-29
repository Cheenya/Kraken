import Foundation

public struct KrakenIOSStoreSnapshot: Codable, Equatable, Sendable {
    public var state: KrakenIOSState
    public var selectedRelationshipId: String?
    public var outboxRecords: [KrakenOutboxRecord]
    public var diagnosticEvents: [String]

    public init(
        state: KrakenIOSState,
        selectedRelationshipId: String?,
        outboxRecords: [KrakenOutboxRecord],
        diagnosticEvents: [String]
    ) {
        self.state = state
        self.selectedRelationshipId = selectedRelationshipId
        self.outboxRecords = outboxRecords
        self.diagnosticEvents = diagnosticEvents
    }
}

public struct KrakenIOSPersistence: Sendable {
    public var fileURL: URL

    public init(fileURL: URL) {
        self.fileURL = fileURL
    }

    public static func applicationDefault() throws -> KrakenIOSPersistence {
        let directory = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let krakenDirectory = directory.appendingPathComponent("KrakenIOS", isDirectory: true)
        try FileManager.default.createDirectory(at: krakenDirectory, withIntermediateDirectories: true)
        return KrakenIOSPersistence(fileURL: krakenDirectory.appendingPathComponent("state.json"))
    }

    public func load() throws -> KrakenIOSStoreSnapshot? {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return nil }
        let data = try Data(contentsOf: fileURL)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode(KrakenIOSStoreSnapshot.self, from: data)
    }

    public func save(_ snapshot: KrakenIOSStoreSnapshot) throws {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        let data = try encoder.encode(snapshot)
        try data.write(to: fileURL, options: [.atomic])
    }
}
