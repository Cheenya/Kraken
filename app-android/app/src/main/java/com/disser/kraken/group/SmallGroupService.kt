package com.disser.kraken.group

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.PendingMembershipRequest
import com.disser.kraken.realm.PendingMembershipState
import com.disser.kraken.realm.Realm
import java.util.UUID

object SmallGroupService {
    fun createDemoGroup(
        realm: Realm,
        owner: LocalIdentity,
    ): DemoSmallGroupCreation {
        val group = SmallGroup(
            groupId = "small-group-${UUID.randomUUID()}",
            realmId = realm.realmId,
            name = "${realm.name}: малая группа",
            description = "Закрытая локальная группа внутри реалма по приглашению.",
            policy = SmallGroupPolicy(maxMembers = realm.policy.smallGroupMaxMembers),
        )
        return DemoSmallGroupCreation(
            group = group,
            ownerMembership = SmallGroupMember(
                groupId = group.groupId,
                memberPublicKey = owner.publicKeyEncoded,
                role = SmallGroupRole.OWNER,
                state = SmallGroupMemberState.ACTIVE,
            ),
        )
    }

    fun canAddMember(
        group: SmallGroup,
        members: List<SmallGroupMember>,
    ): Boolean =
        members.count { it.groupId == group.groupId && it.state != SmallGroupMemberState.LEFT } < group.policy.maxMembers

    fun createPendingMember(
        group: SmallGroup,
        inviteePublicKey: String,
    ): SmallGroupMember =
        SmallGroupMember(
            groupId = group.groupId,
            memberPublicKey = inviteePublicKey,
            role = SmallGroupRole.MEMBER,
            state = SmallGroupMemberState.PENDING_APPROVAL,
        )

    fun inviteeRequiresPendingApproval(member: SmallGroupMember): Boolean =
        member.state == SmallGroupMemberState.PENDING_APPROVAL

    fun pendingMembershipRequestForInvitee(
        group: SmallGroup,
        inviteId: String,
        inviterPublicKey: String,
        inviteePublicKey: String,
        inviteeDisplayName: String?,
        nowEpochMillis: Long,
    ): PendingMembershipRequest =
        PendingMembershipRequest(
            requestId = "small-group-request-${UUID.randomUUID()}",
            realmId = group.realmId,
            inviteId = inviteId,
            inviterPublicKey = inviterPublicKey,
            inviteePublicKey = inviteePublicKey,
            inviteeDisplayName = inviteeDisplayName,
            createdAtEpochMillis = nowEpochMillis,
            state = PendingMembershipState.PENDING_REVIEW,
        )

    fun applyBacklogLimit(
        messages: List<SmallGroupMessagePlaceholder>,
        policy: SmallGroupPolicy,
    ): List<SmallGroupMessagePlaceholder> =
        messages.sortedBy { it.createdAtEpochMillis }.takeLast(policy.maxBacklog)
}
