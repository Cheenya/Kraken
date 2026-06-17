package com.disser.kraken.realm

import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealmManagementModelsTest {
    private val localIdentity = LocalIdentity(
        identityId = "local",
        displayName = "Alice",
        publicKeyEncoded = "placeholder-pub:local",
        privateKeyReference = "placeholder-private-ref:local",
        fingerprint = FingerprintFormatter.shortFingerprint("placeholder-pub:local"),
        createdAtEpochMillis = 1_700_000_000_000,
    )

    @Test
    fun leftRealmDoesNotAppearInActiveGroup() {
        val activeRealm = realm("active", LocalRealmState.ACTIVE)
        val leftRealm = realm("left", LocalRealmState.LEFT)
        val archivedRealm = realm("archived", LocalRealmState.ARCHIVED)

        val groups = RealmLifecycleGrouper.group(
            realms = listOf(activeRealm, leftRealm, archivedRealm),
            pendingRequests = emptyList(),
        )

        assertEquals(listOf(activeRealm), groups.active)
        assertFalse(groups.active.contains(leftRealm))
        assertEquals(listOf(leftRealm, archivedRealm), groups.leftArchived)
    }

    @Test
    fun realmWithPendingReviewRequestMovesToPendingGroup() {
        val activeRealm = realm("active", LocalRealmState.ACTIVE)
        val pendingRealm = realm("pending", LocalRealmState.ACTIVE)

        val groups = RealmLifecycleGrouper.group(
            realms = listOf(activeRealm, pendingRealm),
            pendingRequests = listOf(pendingRequest(pendingRealm.realmId)),
        )

        assertEquals(listOf(activeRealm), groups.active)
        assertEquals(listOf(pendingRealm), groups.pendingReview)
    }

    @Test
    fun approvalRequestsAreGroupedIntoReviewAndHistory() {
        val pending = pendingRequest("realm-pending")
        val approved = pendingRequest("realm-approved").copy(state = PendingMembershipState.APPROVED)
        val rejected = pendingRequest("realm-rejected").copy(state = PendingMembershipState.REJECTED)

        val groups = ApprovalRequestGrouper.group(listOf(pending, approved, rejected))

        assertEquals(listOf(pending), groups.pendingReview)
        assertEquals(listOf(approved, rejected), groups.history)
    }

    @Test
    fun ownerAdminMemberActionsAreVisibleByRoleAndState() {
        val activeRealm = realm("realm", LocalRealmState.ACTIVE, createdBy = localIdentity.publicKeyEncoded)
        val ownerCertificate = certificate(activeRealm, listOf("admin"))
        val role = RealmManagementPolicy.roleFor(activeRealm, ownerCertificate, localIdentity)

        assertEquals(RealmManagementRole.OWNER, role)
        assertTrue(RealmManagementPolicy.canPauseOrResume(role, activeRealm))
        assertTrue(RealmManagementPolicy.canArchive(role, activeRealm))
        assertTrue(RealmManagementPolicy.canLeave(role, activeRealm))
        assertTrue(RealmManagementPolicy.canCreateApprovalRequest(role, activeRealm))
    }

    @Test
    fun adminCannotPromoteDemoteOrRemoveAnotherAdmin() {
        val activeRealm = realm("realm", LocalRealmState.ACTIVE)
        val admin = certificate(activeRealm, listOf("admin"), memberKey = "admin-key")

        assertFalse(RealmManagementPolicy.canPromoteMember(RealmManagementRole.ADMIN, activeRealm, admin))
        assertFalse(RealmManagementPolicy.canDemoteMember(RealmManagementRole.ADMIN, activeRealm, admin))
        assertFalse(RealmManagementPolicy.canRemoveMember(RealmManagementRole.ADMIN, activeRealm, admin))
    }

    @Test
    fun ownerCanPromoteMemberAndDemoteAdmin() {
        val activeRealm = realm("realm", LocalRealmState.ACTIVE)
        val member = certificate(activeRealm, listOf("send_direct"), memberKey = "member-key")
        val admin = certificate(activeRealm, listOf("send_direct", "admin"), memberKey = "admin-key")

        assertTrue(RealmManagementPolicy.canPromoteMember(RealmManagementRole.OWNER, activeRealm, member))
        assertTrue(RealmManagementPolicy.canDemoteMember(RealmManagementRole.OWNER, activeRealm, admin))
    }

    @Test
    fun ownerCannotRemoveOwnerCertificate() {
        val activeRealm = realm("realm", LocalRealmState.ACTIVE, createdBy = "owner-key")
        val owner = certificate(activeRealm, listOf("admin"), memberKey = "owner-key")

        assertFalse(RealmManagementPolicy.canRemoveMember(RealmManagementRole.OWNER, activeRealm, owner))
        assertFalse(RealmManagementPolicy.canRestrictMember(RealmManagementRole.OWNER, activeRealm, owner))
    }

    @Test
    fun deleteLocalRecordIsLimitedToLeftOrArchivedRealms() {
        assertFalse(RealmManagementPolicy.canDeleteLocalRecord(realm("active", LocalRealmState.ACTIVE)))
        assertTrue(RealmManagementPolicy.canDeleteLocalRecord(realm("left", LocalRealmState.LEFT)))
        assertTrue(RealmManagementPolicy.canDeleteLocalRecord(realm("archived", LocalRealmState.ARCHIVED)))
    }

    private fun realm(
        id: String,
        state: LocalRealmState,
        createdBy: String = "owner-key",
    ): Realm =
        Realm(
            realmId = "realm-$id",
            name = "Realm $id",
            description = null,
            createdByPublicKey = createdBy,
            createdAtEpochMillis = 1,
            policy = RealmPolicy(),
            capacityState = CapacityState(memberCount = 1, capacity = 10, epoch = 1),
            localState = state,
        )

    private fun certificate(
        realm: Realm,
        capabilities: List<String>,
        memberKey: String = localIdentity.publicKeyEncoded,
    ): MembershipCertificate =
        MembershipCertificate(
            realmId = realm.realmId,
            membershipId = "membership-${realm.realmId}-$memberKey",
            memberPublicKey = memberKey,
            issuedByPublicKey = realm.createdByPublicKey,
            issuedAtEpochMillis = 1,
            expiresAtEpochMillis = null,
            capabilities = capabilities,
        )

    private fun pendingRequest(realmId: String): PendingMembershipRequest =
        PendingMembershipRequest(
            requestId = "request-$realmId",
            realmId = realmId,
            inviteId = "invite-$realmId",
            inviterPublicKey = "inviter",
            inviteePublicKey = "invitee",
            inviteeDisplayName = "Pending",
            createdAtEpochMillis = 1,
            state = PendingMembershipState.PENDING_REVIEW,
        )
}
