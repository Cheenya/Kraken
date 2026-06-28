import Foundation

public enum MacLanTransportError: Error, Equatable, Sendable {
    case invalidFrameLength
    case frameTooLarge
    case malformedFrame
    case senderFingerprintMismatch
    case ackMissing
}

public enum MacLanEventDirection: String, Codable, Equatable, Sendable {
    case inbound
    case outbound
}

public enum MacLanEventStatus: String, Codable, Equatable, Sendable {
    case accepted
    case acked
    case failed
}

public struct KrakenPacket: Codable, Equatable, Sendable {
    public var packetId: String
    public var protocolVersion: Int
    public var packetType: String
    public var senderFingerprint: String
    public var recipientFingerprint: String
    public var relationshipId: String
    public var conversationId: String
    public var messageId: String?
    public var createdAtEpochMillis: Int64
    public var expiresAtEpochMillis: Int64
    public var ttlHops: Int
    public var payloadType: String
    public var payloadJson: String
    public var cryptoProfileId: String?
    public var sessionProfileId: String?
    public var admissionDecisionHash: String?
    public var profilePolicyVersion: Int?
    public var proofMode: String

    public init(
        packetId: String,
        protocolVersion: Int = 1,
        packetType: String = "MESSAGE",
        senderFingerprint: String,
        recipientFingerprint: String,
        relationshipId: String,
        conversationId: String,
        messageId: String?,
        createdAtEpochMillis: Int64,
        expiresAtEpochMillis: Int64,
        ttlHops: Int = 4,
        payloadType: String = "LOCAL_MESSAGE_JSON",
        payloadJson: String,
        cryptoProfileId: String? = "standard-reviewed-primitives-v1",
        sessionProfileId: String? = nil,
        admissionDecisionHash: String? = "sha256:standard-reviewed-primitives-v1:not-applicable:v1",
        profilePolicyVersion: Int? = 1,
        proofMode: String = "local-admission-check-v1"
    ) {
        self.packetId = packetId
        self.protocolVersion = protocolVersion
        self.packetType = packetType
        self.senderFingerprint = senderFingerprint
        self.recipientFingerprint = recipientFingerprint
        self.relationshipId = relationshipId
        self.conversationId = conversationId
        self.messageId = messageId
        self.createdAtEpochMillis = createdAtEpochMillis
        self.expiresAtEpochMillis = expiresAtEpochMillis
        self.ttlHops = ttlHops
        self.payloadType = payloadType
        self.payloadJson = payloadJson
        self.cryptoProfileId = cryptoProfileId
        self.sessionProfileId = sessionProfileId
        self.admissionDecisionHash = admissionDecisionHash
        self.profilePolicyVersion = profilePolicyVersion
        self.proofMode = proofMode
    }

    private enum CodingKeys: String, CodingKey {
        case packetId = "packet_id"
        case protocolVersion = "protocol_version"
        case packetType = "packet_type"
        case senderFingerprint = "sender_fingerprint"
        case recipientFingerprint = "recipient_fingerprint"
        case relationshipId = "relationship_id"
        case conversationId = "conversation_id"
        case messageId = "message_id"
        case createdAtEpochMillis = "created_at_epoch_millis"
        case expiresAtEpochMillis = "expires_at_epoch_millis"
        case ttlHops = "ttl_hops"
        case payloadType = "payload_type"
        case payloadJson = "payload_json"
        case cryptoProfileId = "crypto_profile_id"
        case sessionProfileId = "session_profile_id"
        case admissionDecisionHash = "admission_decision_hash"
        case profilePolicyVersion = "profile_policy_version"
        case proofMode = "proof_mode"
    }
}

public struct LanFrameEnvelope: Codable, Equatable, Sendable {
    public var frameVersion: Int
    public var senderPeerId: String
    public var senderFingerprint: String
    public var senderDisplayName: String?
    public var senderReplyPort: Int?
    public var packet: KrakenPacket

    public init(
        frameVersion: Int = 1,
        senderPeerId: String,
        senderFingerprint: String,
        senderDisplayName: String? = nil,
        senderReplyPort: Int? = nil,
        packet: KrakenPacket
    ) {
        self.frameVersion = frameVersion
        self.senderPeerId = senderPeerId
        self.senderFingerprint = senderFingerprint
        self.senderDisplayName = senderDisplayName
        self.senderReplyPort = senderReplyPort
        self.packet = packet
    }

    private enum CodingKeys: String, CodingKey {
        case frameVersion = "frame_version"
        case senderPeerId = "sender_peer_id"
        case senderFingerprint = "sender_fingerprint"
        case senderDisplayName = "sender_display_name"
        case senderReplyPort = "sender_reply_port"
        case packet
    }
}

public struct MacLanEndpoint: Codable, Equatable, Sendable {
    public var host: String
    public var port: Int
    public var fingerprint: String
    public var displayName: String?

    public init(host: String, port: Int, fingerprint: String, displayName: String? = nil) {
        self.host = host
        self.port = port
        self.fingerprint = fingerprint
        self.displayName = displayName
    }
}

public struct MacLanTransferEvent: Identifiable, Codable, Equatable, Sendable {
    public var id: String
    public var direction: MacLanEventDirection
    public var status: MacLanEventStatus
    public var atEpochMillis: Int64
    public var source: String?
    public var target: String?
    public var packetId: String?
    public var messageId: String?
    public var payloadJson: String?
    public var senderDisplayName: String?
    public var senderFingerprint: String?
    public var recipientFingerprint: String?
    public var relationshipId: String?
    public var error: String?

    public init(
        id: String = UUID().uuidString,
        direction: MacLanEventDirection,
        status: MacLanEventStatus,
        atEpochMillis: Int64,
        source: String?,
        target: String?,
        packetId: String?,
        messageId: String?,
        payloadJson: String? = nil,
        senderDisplayName: String? = nil,
        senderFingerprint: String?,
        recipientFingerprint: String?,
        relationshipId: String?,
        error: String? = nil
    ) {
        self.id = id
        self.direction = direction
        self.status = status
        self.atEpochMillis = atEpochMillis
        self.source = source
        self.target = target
        self.packetId = packetId
        self.messageId = messageId
        self.payloadJson = payloadJson
        self.senderDisplayName = senderDisplayName
        self.senderFingerprint = senderFingerprint
        self.recipientFingerprint = recipientFingerprint
        self.relationshipId = relationshipId
        self.error = error
    }
}
