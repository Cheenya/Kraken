import Foundation

public enum MacBleConstants {
    public static let serviceUuidString = "58a1257c-f4a8-48c8-99d5-917b9863d7c4"
    public static let identityCharacteristicUuidString = "58a1257d-f4a8-48c8-99d5-917b9863d7c4"
    public static let packetCharacteristicUuidString = "58a1257e-f4a8-48c8-99d5-917b9863d7c4"
}

public enum BleFrameCodec {
    public static let maxPacketBytes = 32 * 1024
    public static let defaultChunkPayloadBytes = 24
    public static let maxGattWriteBytes = 512

    public static func encodeChunks(
        packet: KrakenPacket,
        senderPeerId: String,
        senderFingerprint: String,
        senderDisplayName: String? = nil,
        chunkPayloadBytes: Int = defaultChunkPayloadBytes
    ) throws -> [Data] {
        guard (1...1024).contains(chunkPayloadBytes) else {
            throw MacBleTransportError.invalidChunkPayloadSize
        }
        guard packet.senderFingerprint == senderFingerprint else {
            throw MacBleTransportError.senderFingerprintMismatch
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let payload = try encoder.encode(packet)
        guard payload.count > 0 && payload.count <= maxPacketBytes else {
            throw MacBleTransportError.packetTooLarge
        }
        let transferId = "\(packet.packetId)-\(UUID().uuidString)"
        let crc32 = CRC32.checksum(payload)
        let chunkCount = (payload.count + chunkPayloadBytes - 1) / chunkPayloadBytes

        return try (0..<chunkCount).map { index in
            let start = index * chunkPayloadBytes
            let end = min(start + chunkPayloadBytes, payload.count)
            let chunk = BleFrameChunk(
                transferId: transferId,
                senderPeerId: senderPeerId,
                senderFingerprint: senderFingerprint,
                senderDisplayName: senderDisplayName,
                chunkIndex: index,
                chunkCount: chunkCount,
                payloadSize: payload.count,
                payloadCrc32: crc32,
                payloadBase64: payload[start..<end].base64EncodedString()
            )
            let data = try encoder.encode(chunk)
            guard data.count <= maxGattWriteBytes else {
                throw MacBleTransportError.packetTooLarge
            }
            return data
        }
    }

    public static func decodeChunk(_ data: Data) throws -> BleFrameChunk {
        do {
            let chunk = try JSONDecoder().decode(BleFrameChunk.self, from: data)
            return try validate(chunk)
        } catch let error as MacBleTransportError {
            throw error
        } catch {
            throw MacBleTransportError.malformedChunk
        }
    }

    private static func validate(_ chunk: BleFrameChunk) throws -> BleFrameChunk {
        guard chunk.frameVersion == 1 else {
            throw MacBleTransportError.unsupportedFrameVersion
        }
        guard !chunk.transferId.isEmpty else {
            throw MacBleTransportError.missingTransferId
        }
        guard !chunk.senderFingerprint.isEmpty else {
            throw MacBleTransportError.missingSenderFingerprint
        }
        guard (1...512).contains(chunk.chunkCount) else {
            throw MacBleTransportError.invalidChunkCount
        }
        guard (0..<chunk.chunkCount).contains(chunk.chunkIndex) else {
            throw MacBleTransportError.invalidChunkIndex
        }
        guard (1...maxPacketBytes).contains(chunk.payloadSize) else {
            throw MacBleTransportError.invalidPayloadSize
        }
        guard Data(base64Encoded: chunk.payloadBase64) != nil else {
            throw MacBleTransportError.malformedChunk
        }
        return chunk
    }
}

public final class BleFrameReassembler: @unchecked Sendable {
    private struct PendingTransfer {
        var firstSeenAtEpochMillis: Int64
        var senderPeerId: String
        var senderFingerprint: String
        var senderDisplayName: String?
        var chunkCount: Int
        var payloadSize: Int
        var payloadCrc32: UInt32
        var chunks: [Int: Data] = [:]

        func matches(_ chunk: BleFrameChunk) -> Bool {
            senderPeerId == chunk.senderPeerId &&
                senderFingerprint == chunk.senderFingerprint &&
                chunkCount == chunk.chunkCount &&
                payloadSize == chunk.payloadSize &&
                payloadCrc32 == chunk.payloadCrc32
        }
    }

    private let now: @Sendable () -> Int64
    private let transferTtlMillis: Int64
    private var pending: [String: PendingTransfer] = [:]

    public init(
        now: @escaping @Sendable () -> Int64 = { Int64(Date().timeIntervalSince1970 * 1000) },
        transferTtlMillis: Int64 = 60_000
    ) {
        self.now = now
        self.transferTtlMillis = transferTtlMillis
    }

    public func accept(_ chunk: BleFrameChunk) throws -> LanFrameEnvelope? {
        pruneExpired()
        var transfer = pending[chunk.transferId] ?? PendingTransfer(
            firstSeenAtEpochMillis: now(),
            senderPeerId: chunk.senderPeerId,
            senderFingerprint: chunk.senderFingerprint,
            senderDisplayName: chunk.senderDisplayName,
            chunkCount: chunk.chunkCount,
            payloadSize: chunk.payloadSize,
            payloadCrc32: chunk.payloadCrc32
        )
        guard transfer.matches(chunk) else {
            throw MacBleTransportError.transferMetadataMismatch
        }
        guard let chunkPayload = Data(base64Encoded: chunk.payloadBase64) else {
            throw MacBleTransportError.malformedChunk
        }
        transfer.chunks[chunk.chunkIndex] = chunkPayload
        if transfer.chunks.count != transfer.chunkCount {
            pending[chunk.transferId] = transfer
            return nil
        }
        pending.removeValue(forKey: chunk.transferId)

        var payload = Data()
        for index in 0..<transfer.chunkCount {
            guard let bytes = transfer.chunks[index] else {
                throw MacBleTransportError.missingChunk
            }
            payload.append(bytes)
        }
        guard payload.count == transfer.payloadSize else {
            throw MacBleTransportError.payloadSizeMismatch
        }
        guard CRC32.checksum(payload) == transfer.payloadCrc32 else {
            throw MacBleTransportError.payloadChecksumMismatch
        }
        let packet = try JSONDecoder().decode(KrakenPacket.self, from: payload)
        guard packet.senderFingerprint == transfer.senderFingerprint else {
            throw MacBleTransportError.senderFingerprintMismatch
        }
        return LanFrameEnvelope(
            senderPeerId: transfer.senderPeerId,
            senderFingerprint: transfer.senderFingerprint,
            senderDisplayName: transfer.senderDisplayName,
            packet: packet
        )
    }

    private func pruneExpired() {
        let current = now()
        pending = pending.filter { _, transfer in
            current - transfer.firstSeenAtEpochMillis <= transferTtlMillis
        }
    }
}

private enum CRC32 {
    private static let table: [UInt32] = (0..<256).map { value in
        var crc = UInt32(value)
        for _ in 0..<8 {
            if crc & 1 == 1 {
                crc = 0xEDB88320 ^ (crc >> 1)
            } else {
                crc >>= 1
            }
        }
        return crc
    }

    static func checksum(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data {
            let index = Int((crc ^ UInt32(byte)) & 0xFF)
            crc = table[index] ^ (crc >> 8)
        }
        return crc ^ 0xFFFFFFFF
    }
}
