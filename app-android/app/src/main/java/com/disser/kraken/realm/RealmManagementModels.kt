package com.disser.kraken.realm

import com.disser.kraken.identity.LocalIdentity

enum class RealmManagementRole {
    OWNER,
    ADMIN,
    MEMBER,
    OBSERVER,
}

data class RealmLifecycleGroups(
    val active: List<Realm>,
    val pendingReview: List<Realm>,
    val leftArchived: List<Realm>,
)

data class ApprovalRequestGroups(
    val pendingReview: List<PendingMembershipRequest>,
    val history: List<PendingMembershipRequest>,
)

object RealmLifecycleGrouper {
    fun group(
        realms: List<Realm>,
        pendingRequests: List<PendingMembershipRequest>,
    ): RealmLifecycleGroups {
        val terminalStates = setOf(LocalRealmState.LEFT, LocalRealmState.ARCHIVED)
        val pendingRealmIds = pendingRequests
            .filter { it.state == PendingMembershipState.PENDING_REVIEW }
            .map { it.realmId }
            .toSet()
        val leftArchived = realms.filter { it.localState in terminalStates }
        val nonTerminal = realms.filterNot { it.localState in terminalStates }
        val pendingReview = nonTerminal.filter { it.realmId in pendingRealmIds }
        val active = nonTerminal.filterNot { it.realmId in pendingRealmIds }

        return RealmLifecycleGroups(
            active = active,
            pendingReview = pendingReview,
            leftArchived = leftArchived,
        )
    }
}

object ApprovalRequestGrouper {
    fun group(requests: List<PendingMembershipRequest>): ApprovalRequestGroups =
        ApprovalRequestGroups(
            pendingReview = requests.filter { it.state == PendingMembershipState.PENDING_REVIEW },
            history = requests.filterNot { it.state == PendingMembershipState.PENDING_REVIEW },
        )
}

object RealmManagementPolicy {
    fun roleFor(
        realm: Realm,
        certificate: MembershipCertificate?,
        localIdentity: LocalIdentity?,
    ): RealmManagementRole {
        if (localIdentity == null || certificate == null) return RealmManagementRole.OBSERVER
        if (certificate.realmId != realm.realmId || certificate.memberPublicKey != localIdentity.publicKeyEncoded) {
            return RealmManagementRole.OBSERVER
        }
        if (realm.createdByPublicKey == localIdentity.publicKeyEncoded) return RealmManagementRole.OWNER
        if ("admin" in certificate.capabilities) return RealmManagementRole.ADMIN
        return RealmManagementRole.MEMBER
    }

    fun memberRoleFor(realm: Realm, certificate: MembershipCertificate): RealmManagementRole =
        when {
            certificate.memberPublicKey == realm.createdByPublicKey -> RealmManagementRole.OWNER
            "admin" in certificate.capabilities -> RealmManagementRole.ADMIN
            else -> RealmManagementRole.MEMBER
        }

    fun isRestricted(certificate: MembershipCertificate): Boolean =
        "restricted" in certificate.capabilities

    fun approvalRoleFor(managementRole: RealmManagementRole): RealmRole? =
        when (managementRole) {
            RealmManagementRole.OWNER -> RealmRole.OWNER
            RealmManagementRole.ADMIN -> RealmRole.ADMIN
            RealmManagementRole.MEMBER,
            RealmManagementRole.OBSERVER -> null
        }

    fun canPauseOrResume(role: RealmManagementRole, realm: Realm): Boolean =
        role in setOf(RealmManagementRole.OWNER, RealmManagementRole.ADMIN) &&
            realm.localState in setOf(LocalRealmState.ACTIVE, LocalRealmState.PAUSED)

    fun canArchive(role: RealmManagementRole, realm: Realm): Boolean =
        role in setOf(RealmManagementRole.OWNER, RealmManagementRole.ADMIN) &&
            realm.localState !in setOf(LocalRealmState.ARCHIVED, LocalRealmState.LEFT)

    fun canCreateApprovalRequest(role: RealmManagementRole, realm: Realm): Boolean =
        role in setOf(RealmManagementRole.OWNER, RealmManagementRole.ADMIN) &&
            realm.localState !in setOf(LocalRealmState.ARCHIVED, LocalRealmState.LEFT)

    fun canLeave(role: RealmManagementRole, realm: Realm): Boolean =
        role != RealmManagementRole.OBSERVER && realm.localState != LocalRealmState.LEFT

    fun canDeleteLocalRecord(realm: Realm): Boolean =
        realm.localState in setOf(LocalRealmState.LEFT, LocalRealmState.ARCHIVED)

    fun canPromoteMember(
        actorRole: RealmManagementRole,
        realm: Realm,
        target: MembershipCertificate,
    ): Boolean =
        actorRole == RealmManagementRole.OWNER &&
            realm.localState !in setOf(LocalRealmState.ARCHIVED, LocalRealmState.LEFT) &&
            memberRoleFor(realm, target) == RealmManagementRole.MEMBER

    fun canDemoteMember(
        actorRole: RealmManagementRole,
        realm: Realm,
        target: MembershipCertificate,
    ): Boolean =
        actorRole == RealmManagementRole.OWNER &&
            realm.localState !in setOf(LocalRealmState.ARCHIVED, LocalRealmState.LEFT) &&
            memberRoleFor(realm, target) == RealmManagementRole.ADMIN

    fun canRestrictMember(
        actorRole: RealmManagementRole,
        realm: Realm,
        target: MembershipCertificate,
    ): Boolean {
        val targetRole = memberRoleFor(realm, target)
        return actorRole in setOf(RealmManagementRole.OWNER, RealmManagementRole.ADMIN) &&
            realm.localState !in setOf(LocalRealmState.ARCHIVED, LocalRealmState.LEFT) &&
            targetRole != RealmManagementRole.OWNER &&
            !(actorRole == RealmManagementRole.ADMIN && targetRole == RealmManagementRole.ADMIN)
    }

    fun canRemoveMember(
        actorRole: RealmManagementRole,
        realm: Realm,
        target: MembershipCertificate,
    ): Boolean =
        canRestrictMember(actorRole, realm, target)
}
