package com.disser.kraken.realm

import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealmServiceTest {
    private val owner = LocalIdentity(
        identityId = "owner",
        displayName = "Owner",
        publicKeyEncoded = "placeholder-pub:T1dORVJPV05FUk9XTkVST1dORVJPV05FUk9XTkVS",
        privateKeyReference = "placeholder-private-ref:owner",
        fingerprint = FingerprintFormatter.shortFingerprint("placeholder-pub:T1dORVJPV05FUk9XTkVST1dORVJPV05FUk9XTkVS"),
        createdAtEpochMillis = 1_700_000_000_000,
    )

    @Test
    fun creatingDemoRealmProducesDefaultPolicyCapacityAndMembershipCertificate() {
        val creation = RealmService.createDemoRealm(owner, nowEpochMillis = 1_700_000_000_100)

        assertEquals("Kraken Demo", creation.realm.name)
        assertEquals(500, creation.realm.policy.maxMembers)
        assertEquals(500, creation.realm.capacityState.capacity)
        assertEquals(1, creation.realm.capacityState.memberCount)
        assertEquals(creation.realm.realmId, creation.membershipCertificate.realmId)
        assertEquals(owner.publicKeyEncoded, creation.membershipCertificate.memberPublicKey)
    }

    @Test
    fun realmLocalStateTransitionsPauseResumeArchiveAndLeave() {
        val realm = RealmService.createDemoRealm(owner).realm

        val paused = RealmService.pause(realm)
        val resumed = RealmService.resume(paused)
        val archived = RealmService.archive(resumed)
        val left = RealmService.leave(archived)

        assertEquals(LocalRealmState.PAUSED, paused.localState)
        assertEquals(LocalRealmState.ACTIVE, resumed.localState)
        assertEquals(LocalRealmState.ARCHIVED, archived.localState)
        assertEquals(LocalRealmState.LEFT, left.localState)
    }

    @Test
    fun capacityControlsInviteCreation() {
        assertTrue(RealmService.canCreateInvite(CapacityState(memberCount = 499, capacity = 500, epoch = 1)))
        assertFalse(RealmService.canCreateInvite(CapacityState(memberCount = 500, capacity = 500, epoch = 1)))
    }

    @Test
    fun membershipCertificateBelongsToCorrectRealmAndMember() {
        val creation = RealmService.createDemoRealm(owner)

        assertEquals(creation.realm.realmId, creation.membershipCertificate.realmId)
        assertEquals(owner.publicKeyEncoded, creation.membershipCertificate.memberPublicKey)
    }

    @Test
    fun inviteEdgeLinksInviterAndInviteeKeys() {
        val edge = InviteEdge(
            realmId = "realm-1",
            inviteId = "invite-1",
            inviterPublicKey = "inviter-key",
            inviteePublicKey = "invitee-key",
            membershipId = "membership-1",
            acceptedAtEpochMillis = 1_700_000_000_200,
        )

        assertEquals("inviter-key", edge.inviterPublicKey)
        assertEquals("invitee-key", edge.inviteePublicKey)
    }

    @Test
    fun approvalOutcomeAddsMembershipUpdatesCapacityAndInviteEdge() {
        val creation = RealmService.createDemoRealm(owner, nowEpochMillis = 1_700_000_000_000)
        val request = RealmService.createDemoPendingRequest(
            realm = creation.realm,
            inviterPublicKey = owner.publicKeyEncoded,
            nowEpochMillis = 1_700_000_000_100,
        )
        val outcome = ApprovalEvaluator.recordDecision(
            request = request,
            decision = ApprovalDecision(
                approverPublicKey = owner.publicKeyEncoded,
                approverRole = RealmRole.OWNER,
                decisionType = ApprovalDecisionType.APPROVE,
                decidedAtEpochMillis = 1_700_000_000_200,
            ),
            issuedByPublicKey = owner.publicKeyEncoded,
            nowEpochMillis = 1_700_000_000_300,
        )
        val snapshot = RealmSnapshot(
            realms = listOf(creation.realm),
            membershipCertificates = listOf(creation.membershipCertificate),
            inviteEdges = emptyList(),
            pendingRequests = listOf(request),
        )

        val updated = RealmService.applyApprovalOutcome(snapshot, outcome)

        assertEquals(PendingMembershipState.APPROVED, updated.pendingRequests.single().state)
        assertEquals(2, updated.membershipCertificates.size)
        assertEquals(2, updated.realms.single().capacityState.memberCount)
        assertEquals(request.inviteId, updated.inviteEdges.single().inviteId)
        assertEquals(request.inviteePublicKey, updated.inviteEdges.single().inviteePublicKey)
        assertEquals(outcome.membershipCertificate?.membershipId, updated.inviteEdges.single().membershipId)
    }

    @Test
    fun membershipConfirmationAddsJoinedRealmCertificateAndInviteEdge() {
        val certificate = MembershipCertificate(
            realmId = "realm-joined",
            membershipId = "membership-joined",
            memberPublicKey = "placeholder-pub:invitee",
            issuedByPublicKey = owner.publicKeyEncoded,
            issuedAtEpochMillis = 1_700_000_000_300,
            expiresAtEpochMillis = null,
            capabilities = listOf("send_direct", "relay_basic"),
        )

        val updated = RealmService.applyMembershipConfirmation(
            snapshot = RealmSnapshot(
                realms = emptyList(),
                membershipCertificates = emptyList(),
                inviteEdges = emptyList(),
                pendingRequests = emptyList(),
            ),
            realmName = "OctoLab",
            inviteId = "invite-realm-1",
            inviterPublicKey = owner.publicKeyEncoded,
            certificate = certificate,
        )

        assertEquals("OctoLab", updated.realms.single().name)
        assertEquals(LocalRealmState.ACTIVE, updated.realms.single().localState)
        assertEquals(certificate, updated.membershipCertificates.single())
        assertEquals("invite-realm-1", updated.inviteEdges.single().inviteId)
        assertEquals(certificate.membershipId, updated.inviteEdges.single().membershipId)
    }

    @Test
    fun pendingMembershipRequestCanBeCreatedFromRealmInviteResponseData() {
        val creation = RealmService.createDemoRealm(owner, nowEpochMillis = 1_700_000_000_000)

        val request = RealmService.createPendingMembershipRequest(
            realm = creation.realm,
            inviteId = "invite-realm-1",
            inviterPublicKey = owner.publicKeyEncoded,
            inviteePublicKey = "placeholder-pub:invitee",
            inviteeDisplayName = "Invitee",
            nowEpochMillis = 1_700_000_000_500,
        )

        assertEquals("invite-realm-1", request.inviteId)
        assertEquals(creation.realm.realmId, request.realmId)
        assertEquals("placeholder-pub:invitee", request.inviteePublicKey)
        assertEquals("Invitee", request.inviteeDisplayName)
        assertEquals(PendingMembershipState.PENDING_REVIEW, request.state)
    }

    @Test
    fun approvalPolicyValidationSupportsSingleAdminAndThreshold() {
        assertTrue(
            RealmService.validateApprovalPolicy(
                ApprovalPolicy(
                    mode = ApprovalMode.SINGLE_ADMIN,
                    requiredApprovals = 1,
                    eligibleRoles = listOf(RealmRole.ADMIN),
                )
            )
        )
        assertTrue(
            RealmService.validateApprovalPolicy(
                ApprovalPolicy(
                    mode = ApprovalMode.THRESHOLD,
                    requiredApprovals = 2,
                    eligibleRoles = listOf(RealmRole.ADMIN, RealmRole.MODERATOR),
                )
            )
        )
        assertFalse(
            RealmService.validateApprovalPolicy(
                ApprovalPolicy(
                    mode = ApprovalMode.THRESHOLD,
                    requiredApprovals = 1,
                    eligibleRoles = listOf(RealmRole.MEMBER),
                )
            )
        )
    }

    @Test
    fun ownerCanPromoteDemoteRestrictAndRestoreMemberCertificate() {
        val realm = RealmService.createDemoRealm(owner).realm
        val member = MembershipCertificate(
            realmId = realm.realmId,
            membershipId = "membership-member",
            memberPublicKey = "member-key",
            issuedByPublicKey = owner.publicKeyEncoded,
            issuedAtEpochMillis = 1,
            expiresAtEpochMillis = null,
            capabilities = listOf("send_direct", "relay_basic"),
        )

        val promoted = RealmService.promoteToAdmin(realm, RealmManagementRole.OWNER, member)
        assertTrue("admin" in promoted.capabilities)

        val demoted = RealmService.demoteToMember(realm, RealmManagementRole.OWNER, promoted)
        assertFalse("admin" in demoted.capabilities)

        val restricted = RealmService.restrictMember(realm, RealmManagementRole.OWNER, demoted)
        assertEquals(listOf("restricted"), restricted.capabilities)

        val restored = RealmService.restoreMember(realm, RealmManagementRole.OWNER, restricted)
        assertTrue("send_direct" in restored.capabilities)
        assertFalse("restricted" in restored.capabilities)
    }

    @Test
    fun realmModelsDoNotIntroducePublicDiscoveryFields() {
        val forbidden = setOf("public_discovery", "nearby_discovery", "global_search", "discovery")
        val realmFields = Realm::class.java.declaredFields.map { it.name.lowercase() }.toSet()
        val policyFields = RealmPolicy::class.java.declaredFields.map { it.name.lowercase() }.toSet()

        assertTrue(realmFields.intersect(forbidden).isEmpty())
        assertTrue(policyFields.intersect(forbidden).isEmpty())
    }
}
