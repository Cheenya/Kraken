package com.disser.kraken.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.channel.Channel
import com.disser.kraken.channel.ChannelMembership
import com.disser.kraken.channel.ChannelRole
import com.disser.kraken.channel.ChannelService
import com.disser.kraken.channel.ChannelSnapshot
import com.disser.kraken.channel.ChannelSubscriberState
import com.disser.kraken.group.SmallGroup
import com.disser.kraken.group.SmallGroupMember
import com.disser.kraken.group.SmallGroupMemberState
import com.disser.kraken.group.SmallGroupRole
import com.disser.kraken.group.SmallGroupService
import com.disser.kraken.group.SmallGroupSnapshot
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.ui.components.EmptyState
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.StateBadge

@Composable
fun ChannelsScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    realmSnapshot: RealmSnapshot,
    channelSnapshot: ChannelSnapshot,
    smallGroupSnapshot: SmallGroupSnapshot,
    onCreateDemoChannel: () -> Unit,
    onMembershipUpdated: (ChannelMembership) -> Unit,
    onCreateDemoSmallGroup: () -> Unit,
) {
    ScreenContainer("Каналы и группы", navController) {
        InfoCard(
            "Каналы и малые группы",
            listOf(
                "Каналы существуют только внутри реалмов по QR-приглашению.",
                "Личные чаты остаются основным сценарием; каналы нужны для общения внутри реалма.",
                "Малые группы ограничены политикой реалма и не становятся публичными.",
            )
        )
        if (localIdentity == null || realmSnapshot.realms.isEmpty()) {
            EmptyState("Нужен реалм", "Создайте профиль Kraken и реалм перед созданием каналов или малых групп.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onCreateDemoChannel, modifier = Modifier.weight(1f)) {
                    Text("Создать канал")
                }
                OutlinedButton(onClick = onCreateDemoSmallGroup, modifier = Modifier.weight(1f)) {
                    Text("Малая группа")
                }
            }
        }
        Text("Каналы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (channelSnapshot.channels.isEmpty()) {
            EmptyState("Каналов пока нет", "Создайте канал внутри реалма по QR-приглашению.")
        }
        channelSnapshot.channels.forEach { channel ->
            val membership = channelSnapshot.memberships.firstOrNull { it.channelId == channel.channelId }
            ChannelCard(channel, membership, onMembershipUpdated)
        }
        Text("Малые группы", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (smallGroupSnapshot.groups.isEmpty()) {
            EmptyState("Малых групп пока нет", "Малые группы ограничены политикой реалма и требуют одобрения приглашения.")
        }
        smallGroupSnapshot.groups.forEach { group ->
            val members = smallGroupSnapshot.members.filter { it.groupId == group.groupId }
            SmallGroupCard(group, members)
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    membership: ChannelMembership?,
    onMembershipUpdated: (ChannelMembership) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(channel.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(channel.description ?: channel.realmId, style = MaterialTheme.typography.bodySmall)
                }
                StateBadge(membership?.state?.let(::channelStateLabel) ?: "НЕТ ДОСТУПА")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateBadge("последние ${channel.policy.latestNPolicy.maxMessages}")
                StateBadge("${channel.policy.ttlPolicy.ttlHours} ч")
                StateBadge(membership?.role?.let(::channelRoleLabel) ?: "просмотр")
            }
            Text(if (membership?.role?.let(ChannelService::canPublish) == true) "Публикация доступна." else "Публикация недоступна для этой роли.")
            if (membership != null && membership.state != ChannelSubscriberState.LEFT) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onMembershipUpdated(ChannelService.mute(membership)) }) {
                        Text("Заглушить")
                    }
                    OutlinedButton(onClick = { onMembershipUpdated(ChannelService.leave(membership)) }) {
                        Text("Покинуть")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallGroupCard(
    group: SmallGroup,
    members: List<SmallGroupMember>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(group.description ?: group.realmId, style = MaterialTheme.typography.bodySmall)
                }
                StateBadge("${members.size}/${group.policy.maxMembers}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StateBadge("${group.policy.ttlHours} ч")
                StateBadge("история ${group.policy.maxBacklog}")
                StateBadge("пауза ${group.policy.slowModeSeconds} с")
            }
            Text(
                "Новые участники попадают на одобрение. Больших публичных групп и публичного поиска нет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (SmallGroupService.canAddMember(group, members)) {
                    "Можно пригласить ещё участника через QR."
                } else {
                    "Лимит группы достигнут."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun channelStateLabel(state: ChannelSubscriberState): String =
    when (state) {
        ChannelSubscriberState.ACTIVE -> "АКТИВЕН"
        ChannelSubscriberState.MUTED -> "ЗАГЛУШЕН"
        ChannelSubscriberState.LEFT -> "ПОКИНУТ"
    }

private fun channelRoleLabel(role: ChannelRole): String =
    when (role) {
        ChannelRole.OWNER -> "владелец"
        ChannelRole.PUBLISHER -> "публикатор"
        ChannelRole.SUBSCRIBER -> "подписчик"
    }

private fun smallGroupMemberStateLabel(state: SmallGroupMemberState): String =
    when (state) {
        SmallGroupMemberState.ACTIVE -> "активен"
        SmallGroupMemberState.PENDING_APPROVAL -> "ожидает"
        SmallGroupMemberState.LEFT -> "покинул"
    }

private fun smallGroupRoleLabel(role: SmallGroupRole): String =
    when (role) {
        SmallGroupRole.OWNER -> "владелец"
        SmallGroupRole.MODERATOR -> "модератор"
        SmallGroupRole.MEMBER -> "участник"
    }
