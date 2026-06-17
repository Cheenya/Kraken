import Foundation

public enum MacBleTransportError: Error, Equatable, Sendable {
    case invalidChunkPayloadSize
    case packetTooLarge
    case malformedChunk
    case unsupportedFrameVersion
    case missingTransferId
    case missingSenderFingerprint
    case invalidChunkCount
    case invalidChunkIndex
    case invalidPayloadSize
    case transferMetadataMismatch
    case missingChunk
    case payloadSizeMismatch
    case payloadChecksumMismatch
    case senderFingerprintMismatch
    case peerNotReady
}

public struct BleFrameChunk: Codable, Equatable, Sendable {
    public var frameVersion: Int
    public var transferId: String
    public var senderPeerId: String
    public var senderFingerprint: String
    public var senderDisplayName: String?
    public var chunkIndex: Int
    public var chunkCount: Int
    public var payloadSize: Int
    public var payloadCrc32: UInt32
    public var payloadBase64: String

    public init(
        frameVersion: Int = 1,
        transferId: String,
        senderPeerId: String,
        senderFingerprint: String,
        senderDisplayName: String? = nil,
        chunkIndex: Int,
        chunkCount: Int,
        payloadSize: Int,
        payloadCrc32: UInt32,
        payloadBase64: String
    ) {
        self.frameVersion = frameVersion
        self.transferId = transferId
        self.senderPeerId = senderPeerId
        self.senderFingerprint = senderFingerprint
        self.senderDisplayName = senderDisplayName
        self.chunkIndex = chunkIndex
        self.chunkCount = chunkCount
        self.payloadSize = payloadSize
        self.payloadCrc32 = payloadCrc32
        self.payloadBase64 = payloadBase64
    }

    private enum CodingKeys: String, CodingKey {
        case frameVersion = "frame_version"
        case transferId = "transfer_id"
        case senderPeerId = "sender_peer_id"
        case senderFingerprint = "sender_fingerprint"
        case senderDisplayName = "sender_display_name"
        case chunkIndex = "chunk_index"
        case chunkCount = "chunk_count"
        case payloadSize = "payload_size"
        case payloadCrc32 = "payload_crc32"
        case payloadBase64 = "payload_base64"
    }
}

public struct MacBlePeerIdentity: Codable, Equatable, Sendable {
    public var peerId: String
    public var fingerprint: String
    public var displayName: String?

    public init(peerId: String, fingerprint: String, displayName: String? = nil) {
        self.peerId = peerId
        self.fingerprint = fingerprint
        self.displayName = displayName
    }

    private enum CodingKeys: String, CodingKey {
        case peerId = "peer_id"
        case fingerprint
        case displayName = "display_name"
    }
}

public struct MacBleTransportStatus: Codable, Equatable, Sendable {
    public var modeId: String
    public var serviceUuid: String
    public var identityCharacteristicUuid: String
    public var packetCharacteristicUuid: String
    public var centralState: String
    public var peripheralState: String
    public var authorizationState: String
    public var discoveredPeerCount: Int
    public var inboundChunks: Int
    public var reassembledPackets: Int
    public var outboundChunks: Int
    public var lastError: String?

    public init(
        modeId: String = "ble-gatt",
        serviceUuid: String = MacBleConstants.serviceUuidString,
        identityCharacteristicUuid: String = MacBleConstants.identityCharacteristicUuidString,
        packetCharacteristicUuid: String = MacBleConstants.packetCharacteristicUuidString,
        centralState: String = "stopped",
        peripheralState: String = "stopped",
        authorizationState: String = "unknown",
        discoveredPeerCount: Int = 0,
        inboundChunks: Int = 0,
        reassembledPackets: Int = 0,
        outboundChunks: Int = 0,
        lastError: String? = nil
    ) {
        self.modeId = modeId
        self.serviceUuid = serviceUuid
        self.identityCharacteristicUuid = identityCharacteristicUuid
        self.packetCharacteristicUuid = packetCharacteristicUuid
        self.centralState = centralState
        self.peripheralState = peripheralState
        self.authorizationState = authorizationState
        self.discoveredPeerCount = discoveredPeerCount
        self.inboundChunks = inboundChunks
        self.reassembledPackets = reassembledPackets
        self.outboundChunks = outboundChunks
        self.lastError = lastError
    }
}

public enum MacBleEventDirection: String, Codable, Equatable, Sendable {
    case inbound
    case outbound
}

public enum MacBleEventStatus: String, Codable, Equatable, Sendable {
    case accepted
    case queued
    case failed
}

public struct MacBleTransferEvent: Identifiable, Codable, Equatable, Sendable {
    public var id: String
    public var direction: MacBleEventDirection
    public var status: MacBleEventStatus
    public var atEpochMillis: Int64
    public var peerFingerprint: String?
    public var packetId: String?
    public var messageId: String?
    public var payloadJson: String?
    public var senderDisplayName: String?
    public var senderFingerprint: String?
    public var recipientFingerprint: String?
    public var relationshipId: String?
    public var chunkCount: Int?
    public var error: String?

    public init(
        id: String = UUID().uuidString,
        direction: MacBleEventDirection,
        status: MacBleEventStatus,
        atEpochMillis: Int64,
        peerFingerprint: String?,
        packetId: String?,
        messageId: String?,
        payloadJson: String? = nil,
        senderDisplayName: String? = nil,
        senderFingerprint: String? = nil,
        recipientFingerprint: String? = nil,
        relationshipId: String? = nil,
        chunkCount: Int? = nil,
        error: String? = nil
    ) {
        self.id = id
        self.direction = direction
        self.status = status
        self.atEpochMillis = atEpochMillis
        self.peerFingerprint = peerFingerprint
        self.packetId = packetId
        self.messageId = messageId
        self.payloadJson = payloadJson
        self.senderDisplayName = senderDisplayName
        self.senderFingerprint = senderFingerprint
        self.recipientFingerprint = recipientFingerprint
        self.relationshipId = relationshipId
        self.chunkCount = chunkCount
        self.error = error
    }
}
