package com.disser.kraken.group

import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.CapacityState
import com.disser.kraken.realm.LocalRealmState
import com.disser.kraken.realm.PendingMembershipState
import com.disser.kraken.realm.Realm
import com.disser.kraken.realm.RealmPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmallGroupServiceTest {
    @Test
    fun groupCannotExceedMaxMembers() {
        val group = smallGroup(maxMembers = 2)
        val members = listOf(
            member("one"),
            member("two"),
        )

        assertFalse(SmallGroupService.canAddMember(group, members))
        assertTrue(SmallGroupService.canAddMember(group, members.take(1)))
    }

    @Test
    fun ttlAndBacklogPolicyExists() {
        val group = smallGroup(maxMembers = 10)

        assertEquals(24, group.policy.ttlHours)
        assertEquals(30, group.policy.maxBacklog)
        assertEquals(10, group.policy.slowModeSeconds)
    }

    @Test
    fun inviteeRequiresPendingApproval() {
        val pending = SmallGroupService.createPendingMember(
            group = smallGroup(maxMembers = 10),
            inviteePublicKey = "invitee-key",
        )

        assertEquals(SmallGroupMemberState.PENDING_APPROVAL, pending.state)
        assertTrue(SmallGroupService.inviteeRequiresPendingApproval(pending))
    }

    @Test
    fun pendingMembershipRequestUsesRealmAndPendingReview() {
        val request = SmallGroupService.pendingMembershipRequestForInvitee(
            group = smallGroup(maxMembers = 10),
            inviteId = "invite-1",
            inviterPublicKey = "owner-key",
            inviteePublicKey = "invitee-key",
            inviteeDisplayName = "Invitee",
            nowEpochMillis = 10,
        )

        assertEquals("realm-1", request.realmId)
        assertEquals(PendingMembershipState.PENDING_REVIEW, request.state)
    }

    @Test
    fun backlogPolicyLimitsMessages() {
        val messages = (1..5).map {
            SmallGroupMessagePlaceholder(
                messageId = "message-$it",
                groupId = "group-1",
                senderPublicKey = "sender",
                body = "body",
                createdAtEpochMillis = it.toLong(),
            )
        }

        val limited = SmallGroupService.applyBacklogLimit(messages, SmallGroupPolicy(maxBacklog = 2))

        assertEquals(listOf("message-4", "message-5"), limited.map { it.messageId })
    }

    @Test
    fun groupModelsHaveNoPublicDiscoveryFields() {
        val fields = SmallGroup::class.java.declaredFields.map { it.name.lowercase() }.toSet()

        assertFalse(fields.any { it.contains("public") || it.contains("discovery") || it.contains("search") })
    }

    @Test
    fun demoGroupUsesRealmSmallGroupLimit() {
        val creation = SmallGroupService.createDemoGroup(realm(), owner())

        assertEquals("realm-1", creation.group.realmId)
        assertEquals(7, creation.group.policy.maxMembers)
        assertEquals(SmallGroupRole.OWNER, creation.ownerMembership.role)
    }

    private fun smallGroup(maxMembers: Int): SmallGroup =
        SmallGroup(
            groupId = "group-1",
            realmId = "realm-1",
            name = "Small group",
            description = null,
            policy = SmallGroupPolicy(maxMembers = maxMembers),
        )

    private fun member(publicKey: String): SmallGroupMember =
        SmallGroupMember(
            groupId = "group-1",
            memberPublicKey = publicKey,
            role = SmallGroupRole.MEMBER,
            state = SmallGroupMemberState.ACTIVE,
        )

    private fun owner(): LocalIdentity =
        LocalIdentity(
            identityId = "owner",
            displayName = "Owner",
            publicKeyEncoded = "owner-public-key",
            privateKeyReference = "owner-private-ref",
            fingerprint = FingerprintFormatter.shortFingerprint("owner-public-key"),
            createdAtEpochMillis = 1,
        )

    private fun realm(): Realm =
        Realm(
            realmId = "realm-1",
            name = "Realm",
            description = null,
            createdByPublicKey = "owner-public-key",
            createdAtEpochMillis = 1,
            policy = RealmPolicy(smallGroupMaxMembers = 7),
            capacityState = CapacityState(memberCount = 1, capacity = 500, epoch = 1),
            localState = LocalRealmState.ACTIVE,
        )
}
