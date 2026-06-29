package com.disser.kraken.channel

import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.Realm
import java.util.UUID

object ChannelService {
    fun createDemoChannel(
        realm: Realm,
        owner: LocalIdentity,
    ): DemoChannelCreation {
        val channel = Channel(
            channelId = "channel-${UUID.randomUUID()}",
            realmId = realm.realmId,
            name = "${realm.name}: объявления",
            description = "Локальный демо-канал внутри реалма по приглашению.",
            policy = ChannelPolicy(),
        )
        return DemoChannelCreation(
            channel = channel,
            membership = ChannelMembership(
                channelId = channel.channelId,
                memberPublicKey = owner.publicKeyEncoded,
                role = ChannelRole.PUBLISHER,
                state = ChannelSubscriberState.ACTIVE,
            ),
        )
    }

    fun canPublish(role: ChannelRole): Boolean =
        role == ChannelRole.OWNER || role == ChannelRole.PUBLISHER

    fun applyLatestN(
        messages: List<ChannelMessage>,
        policy: LatestNPolicy,
    ): List<ChannelMessage> =
        messages.sortedBy { it.createdAtEpochMillis }.takeLast(policy.maxMessages)

    fun mute(membership: ChannelMembership): ChannelMembership =
        if (membership.state == ChannelSubscriberState.ACTIVE) {
            membership.copy(state = ChannelSubscriberState.MUTED)
        } else {
            membership
        }

    fun leave(membership: ChannelMembership): ChannelMembership =
        membership.copy(state = ChannelSubscriberState.LEFT)
}
