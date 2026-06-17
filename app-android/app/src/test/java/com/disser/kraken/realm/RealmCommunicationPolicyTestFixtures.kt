package com.disser.kraken.realm

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState

object RealmCommunicationPolicyTestFixtures {
    fun relationship(
        localIdentity: LocalIdentity,
        peerIdentity: LocalIdentity,
        realmId: String?,
    ): Relationship =
        Relationship(
            relationshipId = "relationship-${localIdentity.identityId}-${peerIdentity.identityId}",
            localIdentityPublicKey = localIdentity.publicKeyEncoded,
            peerPublicKey = peerIdentity.publicKeyEncoded,
            peerDisplayName = peerIdentity.displayName,
            peerFingerprint = peerIdentity.fingerprint,
            realmId = realmId,
            state = RelationshipState.ACTIVE,
            createdAtEpochMillis = 1_700_000_000_000,
            updatedAtEpochMillis = 1_700_000_000_000,
            sourceInviteId = "invite-${localIdentity.identityId}-${peerIdentity.identityId}",
            offlineHandshakeRole = OfflineHandshakeRole.INVITER,
        )
}
