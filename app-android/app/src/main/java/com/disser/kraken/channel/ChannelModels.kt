package com.disser.kraken.channel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    @SerialName("channel_id")
    val channelId: String,
    @SerialName("realm_id")
    val realmId: String,
    val name: String,
    val description: String?,
    val policy: ChannelPolicy,
)

@Serializable
enum class ChannelRole {
    OWNER,
    PUBLISHER,
    SUBSCRIBER,
}

@Serializable
enum class ChannelSubscriberState {
    ACTIVE,
    MUTED,
    LEFT,
}

@Serializable
data class ChannelMembership(
    @SerialName("channel_id")
    val channelId: String,
    @SerialName("member_public_key")
    val memberPublicKey: String,
    val role: ChannelRole,
    val state: ChannelSubscriberState,
)

@Serializable
data class LatestNPolicy(
    @SerialName("max_messages")
    val maxMessages: Int = 20,
)

@Serializable
data class TtlPolicy(
    @SerialName("ttl_hours")
    val ttlHours: Int = 24,
)

@Serializable
data class ChannelPolicy(
    @SerialName("latest_n_policy")
    val latestNPolicy: LatestNPolicy = LatestNPolicy(),
    @SerialName("ttl_policy")
    val ttlPolicy: TtlPolicy = TtlPolicy(),
)

@Serializable
data class ChannelMessage(
    @SerialName("message_id")
    val messageId: String,
    @SerialName("channel_id")
    val channelId: String,
    @SerialName("publisher_public_key")
    val publisherPublicKey: String,
    val body: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

data class ChannelSnapshot(
    val channels: List<Channel>,
    val memberships: List<ChannelMembership>,
    val messages: List<ChannelMessage>,
)

data class DemoChannelCreation(
    val channel: Channel,
    val membership: ChannelMembership,
)
