package com.disser.kraken.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.handshake.HandshakePayloadCodec
import com.disser.kraken.handshake.OfflineHandshakeService
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.PendingInviteImport
import com.disser.kraken.invite.PendingInviteState
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipService
import com.disser.kraken.relationship.RelationshipState
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.ui.components.EmptyState
import com.disser.kraken.ui.components.KrakenCompactCard
import com.disser.kraken.ui.components.KrakenSectionHeader
import com.disser.kraken.ui.components.KrakenSecondaryAction
import com.disser.kraken.ui.components.KrakenStateBadge
import com.disser.kraken.ui.components.PayloadQrCodeCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.WarningCard
import com.disser.kraken.ui.components.nonActiveRelationshipReason
import com.disser.kraken.ui.icons.KrakenIcons

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    pendingInvites: List<PendingInviteImport>,
    relationships: List<Relationship>,
    realmSnapshot: RealmSnapshot,
    onRelationshipUpdated: (Relationship) -> Unit,
    onCancelPairing: (Relationship) -> Unit,
    onOpenContactProfile: (Relationship) -> Unit,
    onOpenChat: (Relationship) -> Unit,
) {
    val handshakeService = remember { OfflineHandshakeService() }
    val active = relationships.filter { it.state == RelationshipState.ACTIVE }
    val pending = relationships.filter {
        it.state == RelationshipState.PENDING_IMPORT || it.state == RelationshipState.PENDING_HANDSHAKE
    }
    val blocked = relationships.filter {
        it.state in setOf(
            RelationshipState.UNLINK_REQUESTED,
            RelationshipState.UNLINKED,
            RelationshipState.BLOCKED_BY_PEER,
            RelationshipState.REJOIN_REQUIRED,
        )
    }

    ScreenContainer("Контакты", navController, showBack = false) {
        KrakenCompactCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("QR-приглашение", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Локальное QR-приглашение для контакта.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KrakenSecondaryAction(
                        label = "Мой QR",
                        onClick = { navController.navigate(KrakenRoute.MyQr.route) },
                        icon = KrakenIcons.Invite,
                    )
                    KrakenSecondaryAction(
                        label = "Скан",
                        onClick = { navController.navigate(KrakenRoute.QrScanner.route) },
                        icon = KrakenIcons.Import,
                    )
                }
            }
        }
        if (relationships.isNotEmpty()) {
            if (active.isNotEmpty()) {
                KrakenSectionHeader("Чаты")
                active.forEach { relationship ->
                    RelationshipCard(
                        navController = navController,
                        relationship = relationship,
                        localIdentity = localIdentity,
                        realmSnapshot = realmSnapshot,
                        handshakeService = handshakeService,
                        onRelationshipUpdated = onRelationshipUpdated,
                        onCancelPairing = onCancelPairing,
                        onOpenContactProfile = onOpenContactProfile,
                        onOpenChat = onOpenChat,
                    )
                }
            }
            if (pending.isNotEmpty()) {
                KrakenSectionHeader("Ждут подтверждения")
                pending.forEach { relationship ->
                    RelationshipCard(
                        navController = navController,
                        relationship = relationship,
                        localIdentity = localIdentity,
                        realmSnapshot = realmSnapshot,
                        handshakeService = handshakeService,
                        onRelationshipUpdated = onRelationshipUpdated,
                        onCancelPairing = onCancelPairing,
                        onOpenContactProfile = onOpenContactProfile,
                        onOpenChat = onOpenChat,
                    )
                }
            }
            if (blocked.isNotEmpty()) {
                KrakenSectionHeader("Заблокированы / отвязаны")
                blocked.forEach { relationship ->
                    RelationshipCard(
                        navController = navController,
                        relationship = relationship,
                        localIdentity = localIdentity,
                        realmSnapshot = realmSnapshot,
                        handshakeService = handshakeService,
                        onRelationshipUpdated = onRelationshipUpdated,
                        onCancelPairing = onCancelPairing,
                        onOpenContactProfile = onOpenContactProfile,
                        onOpenChat = onOpenChat,
                    )
                }
            }
            val remaining = relationships - active.toSet() - pending.toSet() - blocked.toSet()
            if (remaining.isNotEmpty()) {
                KrakenSectionHeader("Другие записи")
                remaining.forEach { relationship ->
                    RelationshipCard(
                        navController = navController,
                        relationship = relationship,
                        localIdentity = localIdentity,
                        realmSnapshot = realmSnapshot,
                        handshakeService = handshakeService,
                        onRelationshipUpdated = onRelationshipUpdated,
                        onCancelPairing = onCancelPairing,
                        onOpenContactProfile = onOpenContactProfile,
                        onOpenChat = onOpenChat,
                    )
                }
            }
        } else if (pendingInvites.isNotEmpty()) {
            KrakenSectionHeader("Ждут подтверждения")
            pendingInvites.forEach { PendingImportCard(it) }
        } else {
            EmptyState(
                "Контактов пока нет",
                "Сканируйте QR, чтобы добавить локальный контакт.",
                actionLabel = "Скан QR",
                route = KrakenRoute.QrScanner,
                navController = navController,
            )
        }
        KrakenCompactCard {
            Text(
                "QR создаёт локальное доверие для контакта и запускает подтверждение связи.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationshipCard(
    navController: NavHostController,
    relationship: Relationship,
    localIdentity: LocalIdentity?,
    realmSnapshot: RealmSnapshot,
    handshakeService: OfflineHandshakeService,
    onRelationshipUpdated: (Relationship) -> Unit,
    onCancelPairing: (Relationship) -> Unit,
    onOpenContactProfile: (Relationship) -> Unit,
    onOpenChat: (Relationship) -> Unit,
) {
    var expandedPayload by remember(relationship.relationshipId) {
        mutableStateOf<HandshakePayloadView?>(null)
    }
    var localMessage by remember(relationship.relationshipId) {
        mutableStateOf<String?>(null)
    }
    var cancelPairingRequested by remember(relationship.relationshipId) { mutableStateOf(false) }
    var showManualFallback by remember(relationship.relationshipId) { mutableStateOf(false) }
    val showActionRow = relationship.state != RelationshipState.ACTIVE
    KrakenCompactCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        imageVector = KrakenIcons.Contacts,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(7.dp)
                            .size(17.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        relationship.peerDisplayName ?: "Неизвестный контакт",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        shortRelationshipSubtitle(relationship),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    KrakenStateBadge(relationshipStateLabel(relationship.state))
                    if (relationship.state == RelationshipState.ACTIVE) {
                        TextButton(onClick = { onOpenChat(relationship) }) {
                            Text("Чат")
                        }
                    }
                    TextButton(onClick = { onOpenContactProfile(relationship) }) {
                        Text("Профиль")
                    }
                }
            }

            if (relationship.state == RelationshipState.PENDING_IMPORT || relationship.state == RelationshipState.PENDING_HANDSHAKE) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        pendingHandshakeProgressLabel(relationship.state),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "Дождитесь локального подтверждения. Если оно не придёт, используйте QR подтверждения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (showActionRow) {
                RelationshipActionRow(
                    navController = navController,
                    relationship = relationship,
                    localIdentity = localIdentity,
                    realmSnapshot = realmSnapshot,
                    handshakeService = handshakeService,
                    onRelationshipUpdated = onRelationshipUpdated,
                    onCancelPairingRequested = { cancelPairingRequested = true },
                    onOpenChat = onOpenChat,
                    showManualFallback = showManualFallback,
                    onShowManualFallback = { showManualFallback = true },
                    onPayloadReady = { payload ->
                        expandedPayload = payload
                        localMessage = null
                    },
                    onError = { error ->
                        expandedPayload = null
                        localMessage = error
                    }
                )
            }

            localMessage?.let {
                WarningCard("Действие недоступно", listOf(it))
            }

            expandedPayload?.let { payload ->
                PayloadQrCodeCard(
                    title = payload.title,
                    payloadJson = payload.payloadJson,
                    details = payload.details,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { expandedPayload = null }) {
                        Text("Скрыть QR")
                    }
                }
            }
    }

    if (cancelPairingRequested) {
        CancelPairingSheet(
            relationship = relationship,
            onDismiss = { cancelPairingRequested = false },
            onConfirm = {
                cancelPairingRequested = false
                onCancelPairing(relationship)
            },
        )
    }
}

@Composable
private fun PendingImportCard(pending: PendingInviteImport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(
                    imageVector = KrakenIcons.Invite,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(24.dp),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pending.inviterDisplayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(pending.inviterFingerprint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Ожидает подтверждение перед чатом.", style = MaterialTheme.typography.bodySmall)
            }
            KrakenStateBadge(pendingInviteStateLabel(pending.state))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelPairingSheet(
    relationship: Relationship,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Отменить сопряжение?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Сопряжение с ${relationship.peerDisplayName ?: "контактом"} будет удалено. Для новой попытки отсканируйте QR заново.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Отменить сопряжение")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Оставить")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationshipActionRow(
    navController: NavHostController,
    relationship: Relationship,
    localIdentity: LocalIdentity?,
    realmSnapshot: RealmSnapshot,
    handshakeService: OfflineHandshakeService,
    onRelationshipUpdated: (Relationship) -> Unit,
    onCancelPairingRequested: () -> Unit,
    onOpenChat: (Relationship) -> Unit,
    showManualFallback: Boolean,
    onShowManualFallback: () -> Unit,
    onPayloadReady: (HandshakePayloadView) -> Unit,
    onError: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (relationship.state == RelationshipState.ACTIVE) {
            TextButton(onClick = { onOpenChat(relationship) }) {
                Text("Открыть чат")
            }
        }
        if (relationship.state in setOf(RelationshipState.PENDING_IMPORT, RelationshipState.PENDING_HANDSHAKE) && !showManualFallback) {
            TextButton(onClick = onShowManualFallback) {
                Text("Не получилось через Bluetooth?")
            }
        }
        if (relationship.state == RelationshipState.PENDING_IMPORT && showManualFallback) {
            TextButton(
                onClick = {
                    val identity = localIdentity
                    if (identity == null) {
                        onError("Сначала создайте профиль Kraken.")
                    } else {
                        val handshaking = RelationshipService.startHandshake(relationship)
                        onRelationshipUpdated(handshaking)
                        handshakeService.generateResponsePayload(identity, handshaking)
                            .fold(
                                onSuccess = { payload ->
                                    onPayloadReady(
                                        HandshakePayloadView(
                                            title = "QR подтверждения",
                                            payloadJson = HandshakePayloadCodec.encodeResponse(payload),
                                            details = listOf(
                                                "Покажите этот QR устройству, которое создало приглашение.",
                                                "Этот QR завершает подтверждение контакта.",
                                                "После сканирования контакт продолжит сопряжение.",
                                            )
                                        )
                                    )
                                },
                                onFailure = { onError(it.message ?: "Не удалось создать QR подтверждения.") },
                            )
                    }
                }
            ) {
                Text("Завершить через QR")
            }
        }
        if (relationship.state in setOf(RelationshipState.PENDING_IMPORT, RelationshipState.PENDING_HANDSHAKE)) {
            OutlinedButton(onClick = onCancelPairingRequested) {
                Text("Отменить")
            }
        }
        if (relationship.state == RelationshipState.PENDING_HANDSHAKE && showManualFallback) {
            TextButton(
                onClick = {
                    val identity = localIdentity
                    if (identity == null) {
                        onError("Сначала создайте профиль Kraken.")
                    } else {
                        handshakeService.generateResponsePayload(identity, relationship)
                            .fold(
                                onSuccess = { payload ->
                                    onPayloadReady(
                                        HandshakePayloadView(
                                            title = "QR подтверждения",
                                            payloadJson = HandshakePayloadCodec.encodeResponse(payload),
                                            details = listOf(
                                                "Покажите этот QR устройству, которое создало приглашение.",
                                                "Это устройство пока ждёт завершение сопряжения.",
                                                "Этот QR завершает подтверждение контакта.",
                                            )
                                        )
                                    )
                                },
                                onFailure = { onError(it.message ?: "Не удалось создать QR подтверждения.") },
                            )
                    }
                }
            ) {
                Text("Показать QR")
            }
        }
        if (relationship.state == RelationshipState.PENDING_HANDSHAKE && showManualFallback) {
            OutlinedButton(onClick = { navController.navigate(KrakenRoute.QrScanner.route) }) {
                Text("Сканировать QR")
            }
        }
    }
}

private data class HandshakePayloadView(
    val title: String,
    val payloadJson: String,
    val details: List<String>,
)

private fun shortFingerprint(fingerprint: String): String =
    if (fingerprint.length <= 14) fingerprint else "${fingerprint.take(6)}...${fingerprint.takeLast(4)}"

private fun shortInviteId(inviteId: String): String =
    inviteId.removePrefix("invite-").take(8).ifBlank { inviteId.take(8) }

private fun relationshipStateLabel(state: RelationshipState): String =
    when (state) {
        RelationshipState.PENDING_IMPORT,
        RelationshipState.PENDING_HANDSHAKE -> "ОЖИДАЕТ"
        RelationshipState.ACTIVE -> "АКТИВЕН"
        RelationshipState.UNLINK_REQUESTED -> "ОТВЯЗКА"
        RelationshipState.UNLINKED -> "ЗАВЕРШЁН"
        RelationshipState.BLOCKED_BY_PEER -> "БЛОК"
        RelationshipState.REJOIN_REQUIRED -> "ПОВТОР"
    }

private fun pendingInviteStateLabel(state: PendingInviteState): String =
    when (state) {
        PendingInviteState.PENDING_IMPORT -> "ОЖИДАЕТ"
        PendingInviteState.PENDING_HANDSHAKE -> "РУКОПОЖАТИЕ"
    }

private fun shortRelationshipSubtitle(relationship: Relationship): String =
    when (relationship.state) {
        RelationshipState.ACTIVE -> "${shortFingerprint(relationship.peerFingerprint)} · локальный чат"
        RelationshipState.PENDING_IMPORT -> "${shortFingerprint(relationship.peerFingerprint)} · ждёт подтверждение"
        RelationshipState.PENDING_HANDSHAKE -> "${shortFingerprint(relationship.peerFingerprint)} · ждёт завершение"
        RelationshipState.UNLINK_REQUESTED -> "Запрошена отвязка."
        RelationshipState.UNLINKED -> "Локальный контакт завершён."
        RelationshipState.BLOCKED_BY_PEER -> "Контакт заблокирован или завершён."
        RelationshipState.REJOIN_REQUIRED -> "Нужно новое приглашение и рукопожатие."
    }

private fun pendingHandshakeProgressLabel(state: RelationshipState): String =
    when (state) {
        RelationshipState.PENDING_IMPORT -> "Подтверждаем локальный контакт..."
        RelationshipState.PENDING_HANDSHAKE -> "Завершаем локальное сопряжение..."
        else -> "Ждём локальное подтверждение..."
    }
