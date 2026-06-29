import Foundation

public enum KrakenDeepLinkAction: Equatable, Sendable {
    case importQrPayload(String)
    case unsupported
}

public struct KrakenDeepLinkRouter: Sendable {
    public init() {}

    public func route(_ url: URL) -> KrakenDeepLinkAction {
        guard url.scheme?.lowercased() == "kraken" else { return .unsupported }
        let host = url.host()?.lowercased()
        guard host == "import" || host == "qr" else { return .unsupported }
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let payload = components?.queryItems?.first(where: { $0.name == "payload" })?.value
        guard let payload, !payload.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return .unsupported
        }
        return .importQrPayload(payload)
    }
}
