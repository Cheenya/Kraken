package com.disser.kraken.realm

import com.disser.kraken.identity.LocalIdentity
import java.util.UUID

object RealmService {
    private val memberCapabilities = listOf("send_direct", "relay_basic")
    private val adminCapabilities = listOf(
        "send_direct",
        "relay_basic",
        "create_invite",
        "publish_channel",
        "moderate",
        "admin",
    )

    fun createRealm(
        owner: LocalIdentity,
        name: String,
        description: String? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): DemoRealmCreation {
        val safeName = name.trim().ifBlank { "Private Realm" }.take(80)
        val safeDescription = description?.trim()?.take(180)?.ifBlank { null }
        return createRealmInternal(
            owner = owner,
            name = safeName,
            description = safeDescription ?: "Локальный реалм по приглашению.",
            nowEpochMillis = nowEpochMillis,
        )
    }

    fun createDemoRealm(
        owner: LocalIdentity,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): DemoRealmCreation =
        createRealmInternal(
            owner = owner,
            name = "Демо диссертации",
            description = "Локальный демо-реалм по приглашению.",
            nowEpochMillis = nowEpochMillis,
        )

    private fun createRealmInternal(
        owner: LocalIdentity,
        name: String,
        description: String,
        nowEpochMillis: Long,
    ): DemoRealmCreation {
        val realmId = "realm-${UUID.randomUUID()}"
        val membershipId = "membership-${UUID.randomUUID()}"
        val policy = RealmPolicy()
        val realm = Realm(
            realmId = realmId,
            name = name,
            description = description,
            createdByPublicKey = owner.publicKeyEncoded,
            createdAtEpochMillis = nowEpochMillis,
            policy = policy,
            capacityState = CapacityState(
                memberCount = 1,
                capacity = policy.maxMembers,
                epoch = nowEpochMillis,
                signedStatement = null,
                capacityToken = null,
            ),
            localState = LocalRealmState.ACTIVE,
        )
        val certificate = MembershipCertificate(
            realmId = realmId,
            membershipId = membershipId,
            memberPublicKey = owner.publicKeyEncoded,
            issuedByPublicKey = owner.publicKeyEncoded,
            issuedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = null,
            capabilities = listOf(
                "send_direct",
                "relay_basic",
                "create_invite",
                "publish_channel",
                "moderate",
                "admin",
            ),
            signature = null,
        )

        return DemoRealmCreation(realm, certificate)
    }

    fun pause(realm: Realm): Realm =
        if (realm.localState == LocalRealmState.ACTIVE) realm.copy(localState = LocalRealmState.PAUSED) else realm

    fun resume(realm: Realm): Realm =
        if (realm.localState == LocalRealmState.PAUSED) realm.copy(localState = LocalRealmState.ACTIVE) else realm

    fun archive(realm: Realm): Realm =
        if (realm.localState != LocalRealmState.LEFT) realm.copy(localState = LocalRealmState.ARCHIVED) else realm

    fun leave(realm: Realm): Realm =
        realm.copy(localState = LocalRealmState.LEFT)

    fun canCreateInvite(capacityState: CapacityState): Boolean =
        capacityState.memberCount < capacityState.capacity

    fun promoteToAdmin(
        realm: Realm,
        actorRole: RealmManagementRole,
        certificate: MembershipCertificate,
    ): MembershipCertificate =
        if (RealmManagementPolicy.canPromoteMember(actorRole, realm, certificate)) {
            certificate.copy(capabilities = adminCapabilities)
        } else {
            certificate
        }

    fun demoteToMember(
        realm: Realm,
        actorRole: RealmManagementRole,
        certificate: MembershipCertificate,
    ): MembershipCertificate =
        if (RealmManagementPolicy.canDemoteMember(actorRole, realm, certificate)) {
            certificate.copy(capabilities = memberCapabilities)
        } else {
            certificate
        }

    fun restrictMember(
        realm: Realm,
        actorRole: RealmManagementRole,
        certificate: MembershipCertificate,
    ): MembershipCertificate =
        if (RealmManagementPolicy.canRestrictMember(actorRole, realm, certificate)) {
            certificate.copy(capabilities = listOf("restricted"))
        } else {
            certificate
        }

    fun restoreMember(
        realm: Realm,
        actorRole: RealmManagementRole,
        certificate: MembershipCertificate,
    ): MembershipCertificate =
        if (
            RealmManagementPolicy.canRestrictMember(actorRole, realm, certificate) &&
            RealmManagementPolicy.isRestricted(certificate)
        ) {
            certificate.copy(capabilities = memberCapabilities)
        } else {
            certificate
        }

    fun createDemoPendingRequest(
        realm: Realm,
        inviterPublicKey: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): PendingMembershipRequest =
        createPendingMembershipRequest(
            realm = realm,
            inviteId = "invite-${UUID.randomUUID()}",
            inviterPublicKey = inviterPublicKey,
            inviteePublicKey = "pending-member-${UUID.randomUUID()}",
            inviteeDisplayName = "Pending member",
            nowEpochMillis = nowEpochMillis,
        )

    fun createPendingMembershipRequest(
        realm: Realm,
        inviteId: String,
        inviterPublicKey: String,
        inviteePublicKey: String,
        inviteeDisplayName: String?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): PendingMembershipRequest =
        PendingMembershipRequest(
            requestId = "request-${stableToken(inviteId)}-${stableToken(inviteePublicKey)}",
            realmId = realm.realmId,
            inviteId = inviteId,
            inviterPublicKey = inviterPublicKey,
            inviteePublicKey = inviteePublicKey,
            inviteeDisplayName = inviteeDisplayName,
            createdAtEpochMillis = nowEpochMillis,
            state = PendingMembershipState.PENDING_REVIEW,
            approvalPolicy = ApprovalPolicy.singleAdmin(),
            decisions = emptyList(),
        )

    fun applyApprovalOutcome(
        snapshot: RealmSnapshot,
        outcome: ApprovalOutcome,
    ): RealmSnapshot {
        val updatedRequests = snapshot.pendingRequests
            .filterNot { it.requestId == outcome.request.requestId }
            .plus(outcome.request)

        val certificate = outcome.membershipCertificate
            ?: return snapshot.copy(pendingRequests = updatedRequests)

        val certificates = snapshot.membershipCertificates
            .filterNot { it.membershipId == certificate.membershipId }
            .plus(certificate)

        val inviteEdge = InviteEdge(
            realmId = outcome.request.realmId,
            inviteId = outcome.request.inviteId,
            inviterPublicKey = outcome.request.inviterPublicKey,
            inviteePublicKey = outcome.request.inviteePublicKey,
            membershipId = certificate.membershipId,
            acceptedAtEpochMillis = certificate.issuedAtEpochMillis,
        )
        val inviteEdges = snapshot.inviteEdges
            .filterNot {
                it.realmId == inviteEdge.realmId &&
                    it.inviteId == inviteEdge.inviteId &&
                    it.inviteePublicKey == inviteEdge.inviteePublicKey
            }
            .plus(inviteEdge)

        val realms = snapshot.realms.map { realm ->
            if (realm.realmId == certificate.realmId) {
                realm.copy(
                    capacityState = realm.capacityState.copy(
                        memberCount = certificates.count { it.realmId == realm.realmId },
                        epoch = certificate.issuedAtEpochMillis,
                    ),
                )
            } else {
                realm
            }
        }

        return RealmSnapshot(
            realms = realms,
            membershipCertificates = certificates,
            inviteEdges = inviteEdges,
            pendingRequests = updatedRequests,
        )
    }

    fun applyMembershipConfirmation(
        snapshot: RealmSnapshot,
        realmName: String?,
        inviteId: String,
        inviterPublicKey: String,
        certificate: MembershipCertificate,
    ): RealmSnapshot {
        val certificates = snapshot.membershipCertificates
            .filterNot {
                it.membershipId == certificate.membershipId ||
                    (it.realmId == certificate.realmId && it.memberPublicKey == certificate.memberPublicKey)
            }
            .plus(certificate)

        val existingRealm = snapshot.realms.firstOrNull { it.realmId == certificate.realmId }
        val updatedRealm = (existingRealm ?: createJoinedRealm(certificate, realmName)).copy(
            capacityState = (existingRealm?.capacityState ?: createJoinedCapacityState(certificate)).copy(
                memberCount = certificates.count { it.realmId == certificate.realmId }.coerceAtLeast(1),
                epoch = certificate.issuedAtEpochMillis,
            ),
            localState = LocalRealmState.ACTIVE,
        )
        val realms = snapshot.realms
            .filterNot { it.realmId == certificate.realmId }
            .plus(updatedRealm)

        val inviteEdge = InviteEdge(
            realmId = certificate.realmId,
            inviteId = inviteId,
            inviterPublicKey = inviterPublicKey,
            inviteePublicKey = certificate.memberPublicKey,
            membershipId = certificate.membershipId,
            acceptedAtEpochMillis = certificate.issuedAtEpochMillis,
        )
        val inviteEdges = snapshot.inviteEdges
            .filterNot {
                it.realmId == inviteEdge.realmId &&
                    it.inviteId == inviteEdge.inviteId &&
                    it.inviteePublicKey == inviteEdge.inviteePublicKey
            }
            .plus(inviteEdge)

        return snapshot.copy(
            realms = realms,
            membershipCertificates = certificates,
            inviteEdges = inviteEdges,
        )
    }

    fun validateApprovalPolicy(policy: ApprovalPolicy): Boolean =
        when (policy.mode) {
            ApprovalMode.SINGLE_ADMIN ->
                policy.requiredApprovals == 1 &&
                    policy.eligibleRoles.any { it == RealmRole.OWNER || it == RealmRole.ADMIN }
            ApprovalMode.THRESHOLD ->
                policy.requiredApprovals > 1 &&
                    policy.eligibleRoles.any { it == RealmRole.ADMIN || it == RealmRole.MODERATOR }
        }

    private fun stableToken(value: String): String =
        value.filter { it.isLetterOrDigit() }.take(12).ifBlank { "unknown" }

    private fun createJoinedRealm(
        certificate: MembershipCertificate,
        realmName: String?,
    ): Realm =
        Realm(
            realmId = certificate.realmId,
            name = realmName?.trim()?.take(80)?.ifBlank { null } ?: "Joined Realm",
            description = "Реалм, добавленный через QR подтверждения.",
            createdByPublicKey = certificate.issuedByPublicKey,
            createdAtEpochMillis = certificate.issuedAtEpochMillis,
            policy = RealmPolicy(),
            capacityState = createJoinedCapacityState(certificate),
            localState = LocalRealmState.ACTIVE,
        )

    private fun createJoinedCapacityState(certificate: MembershipCertificate): CapacityState =
        CapacityState(
            memberCount = 1,
            capacity = RealmPolicy().maxMembers,
            epoch = certificate.issuedAtEpochMillis,
            signedStatement = null,
            capacityToken = null,
        )
}
