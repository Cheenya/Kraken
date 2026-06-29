import Foundation

public enum KrakenPacketPolicyError: Error, Equatable, Sendable, CustomStringConvertible {
    case missingPacketId
    case expired(packetId: String)
    case ttlExhausted(packetId: String)
    case duplicatePacket(packetId: String)

    public var description: String {
        switch self {
        case .missingPacketId:
            "packet-policy-missing-packet-id"
        case .expired(let packetId):
            "packet-policy-expired:\(packetId)"
        case .ttlExhausted(let packetId):
            "packet-policy-ttl-exhausted:\(packetId)"
        case .duplicatePacket(let packetId):
            "packet-policy-duplicate:\(packetId)"
        }
    }
}

public struct KrakenPacketPolicyValidator: Sendable {
    private var seenPacketIds: Set<String>
    private let maxSeenPacketIds: Int

    public init(seenPacketIds: Set<String> = [], maxSeenPacketIds: Int = 512) {
        self.seenPacketIds = seenPacketIds
        self.maxSeenPacketIds = maxSeenPacketIds
    }

    public mutating func acceptInbound(_ packet: KrakenPacket, nowMillis: Int64) throws {
        let packetId = packet.packetId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !packetId.isEmpty else {
            throw KrakenPacketPolicyError.missingPacketId
        }
        guard packet.expiresAtEpochMillis > nowMillis else {
            throw KrakenPacketPolicyError.expired(packetId: packetId)
        }
        guard packet.ttlHops > 0 else {
            throw KrakenPacketPolicyError.ttlExhausted(packetId: packetId)
        }
        guard !seenPacketIds.contains(packetId) else {
            throw KrakenPacketPolicyError.duplicatePacket(packetId: packetId)
        }
        seenPacketIds.insert(packetId)
        if seenPacketIds.count > maxSeenPacketIds {
            seenPacketIds.remove(seenPacketIds.first ?? packetId)
        }
    }
}
