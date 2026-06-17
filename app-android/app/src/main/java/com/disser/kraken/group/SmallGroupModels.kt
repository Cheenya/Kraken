package com.disser.kraken.group

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SmallGroup(
    @SerialName("group_id")
    val groupId: String,
    @SerialName("realm_id")
    val realmId: String,
    val name: String,
    val description: String?,
    val policy: SmallGroupPolicy = SmallGroupPolicy(),
)

@Serializable
enum class SmallGroupRole {
    OWNER,
    MODERATOR,
    MEMBER,
}

@Serializable
enum class SmallGroupMemberState {
    ACTIVE,
    PENDING_APPROVAL,
    LEFT,
}

@Serializable
data class SmallGroupMember(
    @SerialName("group_id")
    val groupId: String,
    @SerialName("member_public_key")
    val memberPublicKey: String,
    val role: SmallGroupRole,
    val state: SmallGroupMemberState,
)

@Serializable
data class SmallGroupPolicy(
    @SerialName("max_members")
    val maxMembers: Int = 10,
    @SerialName("max_backlog")
    val maxBacklog: Int = 30,
    @SerialName("ttl_hours")
    val ttlHours: Int = 24,
    @SerialName("slow_mode_seconds")
    val slowModeSeconds: Int = 10,
)

@Serializable
data class SmallGroupMessagePlaceholder(
    @SerialName("message_id")
    val messageId: String,
    @SerialName("group_id")
    val groupId: String,
    @SerialName("sender_public_key")
    val senderPublicKey: String,
    val body: String,
    @SerialName("created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

data class SmallGroupSnapshot(
    val groups: List<SmallGroup>,
    val members: List<SmallGroupMember>,
    val messages: List<SmallGroupMessagePlaceholder>,
)

data class DemoSmallGroupCreation(
    val group: SmallGroup,
    val ownerMembership: SmallGroupMember,
)
