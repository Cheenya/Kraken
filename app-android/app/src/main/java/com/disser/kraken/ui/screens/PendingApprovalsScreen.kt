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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.disser.kraken.handshake.HandshakePayloadCodec
import com.disser.kraken.handshake.OfflineHandshakeService
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.realm.ApprovalDecision
import com.disser.kraken.realm.ApprovalDecisionType
import com.disser.kraken.realm.ApprovalEvaluator
import com.disser.kraken.realm.ApprovalOutcome
import com.disser.kraken.realm.ApprovalRequestGrouper
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.realm.PendingMembershipRequest
import com.disser.kraken.realm.PendingMembershipState
import com.disser.kraken.realm.Realm
import com.disser.kraken.realm.RealmManagementPolicy
import com.disser.kraken.realm.RealmRole
import com.disser.kraken.relationship.ComplaintCategory
import com.disser.kraken.relationship.ComplaintEvent
import com.disser.kraken.relationship.ComplaintModerator
import com.disser.kraken.relationship.ModerationAction
import com.disser.kraken.relationship.OfflineHandshakeRole
import com.disser.kraken.relationship.Relationship
import com.disser.kraken.relationship.RelationshipState
import com.disser.kraken.ui.components.EmptyState
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.KrakenSectionHeader
import com.disser.kraken.ui.components.PayloadQrCodeCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.StateBadge
import com.disser.kraken.ui.components.TechnicalDetailsDisclosure
import com.disser.kraken.ui.components.WarningCard

@Composable
fun PendingApprovalsScreen(
    navController: NavHostController,
    pendingRequests: List<PendingMembershipRequest>,
    realms: List<Realm>,
    certificates: List<MembershipCertificate>,
    relationships: List<Relationship>,
    complaints: List<ComplaintEvent>,
    localIdentity: LocalIdentity?,
    onApprovalOutcome: (ApprovalOutcome) -> Unit,
) {
    var lastOutcome by remember { mutableStateOf<ApprovalOutcome?>(null) }
    val handshakeService = remember { OfflineHandshakeService() }
    val groups = ApprovalRequestGrouper.group(pendingRequests)
    ScreenContainer("Заявки реалмов", navController) {
        val approvedRequestsNeedingFinalQr = groups.history.filter {
            it.state == PendingMembershipState.APPROVED &&
                certificates.any { certificate ->
                    certificate.realmId == it.realmId && certificate.memberPublicKey == it.inviteePublicKey
                }
        }
        val finalQrOutcomes = buildList {
            lastOutcome?.takeIf {
                it.request.state == PendingMembershipState.APPROVED && it.membershipCertificate != null
            }?.let(::add)
            approvedRequestsNeedingFinalQr.forEach { request ->
                if (none { it.request.requestId == request.requestId }) {
                    add(
                        ApprovalOutcome(
                            request = request,
                            membershipCertificate = certificates.firstOrNull {
                                it.realmId == request.realmId && it.memberPublicKey == request.inviteePublicKey
                            },
                        )
                    )
                }
            }
        }
        finalQrOutcomes.forEach { outcome ->
            ApprovedRequestFinalQr(
                outcome = outcome,
                relationships = relationships,
                realms = realms,
                localIdentity = localIdentity,
                handshakeService = handshakeService,
                onOpenContacts = { navController.navigate(KrakenRoute.Contacts.route) },
            )
        }
        KrakenSectionHeader("На ревью")
        if (groups.pendingReview.isEmpty()) {
            EmptyState("Заявок пока нет", "Заявки появятся после скана ответного QR от вступающего устройства.")
        } else {
            groups.pendingReview.forEach { request ->
                PendingRequestCard(
                    request = request,
                    realm = realms.firstOrNull { it.realmId == request.realmId },
                    certificate = certificates.firstOrNull { it.realmId == request.realmId && it.memberPublicKey == localIdentity?.publicKeyEncoded },
                    localIdentity = localIdentity,
                    onApprovalOutcome = { outcome ->
                        lastOutcome = outcome
                        onApprovalOutcome(outcome)
                    },
                )
            }
        }
        if (groups.history.isNotEmpty()) {
            KrakenSectionHeader("История решений")
            groups.history
                .sortedByDescending { it.createdAtEpochMillis }
                .forEach { request ->
                    PendingRequestCard(
                        request = request,
                        realm = realms.firstOrNull { it.realmId == request.realmId },
                        certificate = certificates.firstOrNull { it.realmId == request.realmId && it.memberPublicKey == localIdentity?.publicKeyEncoded },
                        localIdentity = localIdentity,
                        onApprovalOutcome = { outcome ->
                            lastOutcome = outcome
                            onApprovalOutcome(outcome)
                        },
                    )
                }
        }
        InfoCard(
            "Права ожидающего участника",
            listOf(
                "До одобрения участник не может писать первым, публиковать, приглашать других или получать backlog.",
                "Одобрение создаёт локальное подтверждение доступа; его нужно передать финальным QR.",
            )
        )
        val aggregates = ComplaintModerator.aggregate(complaints)
        if (aggregates.isEmpty()) {
            EmptyState("Модерация пуста", "Жалобы после негативной отвязки будут собираться здесь для локального ревью.")
        } else {
            aggregates.forEach { aggregate ->
                val action = ComplaintModerator.recommendedAction(aggregate)
                InfoCard(
                    "Сводка жалоб",
                    listOf(
                        "Реалм: ${realms.firstOrNull { it.realmId == aggregate.realmId }?.name ?: aggregate.realmId.take(12)}",
                        "Участник: ${aggregate.targetPublicKey.take(24)}...",
                        "Причина: ${complaintCategoryLabel(aggregate.category)}",
                        "Жалоб: ${aggregate.complaintCount}",
                        "Рекомендация: ${moderationActionLabel(action)}",
                    )
                )
                TechnicalDetailsDisclosure("Технические данные жалоб") {
                    Text("ID реалма: ${aggregate.realmId}", style = MaterialTheme.typography.bodySmall)
                    Text("Ключ участника: ${aggregate.targetPublicKey}", style = MaterialTheme.typography.bodySmall)
                    Text("Категория: ${aggregate.category.name}", style = MaterialTheme.typography.bodySmall)
                    Text("Связей-источников: ${aggregate.sourceRelationshipIds.size}", style = MaterialTheme.typography.bodySmall)
                    Text("Локальное действие: ${action.name}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ApprovedRequestFinalQr(
    outcome: ApprovalOutcome,
    relationships: List<Relationship>,
    realms: List<Realm>,
    localIdentity: LocalIdentity?,
    handshakeService: OfflineHandshakeService,
    onOpenContacts: () -> Unit,
) {
    val certificate = outcome.membershipCertificate
    val relationship = relationships.firstOrNull {
        it.state == RelationshipState.ACTIVE &&
            it.offlineHandshakeRole == OfflineHandshakeRole.INVITER &&
            it.realmId == outcome.request.realmId &&
            it.sourceInviteId == outcome.request.inviteId &&
            it.peerPublicKey == outcome.request.inviteePublicKey
    }
    val realm = realms.firstOrNull { it.realmId == outcome.request.realmId }

    InfoCard(
        "Заявка одобрена",
        listOf(
            "Сертификат участника создан.",
            "Покажите ручной QR подтверждения вступающему устройству, чтобы оно стало участником реалма.",
        ),
    )

    if (certificate != null && relationship != null && localIdentity != null) {
        handshakeService.generateConfirmationPayload(
            localIdentity = localIdentity,
            relationship = relationship,
            realmName = realm?.name,
            membershipCertificate = certificate,
        ).fold(
            onSuccess = { payload ->
                PayloadQrCodeCard(
                    title = "Ручной QR подтверждения",
                    payloadJson = HandshakePayloadCodec.encodeConfirmation(payload),
                    details = listOf(
                        "Покажите этот QR вступающему устройству.",
                        "QR несёт локальное подтверждение доступа к реалму.",
                        "Подтверждение передаётся офлайн.",
                    ),
                )
            },
            onFailure = { error ->
                WarningCard("Ручной QR недоступен", listOf(error.message ?: "Не удалось создать ручной QR."))
            },
        )
    } else {
        WarningCard(
            "Ручной QR недоступен",
            listOf(
                "Не найден активный inviter-contact для одобренной заявки.",
                "Откройте Контакты и проверьте состояние offline QR handshake.",
            ),
        )
    }

    OutlinedButton(
        onClick = onOpenContacts,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Открыть контакты")
    }
}

@Composable
private fun PendingRequestCard(
    request: PendingMembershipRequest,
    realm: Realm?,
    certificate: MembershipCertificate?,
    localIdentity: LocalIdentity?,
    onApprovalOutcome: (ApprovalOutcome) -> Unit,
) {
    val approvalCount = ApprovalEvaluator.approvalCount(request)
    val managementRole = realm?.let { RealmManagementPolicy.roleFor(it, certificate, localIdentity) }
    val approvalRole = managementRole?.let(RealmManagementPolicy::approvalRoleFor)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(request.inviteeDisplayName ?: "Новый участник", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(realm?.name ?: request.realmId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StateBadge(pendingRequestStateLabel(request.state))
            }
            Text("Заявка на вступление через QR-приглашение.", style = MaterialTheme.typography.bodySmall)
            Text("Решения: $approvalCount/${request.approvalPolicy.requiredApprovals}")

            if (request.approvalPolicy.mode.name == "SINGLE_ADMIN") {
                WarningCard("Одобрение владельцем или админом", listOf("После одобрения нужно показать ручной QR подтверждения вступающему устройству."))
            }

            if (request.state == PendingMembershipState.PENDING_REVIEW && localIdentity != null && approvalRole != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onApprovalOutcome(
                                ApprovalEvaluator.recordDecision(
                                    request = request,
                                    decision = decision(localIdentity, approvalRole, ApprovalDecisionType.APPROVE),
                                    issuedByPublicKey = localIdentity.publicKeyEncoded,
                                )
                            )
                        },
                    ) {
                        Text("Одобрить и выдать доступ")
                    }
                    OutlinedButton(
                        onClick = {
                            onApprovalOutcome(
                                ApprovalEvaluator.recordDecision(
                                    request = request,
                                    decision = decision(localIdentity, approvalRole, ApprovalDecisionType.REJECT),
                                    issuedByPublicKey = localIdentity.publicKeyEncoded,
                                )
                            )
                        },
                    ) {
                        Text("Отклонить")
                    }
                }
            } else if (request.state == PendingMembershipState.PENDING_REVIEW) {
                Text(
                    "Одобрение и отклонение доступны только владельцу или администратору этого реалма.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TechnicalDetailsDisclosure("Технические данные заявки") {
                Text("Пригласил: ${request.inviterPublicKey.take(32)}...", style = MaterialTheme.typography.bodySmall)
                Text("Ключ участника: ${request.inviteePublicKey.take(32)}...", style = MaterialTheme.typography.bodySmall)
                Text("Состояние: ${request.state.name}", style = MaterialTheme.typography.bodySmall)
                Text("Политика: ${request.approvalPolicy.mode.name}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun pendingRequestStateLabel(state: PendingMembershipState): String =
    when (state) {
        PendingMembershipState.PENDING_REVIEW -> "ОЖИДАЕТ"
        PendingMembershipState.APPROVED -> "ОДОБРЕНО"
        PendingMembershipState.REJECTED -> "ОТКЛОНЕНО"
        PendingMembershipState.EXPIRED -> "ИСТЕКЛО"
        PendingMembershipState.CANCELLED -> "ОТМЕНЕНО"
    }

private fun complaintCategoryLabel(category: ComplaintCategory): String =
    when (category) {
        ComplaintCategory.UNWANTED_MESSAGES -> "нежелательные сообщения"
        ComplaintCategory.SPAM -> "спам"
        ComplaintCategory.THREAT_PRESSURE_OR_ETHICS_ABUSE -> "давление или угроза"
        ComplaintCategory.GOVERNANCE_ABUSE -> "злоупотребление управлением"
        ComplaintCategory.OTHER -> "другая причина"
    }

private fun moderationActionLabel(action: ModerationAction): String =
    when (action) {
        ModerationAction.NONE -> "действие не требуется"
        ModerationAction.WATCH -> "наблюдать локально"
        ModerationAction.RESTRICT_INVITES -> "ограничить приглашения"
        ModerationAction.RESTRICT_POSTING -> "ограничить сообщения"
        ModerationAction.REMOVE_FROM_REALM -> "удалить из реалма"
    }

private fun decision(
    localIdentity: LocalIdentity,
    role: RealmRole,
    type: ApprovalDecisionType,
): ApprovalDecision =
    ApprovalDecision(
        approverPublicKey = localIdentity.publicKeyEncoded,
        approverRole = role,
        decisionType = type,
        decidedAtEpochMillis = System.currentTimeMillis(),
    )
