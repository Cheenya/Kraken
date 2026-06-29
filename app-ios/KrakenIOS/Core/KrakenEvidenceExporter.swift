import Foundation

public struct KrakenEvidenceExporter: Sendable {
    public var directory: URL

    public init(directory: URL = FileManager.default.temporaryDirectory) {
        self.directory = directory
    }

    public func writeEvidence(_ json: String, fileName: String = "kraken-ios-evidence.json") throws -> URL {
        let url = directory.appendingPathComponent(fileName)
        guard let data = json.data(using: .utf8, allowLossyConversion: false) else {
            throw KrakenEvidenceExportError.encodingFailed
        }
        try data.write(to: url, options: [.atomic])
        return url
    }
}

public enum KrakenEvidenceExportError: Error, Equatable, Sendable {
    case encodingFailed
}
