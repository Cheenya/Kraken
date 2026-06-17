package com.disser.kraken.mesh

import com.disser.kraken.crypto.AdamovaCryptoPolicyContext
import com.disser.kraken.crypto.KrakenCryptoProfileDefaults
import com.disser.kraken.crypto.ProductCryptoAdmissionGate
import com.disser.kraken.relationship.Relationship

object AdamovaPacketCryptoBinding {
    fun contextFor(
        packet: KrakenPacket,
        relationship: Relationship,
        admissionGate: ProductCryptoAdmissionGate = ProductCryptoAdmissionGate(),
    ): Result<AdamovaCryptoPolicyContext> = runCatching {
        validatePacketAdmission(packet, relationship, admissionGate)?.let { reason ->
            throw IllegalArgumentException("Packet does not match Adamova crypto policy: $reason")
        }
        val profileId = relationship.cryptoProfileId ?: KrakenCryptoProfileDefaults.STANDARD_PROFILE_ID
        val admission = admissionGate.evaluate(profileId)
            ?: throw IllegalArgumentException("Unknown crypto profile: $profileId")
        val expectedSessionProfileId = "session-${relationship.relationshipId}-$profileId"
        require(packet.sessionProfileId == expectedSessionProfileId) {
            "Packet session profile does not match relationship crypto profile."
        }
        AdamovaCryptoPolicyContext(
            profileId = profileId,
            profileHash = relationship.cryptoProfileHash ?: admission.profileHash,
            admissionDecisionHash = relationship.admissionDecisionHash ?: admission.decisionHash,
            profilePolicyVersion = relationship.profilePolicyVersion ?: admission.policyVersion,
            nativeBackendVersion = relationship.nativeBackendVersion ?: admission.nativeBackendVersion,
            sessionProfileId = expectedSessionProfileId,
            relationshipId = relationship.relationshipId,
        )
    }
}
