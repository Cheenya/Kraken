package com.disser.kraken.mesh

object PacketValidator {
    fun validateForStorage(
        packet: KrakenPacket,
        nowEpochMillis: Long = System.currentTimeMillis(),
        alreadySeen: Boolean = false,
        requireForwardableTtl: Boolean = false,
    ): PacketValidationResult {
        if (alreadySeen) {
            return PacketValidationResult(false, MeshRejectionReason.DUPLICATE)
        }
        if (packet.expiresAtEpochMillis <= nowEpochMillis) {
            return PacketValidationResult(false, MeshRejectionReason.EXPIRED)
        }
        if (requireForwardableTtl && packet.ttlHops <= 0) {
            return PacketValidationResult(false, MeshRejectionReason.TTL_EXHAUSTED)
        }
        if (packet.senderFingerprint.isBlank() || packet.recipientFingerprint.isBlank()) {
            return PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }
        if (packet.relationshipId.isBlank() || packet.conversationId.isBlank()) {
            return PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }
        if (packet.cryptoProfileId.isNullOrBlank() ||
            packet.admissionDecisionHash.isNullOrBlank() ||
            packet.profilePolicyVersion == null
        ) {
            return PacketValidationResult(false, MeshRejectionReason.UNKNOWN_CRYPTO_PROFILE)
        }
        if (packet.proofMode != KrakenPacket.LOCAL_PROOF_MODE) {
            return PacketValidationResult(false, MeshRejectionReason.MALFORMED)
        }
        return PacketValidationResult(true)
    }
}
