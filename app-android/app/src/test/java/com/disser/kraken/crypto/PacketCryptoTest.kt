package com.disser.kraken.crypto

import com.disser.kraken.mesh.KrakenPacket
import com.disser.kraken.mesh.KrakenPacketType
import com.disser.kraken.mesh.PacketPayloadType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketCryptoTest {
    @Test
    fun debugPacketCryptoIsVisiblyCompatibilityPath() {
        assertTrue(DebugPlaintextPacketCrypto.ALGORITHM.contains("debug-plaintext"))
        assertTrue(DebugPlaintextPacketCrypto.WARNING.contains("compatibility path"))
    }

    @Test
    fun debugPacketCryptoIsBlockedForReleaseLikeBuildTypes() {
        assertTrue(DebugPlaintextPacketCrypto.isAllowedForBuildType("debug"))
        assertFalse(DebugPlaintextPacketCrypto.isAllowedForBuildType("release"))
        assertFalse(DebugPlaintextPacketCrypto.isAllowedForBuildType("prod"))
        assertFalse(DebugPlaintextPacketCrypto.isAllowedForBuildType("production"))
    }

    @Test
    fun debugSignatureRoundTripIsOnlyPlaceholderProof() {
        val packet = packet()
        val signature = DebugPlaintextPacketCrypto.sign(packet, PrivateKeyReference("placeholder-private-ref:alice"))

        assertTrue(DebugPlaintextPacketCrypto.verify(packet, signature, PublicKeyMaterial("placeholder-pub:alice")))
        assertTrue(signature.value.startsWith("unsigned:"))
    }

    private fun packet(): KrakenPacket =
        KrakenPacket(
            packetId = "packet-crypto",
            packetType = KrakenPacketType.MESSAGE,
            senderFingerprint = "ALICE-FP",
            recipientFingerprint = "BOB-FP",
            relationshipId = "relationship-1",
            conversationId = "conversation-1",
            messageId = "message-1",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            ttlHops = 4,
            payloadType = PacketPayloadType.LOCAL_MESSAGE_JSON,
            payloadJson = """{"message_id":"message-1","body":"hello"}""",
        )
}
