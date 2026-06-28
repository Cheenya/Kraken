package com.disser.kraken.crypto

import com.disser.kraken.mesh.KrakenPacket
import com.disser.kraken.mesh.KrakenPacketType
import com.disser.kraken.mesh.PacketPayloadType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketCryptoTest {
    @Test
    fun localPacketCryptoHasDebugScopeNotice() {
        assertTrue(LocalPacketCrypto.ALGORITHM.contains("local-packet-check"))
        assertTrue(LocalPacketCrypto.WARNING.contains("debug-сборках"))
    }

    @Test
    fun localPacketCryptoIsBlockedForReleaseLikeBuildTypes() {
        assertTrue(LocalPacketCrypto.isAllowedForBuildType("debug"))
        assertFalse(LocalPacketCrypto.isAllowedForBuildType("release"))
        assertFalse(LocalPacketCrypto.isAllowedForBuildType("prod"))
        assertFalse(LocalPacketCrypto.isAllowedForBuildType("public"))
    }

    @Test
    fun localSignatureRoundTripUsesPacketProof() {
        val packet = packet()
        val signature = LocalPacketCrypto.sign(packet, PrivateKeyReference("placeholder-private-ref:alice"))

        assertTrue(LocalPacketCrypto.verify(packet, signature, PublicKeyMaterial("placeholder-pub:alice")))
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
