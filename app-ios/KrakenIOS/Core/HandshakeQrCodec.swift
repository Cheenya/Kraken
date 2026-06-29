import Compression
import Foundation
import zlib

public enum KrakenHandshakePayloadKind: String, Equatable, Sendable {
    case invite
    case response
    case confirmation
    case unknown
    case invalid
}

public enum KrakenQrEncodingError: Error, Sendable {
    case invalidJsonPayload
    case compressionFailed
}

public enum KrakenHandshakeQrCodec {
    public static let inviteTypeName = "one_time_invite"
    public static let responseTypeName = "kraken.handshake.response.v1"
    public static let confirmationTypeName = "kraken.handshake.confirmation.v1"
    private static let webScheme = "https"
    private static let webHost = "kraken.local"
    private static let webPath = "/qr"

    public static func encodedQrPayload(_ rawPayload: String) throws -> String {
        guard let compact = compactJsonPayload(rawPayload),
              let compactData = compact.data(using: .utf8) else {
            throw KrakenQrEncodingError.invalidJsonPayload
        }
        guard let compressed = rawDeflatedData(compactData) else {
            throw KrakenQrEncodingError.compressionFailed
        }
        let encodedPayload = compressed.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .trimmingCharacters(in: CharacterSet(charactersIn: "="))
        return "\(webScheme)://\(webHost)\(webPath)?v=2&z=d&p=\(encodedPayload)"
    }

    public static func normalizedScannedPayload(_ rawPayload: String) -> String {
        normalizedScannedPayload(rawPayload, depth: 0)
    }

    private static func normalizedScannedPayload(_ rawPayload: String, depth: Int) -> String {
        let trimmed = rawPayload.trimmingCharacters(in: .whitespacesAndNewlines)
        guard depth < 3 else { return trimmed }

        if let decodedString = jsonStringValue(in: trimmed) {
            return normalizedScannedPayload(decodedString, depth: depth + 1)
        }

        if let nested = nestedJsonPayload(in: trimmed) {
            return normalizedScannedPayload(nested, depth: depth + 1)
        }

        if let qrPayload = payloadFromKrakenQrUri(trimmed) {
            return normalizedScannedPayload(qrPayload, depth: depth + 1)
        }

        if let schemePayload = payloadFromKrakenScheme(trimmed) {
            return normalizedScannedPayload(schemePayload, depth: depth + 1)
        }

        if let components = URLComponents(string: trimmed),
           let queryItems = components.queryItems {
            for key in ["payload_json", "payload", "data", "qr", "json"] {
                if let value = queryItems.first(where: { $0.name == key })?.value?
                    .removingPercentEncoding?
                    .trimmingCharacters(in: .whitespacesAndNewlines),
                    let candidate = normalizedPayloadCandidate(value) {
                    return normalizedScannedPayload(candidate, depth: depth + 1)
                }
            }
        }

        if let fragmentPayload = payloadFromUrlFragment(trimmed) {
            return normalizedScannedPayload(fragmentPayload, depth: depth + 1)
        }

        if let percentDecoded = trimmed.removingPercentEncoding,
           percentDecoded != trimmed,
           looksLikeWrappedPayload(percentDecoded) {
            return normalizedScannedPayload(percentDecoded, depth: depth + 1)
        }

        if let decoded = decodedBase64Payload(trimmed),
           looksLikeWrappedPayload(decoded) {
            return normalizedScannedPayload(decoded, depth: depth + 1)
        }

        return trimmed
    }

    public static func detectKind(_ rawPayload: String) -> KrakenHandshakePayloadKind {
        let payload = normalizedScannedPayload(rawPayload)
        guard let data = payload.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return .invalid
        }
        guard let type = (object["type"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !type.isEmpty else {
            return .unknown
        }
        switch type {
        case inviteTypeName: return .invite
        case responseTypeName: return .response
        case confirmationTypeName: return .confirmation
        default: return .unknown
        }
    }

    public static func decodeResponse(_ rawPayload: String) throws -> KrakenHandshakeResponsePayload {
        let payload = normalizedScannedPayload(rawPayload)
        let data = Data(payload.utf8)
        return try JSONDecoder().decode(KrakenHandshakeResponsePayload.self, from: data)
    }

    public static func decodeInvite(_ rawPayload: String) throws -> KrakenHandshakeInvitePayload {
        let payload = normalizedScannedPayload(rawPayload)
        let data = Data(payload.utf8)
        return try JSONDecoder().decode(KrakenHandshakeInvitePayload.self, from: data)
    }

    public static func decodeConfirmation(_ rawPayload: String) throws -> KrakenHandshakeConfirmationPayload {
        let payload = normalizedScannedPayload(rawPayload)
        let data = Data(payload.utf8)
        return try JSONDecoder().decode(KrakenHandshakeConfirmationPayload.self, from: data)
    }

    public static func decodeFailureDescription(_ error: Error) -> String {
        guard let decodingError = error as? DecodingError else {
            return error.localizedDescription
        }
        switch decodingError {
        case .keyNotFound(let key, _):
            return "не найдено поле \(fieldName(key.stringValue))"
        case .valueNotFound(_, let context):
            return "поле \(fieldName(context.codingPath.last?.stringValue)) пустое или отсутствует"
        case .typeMismatch(_, let context):
            return "поле \(fieldName(context.codingPath.last?.stringValue)) имеет неверный тип"
        case .dataCorrupted(let context):
            return context.debugDescription.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? "повреждённый JSON"
                : context.debugDescription
        @unknown default:
            return error.localizedDescription
        }
    }

    public static func payloadSummary(_ rawPayload: String) -> String {
        let payload = normalizedScannedPayload(rawPayload)
        guard let data = payload.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return "Payload не является JSON-объектом."
        }
        let type = (object["type"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let keys = object.keys.sorted().prefix(12).joined(separator: ", ")
        return "Тип: \((type?.isEmpty == false ? type : nil) ?? "без типа"). Поля: \(keys.isEmpty ? "нет полей" : keys)."
    }

    private static func nestedJsonPayload(in payload: String) -> String? {
        guard let data = payload.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return nil
        }
        for key in ["payload_json", "payload", "data", "qr_payload", "raw_json"] {
            if let value = object[key] as? String {
                let nested = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if nested.hasPrefix("{") {
                    return nested
                }
            }
            if let nestedObject = object[key] as? [String: Any],
               JSONSerialization.isValidJSONObject(nestedObject),
               let nestedData = try? JSONSerialization.data(withJSONObject: nestedObject, options: [.sortedKeys]),
               let nested = String(data: nestedData, encoding: .utf8) {
                return nested
            }
        }
        return nil
    }

    private static func jsonStringValue(in payload: String) -> String? {
        guard let data = payload.data(using: .utf8),
              let value = try? JSONDecoder().decode(String.self, from: data) else {
            return nil
        }
        let nested = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return looksLikeWrappedPayload(nested) ? nested : nil
    }

    private static func payloadFromKrakenQrUri(_ payload: String) -> String? {
        let normalized = payload
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&#38;", with: "&")
        guard let components = URLComponents(string: normalized),
              let scheme = components.scheme?.lowercased() else {
            return nil
        }
        let host = components.host?.lowercased()
        let isKrakenUri = scheme == "kraken" && host == "qr"
        let isIntentUri = scheme == "intent" && host == "qr"
        let isWebUri = scheme == webScheme && host == webHost && components.path == webPath
        guard isKrakenUri || isIntentUri || isWebUri else {
            return nil
        }
        if isIntentUri {
            guard components.fragment?.range(of: "scheme=kraken", options: [.caseInsensitive]) != nil else {
                return nil
            }
        }
        let query = queryItemsByName(from: components)
        guard let encodedPayload = query["p"] ?? query["payload"],
              let payloadData = decodedBase64Data(encodedPayload) else {
            return nil
        }
        let decodedData: Data
        if isDeflated(query["z"]) {
            guard let inflated = rawInflatedData(payloadData) ?? zlibInflatedData(payloadData) else { return nil }
            decodedData = inflated
        } else {
            decodedData = payloadData
        }
        guard let decoded = String(data: decodedData, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines) else {
            return nil
        }
        return compactJsonPayload(decoded) ?? decoded
    }

    private static func payloadFromKrakenScheme(_ payload: String) -> String? {
        guard payload.lowercased().hasPrefix("kraken:") else { return nil }
        let rawTail = String(payload.dropFirst("kraken:".count))
            .trimmingCharacters(in: CharacterSet(charactersIn: "/ "))
        let decodedTail = rawTail.removingPercentEncoding ?? rawTail
        let trimmedTail = decodedTail.trimmingCharacters(in: .whitespacesAndNewlines)
        return looksLikeWrappedPayload(trimmedTail) ? trimmedTail : nil
    }

    private static func payloadFromUrlFragment(_ payload: String) -> String? {
        guard let components = URLComponents(string: payload),
              let fragment = components.fragment?.removingPercentEncoding?
            .trimmingCharacters(in: .whitespacesAndNewlines),
              !fragment.isEmpty else {
            return nil
        }
        if looksLikeWrappedPayload(fragment) {
            return fragment
        }
        let fragmentComponents = URLComponents(string: "kraken://fragment?\(fragment)")
        for key in ["payload_json", "payload", "data", "qr", "json"] {
            if let value = fragmentComponents?.queryItems?.first(where: { $0.name == key })?.value?
                .removingPercentEncoding?
                .trimmingCharacters(in: .whitespacesAndNewlines),
               let candidate = normalizedPayloadCandidate(value) {
                return candidate
            }
        }
        return nil
    }

    private static func queryItemsByName(from components: URLComponents) -> [String: String] {
        var values: [String: String] = [:]
        for item in components.queryItems ?? [] {
            guard let value = item.value else { continue }
            values[item.name.lowercased()] = value
        }
        return values
    }

    private static func normalizedPayloadCandidate(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if looksLikeWrappedPayload(trimmed) {
            return trimmed
        }
        if let decoded = decodedBase64Payload(trimmed),
           looksLikeWrappedPayload(decoded) {
            return decoded
        }
        return nil
    }

    private static func decodedBase64Payload(_ value: String) -> String? {
        guard let data = decodedBase64Data(value),
              let decoded = String(data: data, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines) else {
            return nil
        }
        return decoded
    }

    private static func decodedBase64Data(_ value: String) -> Data? {
        let compact = value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        guard !compact.isEmpty, compact.count % 4 != 1 else { return nil }

        let paddingLength = (4 - compact.count % 4) % 4
        let padded = compact + String(repeating: "=", count: paddingLength)
        return Data(base64Encoded: padded)
    }

    private static func rawInflatedData(_ data: Data) -> Data? {
        inflateData(data, windowBits: -MAX_WBITS)
    }

    private static func zlibInflatedData(_ data: Data) -> Data? {
        inflateData(data, windowBits: MAX_WBITS)
    }

    private static func inflateData(_ data: Data, windowBits: Int32) -> Data? {
        guard !data.isEmpty else { return Data() }
        var stream = z_stream()
        guard inflateInit2_(&stream, windowBits, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size)) == Z_OK else {
            return nil
        }
        defer { inflateEnd(&stream) }

        return data.withUnsafeBytes { inputBuffer -> Data? in
            guard let input = inputBuffer.bindMemory(to: Bytef.self).baseAddress else {
                return Data()
            }
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: input)
            stream.avail_in = uInt(data.count)

            var output = Data()
            let chunkSize = 16 * 1024
            while true {
                var chunk = [UInt8](repeating: 0, count: chunkSize)
                let result = chunk.withUnsafeMutableBytes { chunkBuffer -> Int32 in
                    stream.next_out = chunkBuffer.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(chunkSize)
                    return inflate(&stream, Z_NO_FLUSH)
                }
                let produced = chunkSize - Int(stream.avail_out)
                if produced > 0 {
                    output.append(chunk, count: produced)
                }
                if result == Z_STREAM_END {
                    return output
                }
                guard result == Z_OK else {
                    return nil
                }
            }
        }
    }

    private static func legacyCompressionInflatedData(_ data: Data) -> Data? {
        guard !data.isEmpty else { return Data() }
        var capacity = max(4096, data.count * 8)
        let maxCapacity = 4 * 1024 * 1024
        while capacity <= maxCapacity {
            let decoded = UnsafeMutablePointer<UInt8>.allocate(capacity: capacity)
            defer { decoded.deallocate() }
            let decodedCount = data.withUnsafeBytes { sourceBuffer in
                guard let source = sourceBuffer.bindMemory(to: UInt8.self).baseAddress else { return 0 }
                return compression_decode_buffer(
                    decoded,
                    capacity,
                    source,
                    data.count,
                    nil,
                    COMPRESSION_ZLIB
                )
            }
            if decodedCount > 0 {
                return Data(bytes: decoded, count: decodedCount)
            }
            capacity *= 2
        }
        return nil
    }

    private static func rawDeflatedData(_ data: Data) -> Data? {
        guard !data.isEmpty else { return Data() }
        var stream = z_stream()
        guard deflateInit2_(
            &stream,
            Z_BEST_COMPRESSION,
            Z_DEFLATED,
            -MAX_WBITS,
            8,
            Z_DEFAULT_STRATEGY,
            ZLIB_VERSION,
            Int32(MemoryLayout<z_stream>.size)
        ) == Z_OK else {
            return nil
        }
        defer { deflateEnd(&stream) }

        return data.withUnsafeBytes { inputBuffer -> Data? in
            guard let input = inputBuffer.bindMemory(to: Bytef.self).baseAddress else {
                return Data()
            }
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: input)
            stream.avail_in = uInt(data.count)

            var output = Data()
            let chunkSize = 16 * 1024
            while true {
                var chunk = [UInt8](repeating: 0, count: chunkSize)
                let result = chunk.withUnsafeMutableBytes { chunkBuffer -> Int32 in
                    stream.next_out = chunkBuffer.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(chunkSize)
                    return deflate(&stream, Z_FINISH)
                }
                let produced = chunkSize - Int(stream.avail_out)
                if produced > 0 {
                    output.append(chunk, count: produced)
                }
                if result == Z_STREAM_END {
                    return output
                }
                guard result == Z_OK else {
                    return nil
                }
            }
        }
    }

    private static func compactJsonPayload(_ payload: String) -> String? {
        guard let data = payload.data(using: .utf8),
              let object = try? JSONSerialization.jsonObject(with: data),
              JSONSerialization.isValidJSONObject(object),
              let compact = try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]),
              let string = String(data: compact, encoding: .utf8) else {
            return nil
        }
        return string
    }

    private static func looksLikeWrappedPayload(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.hasPrefix("{") ||
            trimmed.hasPrefix("\"{") ||
            trimmed.hasPrefix("kraken:") ||
            trimmed.hasPrefix("https://\(webHost)\(webPath)") ||
            trimmed.hasPrefix("intent:") ||
            URLComponents(string: trimmed)?.queryItems?.isEmpty == false
    }

    private static func isDeflated(_ value: String?) -> Bool {
        guard let value else { return false }
        return value.caseInsensitiveCompare("deflate") == .orderedSame ||
            value.caseInsensitiveCompare("d") == .orderedSame
    }

    private static func fieldName(_ value: String?) -> String {
        let field = value?.trimmingCharacters(in: .whitespacesAndNewlines)
        return "`\((field?.isEmpty == false ? field : nil) ?? "неизвестное поле")`"
    }
}

public enum KrakenInviteScope: String, Decodable, Equatable, Sendable {
    case directContact = "DIRECT_CONTACT"
    case realmMembership = "REALM_MEMBERSHIP"
}

public struct KrakenHandshakeInvitePayload: Decodable, Equatable, Sendable {
    public let type: String
    public let version: Int
    public let inviteId: String
    public let scope: KrakenInviteScope?
    public let realmId: String?
    public let realmName: String?
    public let inviterDisplayName: String?
    public let inviterFingerprint: String
    public let inviterPublicKeyEncoded: String
    public let createdAtEpochMillis: Int64
    public let expiresAtEpochMillis: Int64?
    public let oneTime: Bool?
    public let requiresHandshake: Bool?
    public let requiresApproval: Bool?
    public let cryptoProfileId: String?
    public let cryptoProfileHash: String?
    public let admissionDecisionHash: String?
    public let profilePolicyVersion: Int?
    public let nativeBackendVersion: String?

    enum CodingKeys: String, CodingKey {
        case type
        case version
        case inviteId = "invite_id"
        case scope
        case realmId = "realm_id"
        case realmName = "realm_name"
        case inviterDisplayName = "inviter_display_name"
        case inviterFingerprint = "inviter_fingerprint"
        case inviterPublicKeyEncoded = "inviter_public_key_encoded"
        case createdAtEpochMillis = "created_at_epoch_millis"
        case expiresAtEpochMillis = "expires_at_epoch_millis"
        case oneTime = "one_time"
        case requiresHandshake = "requires_handshake"
        case requiresApproval = "requires_approval"
        case cryptoProfileId = "crypto_profile_id"
        case cryptoProfileHash = "crypto_profile_hash"
        case admissionDecisionHash = "admission_decision_hash"
        case profilePolicyVersion = "profile_policy_version"
        case nativeBackendVersion = "native_backend_version"
    }
}

public struct KrakenHandshakeResponsePayload: Decodable, Equatable, Sendable {
    public let type: String
    public let version: Int
    public let responseId: String
    public let inviteId: String
    public let realmId: String?
    public let requiresApproval: Bool?
    public let responderFingerprint: String
    public let responderDisplayName: String
    public let responderPublicKeyEncoded: String
    public let inviterFingerprint: String
    public let createdAtEpochMillis: Int64
    public let relationshipHint: String?
    public let cryptoProfileId: String?
    public let cryptoProfileHash: String?
    public let admissionDecisionHash: String?
    public let profilePolicyVersion: Int?
    public let nativeBackendVersion: String?
    public let proofPlaceholder: String?

    enum CodingKeys: String, CodingKey {
        case type
        case version
        case responseId = "response_id"
        case inviteId = "invite_id"
        case realmId = "realm_id"
        case requiresApproval = "requires_approval"
        case responderFingerprint = "responder_fingerprint"
        case responderDisplayName = "responder_display_name"
        case responderPublicKeyEncoded = "responder_public_key_encoded"
        case inviterFingerprint = "inviter_fingerprint"
        case createdAtEpochMillis = "created_at_epoch_millis"
        case relationshipHint = "relationship_hint"
        case cryptoProfileId = "crypto_profile_id"
        case cryptoProfileHash = "crypto_profile_hash"
        case admissionDecisionHash = "admission_decision_hash"
        case profilePolicyVersion = "profile_policy_version"
        case nativeBackendVersion = "native_backend_version"
        case proofPlaceholder = "proof_placeholder"
    }
}

public struct KrakenHandshakeConfirmationPayload: Decodable, Equatable, Sendable {
    public let type: String
    public let version: Int
    public let confirmationId: String
    public let responseId: String
    public let inviteId: String
    public let realmId: String?
    public let realmName: String?
    public let inviterFingerprint: String
    public let responderFingerprint: String
    public let createdAtEpochMillis: Int64
    public let cryptoProfileId: String?
    public let cryptoProfileHash: String?
    public let admissionDecisionHash: String?
    public let profilePolicyVersion: Int?
    public let nativeBackendVersion: String?
    public let proofPlaceholder: String?

    enum CodingKeys: String, CodingKey {
        case type
        case version
        case confirmationId = "confirmation_id"
        case responseId = "response_id"
        case inviteId = "invite_id"
        case realmId = "realm_id"
        case realmName = "realm_name"
        case inviterFingerprint = "inviter_fingerprint"
        case responderFingerprint = "responder_fingerprint"
        case createdAtEpochMillis = "created_at_epoch_millis"
        case cryptoProfileId = "crypto_profile_id"
        case cryptoProfileHash = "crypto_profile_hash"
        case admissionDecisionHash = "admission_decision_hash"
        case profilePolicyVersion = "profile_policy_version"
        case nativeBackendVersion = "native_backend_version"
        case proofPlaceholder = "proof_placeholder"
    }
}
