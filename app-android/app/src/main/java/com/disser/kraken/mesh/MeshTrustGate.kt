package com.disser.kraken.mesh

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.handshake.HandshakePayloadCodec
import com.disser.kraken.realm.RealmCommunicationPolicy
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState

object MeshTrustGate {
    fun validateOutbound(
        localIdentity: LocalIdentity,
        relationship: Relationship,
        packet: KrakenPacket,
        realmSnapshot: RealmSnapshot? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): PacketValidationResult {
        val base = PacketValidator.validateForStorage(packet, nowEpochMillis)
        if (!base.accepted) return base
        if (relationship.localIdentityPublicKey != localIdentity.publicKeyEncoded) {
            return PacketValidationResult(false, MeshRejectionReason.UNKNOWN_PEER)
        }
        if (
            !RelationshipService.canSendMessage(relationship) &&
            packet.packetType !in setOf(KrakenPacketType.HANDSHAKE_RESPONSE, KrakenPacketType.HANDSHAKE_CONFIRMATION)
        ) {
            return PacketValidationResult(false, MeshRejectionReason.PENDING_RELATIONSHIP)
        }
        if (packet.senderFingerprint != localIdentity.fingerprint || packet.recipientFingerprint != relationship.peerFingerprint) {
            return PacketValidationResult(false, MeshRejectionReason.WRONG_RECIPIENT)
        }
        if (packet.packetType == KrakenPacketType.HANDSHAKE_RESPONSE) {
            if (relationship.state !in setOf(RelationshipState.PENDING_IMPORT, RelationshipState.PENDING_HANDSHAKE)) {
                return PacketValidationResult(false, MeshRejectionReason.PENDING_RELATIONSHIP)
            }
            return PacketValidationResult(true)
        }
        if (packet.packetType == KrakenPacketType.HANDSHAKE_CONFIRMATION) {
            if (relationship.state != RelationshipState.ACTIVE) {
                return PacketValidationResult(false, MeshRejectionReason.PENDING_RELATIONSHIP)
            }
            return PacketValidationResult(true)
        }
        val realmDecision = RealmCommunicationPolicy.canUseRelationship(
            localIdentity = localIdentity,
            relationship = relationship,
            realmSnapshot = realmSnapshot,
            nowEpochMillis = nowEpochMillis,
        )
        if (!realmDecision.allowed) {
            return PacketValidationResult(false, MeshRejectionReason.REALM_MEMBERSHIP_BLOCKED)
        }
        return PacketValidationResult(true)
    }

    fun validateInbound(
        localIdentity: LocalIdentity,
        relationships: List<Relationship>,
        packet: KrakenPacket,
        alreadySeen: Boolean = false,
        realmSnapshot: RealmSnapshot? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Pair<PacketValidationResult, Relationship?> {
        val base = PacketValidator.validateForStorage(packet, nowEpochMillis, alreadySeen)
        if (!base.accepted) return base to null
        if (packet.recipientFingerprint != localIdentity.fingerprint) {
            return PacketValidationResult(false, MeshRejectionReason.WRONG_RECIPIENT) to null
        }
        val relationship = relationships.firstOrNull {
            it.relationshipId == packet.relationshipId &&
                it.peerFingerprint == packet.senderFingerprint &&
                it.localIdentityPublicKey == localIdentity.publicKeyEncoded
        } ?: return PacketValidationResult(false, MeshRejectionReason.UNKNOWN_PEER) to null

        if (relationship.state == RelationshipState.BLOCKED_BY_PEER || relationship.state == RelationshipState.UNLINKED) {
            return PacketValidationResult(false, MeshRejectionReason.BLOCKED_OR_UNLINKED) to relationship
        }
        if (!RelationshipService.canSendMessage(relationship)) {
            if (
                packet.packetType == KrakenPacketType.HANDSHAKE_CONFIRMATION &&
                relationship.state == RelationshipState.PENDING_HANDSHAKE &&
                pendingHandshakeConfirmationMatches(relationship, packet)
            ) {
                return PacketValidationResult(true) to relationship
            }
            return PacketValidationResult(false, MeshRejectionReason.PENDING_RELATIONSHIP) to relationship
        }
        val realmDecision = RealmCommunicationPolicy.canUseRelationship(
            localIdentity = localIdentity,
            relationship = relationship,
            realmSnapshot = realmSnapshot,
            nowEpochMillis = nowEpochMillis,
        )
        if (!realmDecision.allowed) {
            return PacketValidationResult(false, MeshRejectionReason.REALM_MEMBERSHIP_BLOCKED) to relationship
        }
        return PacketValidationResult(true) to relationship
    }

    private fun pendingHandshakeConfirmationMatches(relationship: Relationship, packet: KrakenPacket): Boolean {
        if (packet.payloadType != PacketPayloadType.HANDSHAKE_CONFIRMATION_JSON) return false
        val payload = HandshakePayloadCodec.decodeConfirmation(packet.payloadJson).getOrNull() ?: return false
        return relationship.sourceInviteId == payload.inviteId &&
            relationship.peerFingerprint == payload.inviterFingerprint &&
            packet.senderFingerprint == payload.inviterFingerprint &&
            packet.recipientFingerprint == payload.responderFingerprint
    }
}
