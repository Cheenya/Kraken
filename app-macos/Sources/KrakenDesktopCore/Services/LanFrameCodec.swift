import Foundation

public enum LanFrameCodec {
    public static let maxFrameBytes = 256 * 1024
    public static let ackByte: UInt8 = 0x06
    private static let lengthPrefixBytes = 4

    public static func encodeEnvelope(_ envelope: LanFrameEnvelope) throws -> Data {
        guard envelope.senderFingerprint == envelope.packet.senderFingerprint else {
            throw MacLanTransportError.senderFingerprintMismatch
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let payload = try encoder.encode(envelope)
        guard payload.count <= maxFrameBytes else {
            throw MacLanTransportError.frameTooLarge
        }
        var length = UInt32(payload.count).bigEndian
        var frame = Data(bytes: &length, count: lengthPrefixBytes)
        frame.append(payload)
        return frame
    }

    public static func decodeEnvelope(framePayload: Data) throws -> LanFrameEnvelope {
        let decoder = JSONDecoder()
        do {
            let envelope = try decoder.decode(LanFrameEnvelope.self, from: framePayload)
            guard envelope.senderFingerprint == envelope.packet.senderFingerprint else {
                throw MacLanTransportError.senderFingerprintMismatch
            }
            return envelope
        } catch let error as MacLanTransportError {
            throw error
        } catch {
            do {
                let packet = try decoder.decode(KrakenPacket.self, from: framePayload)
                return LanFrameEnvelope(
                    senderPeerId: "lan-\(packet.senderFingerprint)",
                    senderFingerprint: packet.senderFingerprint,
                    packet: packet
                )
            } catch {
                throw MacLanTransportError.malformedFrame
            }
        }
    }

    public static func decodeFrame(_ data: Data) throws -> LanFrameEnvelope {
        guard data.count >= lengthPrefixBytes else {
            throw MacLanTransportError.invalidFrameLength
        }
        let length = data.prefix(lengthPrefixBytes).reduce(UInt32(0)) { partial, byte in
            (partial << 8) | UInt32(byte)
        }
        guard length > 0 && length <= maxFrameBytes else {
            throw MacLanTransportError.invalidFrameLength
        }
        let payloadStart = data.index(data.startIndex, offsetBy: lengthPrefixBytes)
        let payloadEnd = data.index(payloadStart, offsetBy: Int(length), limitedBy: data.endIndex)
        guard payloadEnd == data.endIndex else {
            throw MacLanTransportError.invalidFrameLength
        }
        return try decodeEnvelope(framePayload: data[payloadStart..<data.endIndex])
    }
}
