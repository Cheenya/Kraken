package com.disser.kraken.demo

import com.disser.kraken.channel.ChannelService
import com.disser.kraken.channel.ChannelSnapshot
import com.disser.kraken.channel.ChannelStore
import com.disser.kraken.group.SmallGroupService
import com.disser.kraken.group.SmallGroupSnapshot
import com.disser.kraken.group.SmallGroupStore
import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.IdentityStore
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.identity.SecureRandomPlaceholderIdentityKeyProvider
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.invite.PendingInviteState
import com.disser.kraken.invite.PendingInviteStore
import com.disser.kraken.realm.RealmService
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.realm.RealmStore
import com.disser.kraken.relationship.ComplaintEvent
import com.disser.kraken.relationship.ComplaintStore
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import com.disser.kraken.relationship.RelationshipStore
import com.disser.kraken.relationship.UnlinkReason

data class DemoSeedResult(
    val identity: LocalIdentity,
    val pendingInvites: List<PendingInviteImport>,
    val relationships: List<Relationship>,
    val complaints: List<ComplaintEvent>,
    val realmSnapshot: RealmSnapshot,
    val channelSnapshot: ChannelSnapshot,
    val smallGroupSnapshot: SmallGroupSnapshot,
)

data class DemoResetResult(
    val pendingInvites: List<PendingInviteImport>,
    val relationships: List<Relationship>,
    val complaints: List<ComplaintEvent>,
    val realmSnapshot: RealmSnapshot,
    val channelSnapshot: ChannelSnapshot,
    val smallGroupSnapshot: SmallGroupSnapshot,
)

object DemoDataSeeder {
    fun seed(
        identityStore: IdentityStore,
        pendingInviteStore: PendingInviteStore,
        relationshipStore: RelationshipStore,
        complaintStore: ComplaintStore,
        realmStore: RealmStore,
        channelStore: ChannelStore,
        smallGroupStore: SmallGroupStore,
        currentIdentity: LocalIdentity?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): DemoSeedResult {
        val identity = currentIdentity ?: identityStore.create(
            displayName = "Demo Reviewer",
            keyProvider = SecureRandomPlaceholderIdentityKeyProvider(),
        )
        val pendingInvite = PendingInviteImport(
            localId = "demo-pending-import",
            inviteId = "demo-invite-pending",
            inviterDisplayName = "Алиса (пример)",
            inviterPublicKeyEncoded = "demo-pending-peer-public-key",
            inviterFingerprint = FingerprintFormatter.shortFingerprint("demo-pending-peer-public-key"),
            importedAtEpochMillis = nowEpochMillis,
            state = PendingInviteState.PENDING_IMPORT,
        )
        val pendingInvites = (pendingInviteStore.load().filterNot { it.localId == pendingInvite.localId } + pendingInvite)
        pendingInviteStore.save(pendingInvites)

        val relationships = createRelationshipSamples(identity, nowEpochMillis, pendingInvite.inviteId)
        val mergedRelationships = relationshipStore.load()
            .filterNot { it.relationshipId in relationships.map(Relationship::relationshipId) }
            .plus(relationships)
        relationshipStore.save(mergedRelationships)

        val realmSnapshot = if (realmStore.loadRealms().isEmpty()) {
            realmStore.addDemoRealm(RealmService.createDemoRealm(identity, nowEpochMillis))
        } else {
            realmStore.snapshot()
        }
        val realm = realmSnapshot.realms.first()
        val withPendingRequest = if (realmSnapshot.pendingRequests.isEmpty()) {
            realmStore.addPendingRequest(
                RealmService.createDemoPendingRequest(
                    realm = realm,
                    inviterPublicKey = identity.publicKeyEncoded,
                    nowEpochMillis = nowEpochMillis,
                )
            )
        } else {
            realmStore.snapshot()
        }
        val channelSnapshot = if (channelStore.snapshot().channels.isEmpty()) {
            channelStore.addDemoChannel(ChannelService.createDemoChannel(realm, identity))
        } else {
            channelStore.snapshot()
        }
        val smallGroupSnapshot = if (smallGroupStore.snapshot().groups.isEmpty()) {
            smallGroupStore.addDemoGroup(SmallGroupService.createDemoGroup(realm, identity))
        } else {
            smallGroupStore.snapshot()
        }
        val complaints = if (complaintStore.load().isEmpty()) {
            complaintStore.add(
                ComplaintEvent(
                    complaintId = "demo-complaint-spam",
                    realmId = realm.realmId,
                    targetPublicKey = "demo-active-peer-public-key",
                    sourceRelationshipId = "demo-relationship-active",
                    reason = UnlinkReason.SPAM,
                    createdAtEpochMillis = nowEpochMillis,
                )
            )
        } else {
            complaintStore.load()
        }

        return DemoSeedResult(
            identity = identity,
            pendingInvites = pendingInvites,
            relationships = mergedRelationships,
            complaints = complaints,
            realmSnapshot = withPendingRequest,
            channelSnapshot = channelSnapshot,
            smallGroupSnapshot = smallGroupSnapshot,
        )
    }

    fun reset(
        pendingInviteStore: PendingInviteStore,
        relationshipStore: RelationshipStore,
        complaintStore: ComplaintStore,
        realmStore: RealmStore,
        channelStore: ChannelStore,
        smallGroupStore: SmallGroupStore,
    ): DemoResetResult {
        val pendingInvites = pendingInviteStore.load()
            .filterNot { it.localId.startsWith(DEMO_PREFIX) || it.inviteId.startsWith(DEMO_PREFIX) }
        pendingInviteStore.save(pendingInvites)

        val relationships = relationshipStore.load()
            .filterNot { it.relationshipId.startsWith(DEMO_PREFIX) || it.sourceInviteId?.startsWith(DEMO_PREFIX) == true }
        relationshipStore.save(relationships)

        val complaints = complaintStore.load()
            .filterNot { it.complaintId.startsWith(DEMO_PREFIX) }
        complaintStore.save(complaints)

        val currentRealmSnapshot = realmStore.snapshot()
        val demoRealmIds = currentRealmSnapshot.realms
            .filter { it.name == DEMO_REALM_NAME || it.description == DEMO_REALM_DESCRIPTION }
            .map { it.realmId }
            .toSet()
        val realmSnapshot = realmStore.saveSnapshot(
            currentRealmSnapshot.copy(
                realms = currentRealmSnapshot.realms.filterNot { it.realmId in demoRealmIds },
                membershipCertificates = currentRealmSnapshot.membershipCertificates.filterNot { it.realmId in demoRealmIds },
                inviteEdges = currentRealmSnapshot.inviteEdges.filterNot { it.realmId in demoRealmIds },
                pendingRequests = currentRealmSnapshot.pendingRequests.filterNot { it.realmId in demoRealmIds },
            )
        )

        val currentChannelSnapshot = channelStore.snapshot()
        val demoChannelIds = currentChannelSnapshot.channels
            .filter { it.realmId in demoRealmIds || it.description == DEMO_CHANNEL_DESCRIPTION }
            .map { it.channelId }
            .toSet()
        val channelSnapshot = channelStore.saveSnapshot(
            currentChannelSnapshot.copy(
                channels = currentChannelSnapshot.channels.filterNot { it.channelId in demoChannelIds },
                memberships = currentChannelSnapshot.memberships.filterNot { it.channelId in demoChannelIds },
                messages = currentChannelSnapshot.messages.filterNot { it.channelId in demoChannelIds },
            )
        )

        val currentSmallGroupSnapshot = smallGroupStore.snapshot()
        val demoGroupIds = currentSmallGroupSnapshot.groups
            .filter { it.realmId in demoRealmIds || it.description == DEMO_GROUP_DESCRIPTION }
            .map { it.groupId }
            .toSet()
        val smallGroupSnapshot = smallGroupStore.saveSnapshot(
            currentSmallGroupSnapshot.copy(
                groups = currentSmallGroupSnapshot.groups.filterNot { it.groupId in demoGroupIds },
                members = currentSmallGroupSnapshot.members.filterNot { it.groupId in demoGroupIds },
                messages = currentSmallGroupSnapshot.messages.filterNot { it.groupId in demoGroupIds },
            )
        )

        return DemoResetResult(
            pendingInvites = pendingInvites,
            relationships = relationships,
            complaints = complaints,
            realmSnapshot = realmSnapshot,
            channelSnapshot = channelSnapshot,
            smallGroupSnapshot = smallGroupSnapshot,
        )
    }

    fun createRelationshipSamples(
        identity: LocalIdentity,
        nowEpochMillis: Long,
        pendingInviteId: String = "demo-invite-pending",
    ): List<Relationship> =
        listOf(
            demoRelationship(
                id = "demo-relationship-pending",
                localPublicKey = identity.publicKeyEncoded,
                peerPublicKey = "demo-pending-peer-public-key",
                peerName = "Алиса (пример)",
                state = RelationshipState.PENDING_IMPORT,
                sourceInviteId = pendingInviteId,
                nowEpochMillis = nowEpochMillis,
            ),
            demoRelationship(
                id = "demo-relationship-active",
                localPublicKey = identity.publicKeyEncoded,
                peerPublicKey = "demo-active-peer-public-key",
                peerName = "Борис (пример)",
                state = RelationshipState.ACTIVE,
                sourceInviteId = "demo-invite-active-after-handshake",
                nowEpochMillis = nowEpochMillis,
            ),
            demoRelationship(
                id = "demo-relationship-blocked",
                localPublicKey = identity.publicKeyEncoded,
                peerPublicKey = "demo-blocked-peer-public-key",
                peerName = "Карина (пример)",
                state = RelationshipState.BLOCKED_BY_PEER,
                sourceInviteId = "demo-invite-blocked-by-peer",
                nowEpochMillis = nowEpochMillis,
            ),
        )

    fun isDemoRelationship(relationship: Relationship): Boolean =
        relationship.relationshipId.startsWith(DEMO_PREFIX) ||
            relationship.sourceInviteId?.startsWith(DEMO_PREFIX) == true ||
            relationship.peerDisplayName?.startsWith(DEMO_LABEL) == true

    private fun demoRelationship(
        id: String,
        localPublicKey: String,
        peerPublicKey: String,
        peerName: String,
        state: RelationshipState,
        sourceInviteId: String,
        nowEpochMillis: Long,
    ): Relationship =
        Relationship(
            relationshipId = id,
            localIdentityPublicKey = localPublicKey,
            peerPublicKey = peerPublicKey,
            peerDisplayName = peerName,
            peerFingerprint = FingerprintFormatter.shortFingerprint(peerPublicKey),
            realmId = null,
            state = state,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            sourceInviteId = sourceInviteId,
        )

    private const val DEMO_PREFIX = "demo-"
    private const val DEMO_LABEL = "пример"
    private const val DEMO_REALM_NAME = "Kraken Demo"
    private const val DEMO_REALM_DESCRIPTION = "Local invite-only demo realm."
    private const val DEMO_CHANNEL_DESCRIPTION = "Local demo channel inside an invite-only realm."
    private const val DEMO_GROUP_DESCRIPTION = "Strictly limited local group inside an invite-only realm."
}
