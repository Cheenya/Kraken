package com.disser.kraken.channel

import com.disser.kraken.identity.FingerprintFormatter
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.LocalRealmState
import com.disser.kraken.realm.Realm
import com.disser.kraken.realm.RealmPolicy
import com.disser.kraken.realm.CapacityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelServiceTest {
    @Test
    fun channelIsCreatedInsideRealm() {
        val creation = ChannelService.createDemoChannel(realm(), owner())

        assertEquals("realm-1", creation.channel.realmId)
        assertEquals(creation.channel.channelId, creation.membership.channelId)
    }

    @Test
    fun onlyPublisherOrOwnerCanPublish() {
        assertTrue(ChannelService.canPublish(ChannelRole.OWNER))
        assertTrue(ChannelService.canPublish(ChannelRole.PUBLISHER))
        assertFalse(ChannelService.canPublish(ChannelRole.SUBSCRIBER))
    }

    @Test
    fun latestNPolicyLimitsStoredMessages() {
        val messages = (1..5).map {
            ChannelMessage(
                messageId = "message-$it",
                channelId = "channel-1",
                publisherPublicKey = "publisher",
                body = "body",
                createdAtEpochMillis = it.toLong(),
            )
        }

        val limited = ChannelService.applyLatestN(messages, LatestNPolicy(maxMessages = 2))

        assertEquals(listOf("message-4", "message-5"), limited.map { it.messageId })
    }

    @Test
    fun muteAndLeaveChangeSubscriberState() {
        val membership = ChannelMembership(
            channelId = "channel-1",
            memberPublicKey = "member",
            role = ChannelRole.SUBSCRIBER,
            state = ChannelSubscriberState.ACTIVE,
        )

        assertEquals(ChannelSubscriberState.MUTED, ChannelService.mute(membership).state)
        assertEquals(ChannelSubscriberState.LEFT, ChannelService.leave(membership).state)
    }

    @Test
    fun channelModelsHaveNoPublicDiscoveryFields() {
        val fields = Channel::class.java.declaredFields.map { it.name.lowercase() }.toSet()

        assertFalse(fields.any { it.contains("public") || it.contains("discovery") || it.contains("search") })
    }

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
            policy = RealmPolicy(),
            capacityState = CapacityState(memberCount = 1, capacity = 500, epoch = 1),
            localState = LocalRealmState.ACTIVE,
        )
}
