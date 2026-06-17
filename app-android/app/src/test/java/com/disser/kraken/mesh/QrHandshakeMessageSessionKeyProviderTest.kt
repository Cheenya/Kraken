package com.disser.kraken.mesh

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

class QrHandshakeMessageSessionKeyProviderTest {
    private val alice = identity("alice", "Alice", "ALICE-FP")
    private val bob = identity("bob", "Bob", "BOB-FP")
    private val provider = QrHandshakeMessageSessionKeyProvider()

    @Test
    fun bothSidesDeriveSameKeyFromQrHandshakeRelationship() {
        val aliceRelationship = relationship(alice, bob, sourceInviteId = "invite-shared-secret")
        val bobRelationship = relationship(bob, alice, sourceInviteId = "invite-shared-secret")
        val alicePacket = packet(alice, bob, aliceRelationship)
        val bobViewOfPacket = alicePacket.copy(
            senderFingerprint = alice.fingerprint,
            recipientFingerprint = bob.fingerprint,
        )

        val aliceKey = provider.keyFor(alice, aliceRelationship, alicePacket)
        val bobKey = provider.keyFor(bob, bobRelationship, bobViewOfPacket)

        assertNotNull(aliceKey)
        assertNotNull(bobKey)
        assertArrayEquals(aliceKey!!.bytes, bobKey!!.bytes)
    }

    @Test
    fun differentInviteSecretProducesDifferentMessageKey() {
        val firstRelationship = relationship(alice, bob, sourceInviteId = "invite-1")
        val secondRelationship = relationship(alice, bob, sourceInviteId = "invite-2")
        val firstKey = provider.keyFor(alice, firstRelationship, packet(alice, bob, firstRelationship))
        val secondKey = provider.keyFor(alice, secondRelationship, packet(alice, bob, secondRelationship))

        assertNotNull(firstKey)
        assertNotNull(secondKey)
        assertFalse(firstKey!!.bytes.contentEquals(secondKey!!.bytes))
    }

    @Test
    fun missingInviteSecretCannotProduceRuntimeMessageKey() {
        val relationship = relationship(alice, bob, sourceInviteId = null)

        val key = provider.keyFor(alice, relationship, packet(alice, bob, relationship))

        assertNull(key)
    }

    private fun identity(id: String, name: String, fingerprint: String): LocalIdentity =
        LocalIdentity(
            identityId = id,
            displayName = name,
            publicKeyEncoded = "placeholder-pub:$id",
            privateKeyReference = "placeholder-private-ref:$id",
            fingerprint = fingerprint,
            createdAtEpochMillis = 1_700_000_000_000,
        )

    private fun relationship(
        local: LocalIdentity,
        peer: LocalIdentity,
        sourceInviteId: String?,
    ): Relationship =
        Relationship(
            relationshipId = "relationship-1",
            localIdentityPublicKey = local.publicKeyEncoded,
            peerPublicKey = peer.publicKeyEncoded,
            peerDisplayName = peer.displayName,
            peerFingerprint = peer.fingerprint,
            realmId = null,
            state = RelationshipState.ACTIVE,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
            sourceInviteId = sourceInviteId,
            offlineHandshakeRole = OfflineHandshakeRole.INVITER,
        )

    private fun packet(
        sender: LocalIdentity,
        recipient: LocalIdentity,
        relationship: Relationship,
    ): KrakenPacket =
        KrakenPacket(
            packetId = "packet-1",
            packetType = KrakenPacketType.MESSAGE,
            senderFingerprint = sender.fingerprint,
            recipientFingerprint = recipient.fingerprint,
            relationshipId = relationship.relationshipId,
            conversationId = "conversation-1",
            messageId = "message-1",
            createdAtEpochMillis = 1_700_000_000_000,
            expiresAtEpochMillis = 1_700_000_060_000,
            ttlHops = 4,
            payloadType = PacketPayloadType.ENCRYPTED_MESSAGE_JSON,
            payloadJson = "{}",
            sessionProfileId = "session-${relationship.relationshipId}-${relationship.cryptoProfileId ?: com.disser.kraken.crypto.KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID}",
        )
}
