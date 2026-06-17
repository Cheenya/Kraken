package com.disser.kraken.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import com.disser.kraken.BuildConfig
import com.disser.kraken.identity.LocalIdentity
import com.disser.kraken.invite.InvitePayloadCodec
import com.disser.kraken.invite.IssuedInviteRecord
import com.disser.kraken.invite.OneTimeInvitePayload
import com.disser.kraken.navigation.KrakenRoute
import com.disser.kraken.realm.InviteEdge
import com.disser.kraken.realm.LocalRealmState
import com.disser.kraken.realm.MembershipCertificate
import com.disser.kraken.realm.PendingMembershipRequest
import com.disser.kraken.realm.PendingMembershipState
import com.disser.kraken.realm.Realm
import com.disser.kraken.realm.RealmLifecycleGrouper
import com.disser.kraken.realm.RealmManagementPolicy
import com.disser.kraken.realm.RealmManagementRole
import com.disser.kraken.realm.RealmService
import com.disser.kraken.realm.RealmSnapshot
import com.disser.kraken.ui.components.EmptyState
import com.disser.kraken.ui.components.InfoCard
import com.disser.kraken.ui.components.KrakenCompactCard
import com.disser.kraken.ui.components.KrakenListRow
import com.disser.kraken.ui.components.KrakenSectionHeader
import com.disser.kraken.ui.components.KrakenStateBadge
import com.disser.kraken.ui.components.LabeledValue
import com.disser.kraken.ui.components.MetricPill
import com.disser.kraken.ui.components.PayloadQrCodeCard
import com.disser.kraken.ui.components.ScreenContainer
import com.disser.kraken.ui.components.TechnicalDetailsDisclosure
import com.disser.kraken.ui.components.WarningCard
import com.disser.kraken.ui.icons.KrakenIcons
import com.disser.kraken.ui.theme.LocalKrakenThemeTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RealmsScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    realmSnapshot: RealmSnapshot,
    onCreateRealm: (String) -> Unit,
    onRealmUpdated: (Realm) -> Unit,
    onCreateDemoPendingRequest: (Realm) -> Unit,
    onManageRealm: (Realm) -> Unit,
) {
    val groups = RealmLifecycleGrouper.group(realmSnapshot.realms, realmSnapshot.pendingRequests)
    var newRealmName by remember { mutableStateOf("") }
    var showCreateRealm by remember { mutableStateOf(false) }

    ScreenContainer("Реалмы", navController, showTitle = true, showBack = false) {
        if (localIdentity == null) {
            EmptyState("Нужен профиль на этом устройстве", "Создайте профиль перед созданием реалма.")
        } else {
            if (realmSnapshot.realms.isEmpty()) {
                EmptyState("Локальных реалмов нет", "Вход только по приглашению. Публичного поиска нет.")
            }

            RealmSection(
                title = "Активные реалмы",
                emptyText = "Активных локальных реалмов нет.",
                realms = groups.active,
                realmSnapshot = realmSnapshot,
                localIdentity = localIdentity,
                onManageRealm = onManageRealm,
            )
            RealmSection(
                title = "Ожидают проверки",
                emptyText = "Нет реалмов с ожидающими заявками.",
                realms = groups.pendingReview,
                realmSnapshot = realmSnapshot,
                localIdentity = localIdentity,
                onManageRealm = onManageRealm,
            )
            RealmSection(
                title = "Покинутые / архив",
                emptyText = "Нет покинутых или архивных записей.",
                realms = groups.leftArchived,
                realmSnapshot = realmSnapshot,
                localIdentity = localIdentity,
                onManageRealm = onManageRealm,
            )
            KrakenSectionHeader("Новый реалм")
            if (showCreateRealm) {
                KrakenCompactCard {
                    OutlinedTextField(
                        value = newRealmName,
                        onValueChange = { newRealmName = it },
                        label = { Text("Название реалма") },
                        supportingText = { Text("Локальный реалм доступен только по QR-приглашениям.") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                onCreateRealm(newRealmName)
                                newRealmName = ""
                                showCreateRealm = false
                            },
                            enabled = newRealmName.isNotBlank(),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Создать")
                        }
                        OutlinedButton(
                            onClick = {
                                newRealmName = ""
                                showCreateRealm = false
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Отмена")
                        }
                    }
                }
            } else {
                KrakenListRow(
                    title = "Создать реалм",
                    subtitle = "Локальная группа по QR-приглашениям",
                    leadingIcon = KrakenIcons.Realms,
                    trailingText = "›",
                    onClick = { showCreateRealm = true },
                )
            }
        }
    }
}

@Composable
private fun RealmSection(
    title: String,
    emptyText: String,
    realms: List<Realm>,
    realmSnapshot: RealmSnapshot,
    localIdentity: LocalIdentity?,
    onManageRealm: (Realm) -> Unit,
) {
    KrakenSectionHeader(title)
    if (realms.isEmpty()) {
        Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    realms.forEach { realm ->
        RealmListCard(
            realm = realm,
            pendingRequests = realmSnapshot.pendingRequests,
            inviteEdges = realmSnapshot.inviteEdges,
            certificate = realmSnapshot.localCertificateFor(realm, localIdentity),
            localIdentity = localIdentity,
            onManageRealm = onManageRealm,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RealmListCard(
    realm: Realm,
    pendingRequests: List<PendingMembershipRequest>,
    inviteEdges: List<InviteEdge>,
    certificate: MembershipCertificate?,
    localIdentity: LocalIdentity?,
    onManageRealm: (Realm) -> Unit,
) {
    val realmPendingRequests = pendingRequests.count {
        it.realmId == realm.realmId && it.state == PendingMembershipState.PENDING_REVIEW
    }
    val capacityReached = !RealmService.canCreateInvite(realm.capacityState)
    val role = RealmManagementPolicy.roleFor(realm, certificate, localIdentity)

    KrakenListRow(
        title = realm.name,
        subtitle = buildString {
            append(roleLabel(role))
            append(" · ")
            append("${realm.capacityState.memberCount}/${realm.capacityState.capacity} участников")
            if (realmPendingRequests > 0) append(" · $realmPendingRequests заявок")
            if (capacityReached) append(" · лимит")
        },
        leadingText = realm.name.take(2),
        trailingText = "${realmStateLabel(realm.localState)} ›",
        onClick = { onManageRealm(realm) },
    )
}

@Composable
fun RealmManageScreen(
    navController: NavHostController,
    localIdentity: LocalIdentity?,
    realm: Realm?,
    realmSnapshot: RealmSnapshot,
    onRealmUpdated: (Realm) -> Unit,
    onCreateDemoPendingRequest: (Realm) -> Unit,
    onDeleteLocalRealmRecord: (Realm) -> Unit,
    onPromoteMember: (MembershipCertificate) -> Unit,
    onDemoteMember: (MembershipCertificate) -> Unit,
    onRestrictMember: (MembershipCertificate) -> Unit,
    onRestoreMember: (MembershipCertificate) -> Unit,
    onRemoveMember: (MembershipCertificate) -> Unit,
    onCreateRealmInvite: (Realm) -> OneTimeInvitePayload?,
    issuedInvites: List<IssuedInviteRecord>,
    onRevokeIssuedInvite: (String) -> Unit,
) {
    ScreenContainer("Детали реалма", navController) {
        if (realm == null) {
            EmptyState(
                "Реалм не выбран",
                "Откройте реалм из списка перед управлением.",
                actionLabel = "К реалмам",
                route = KrakenRoute.Realms,
                navController = navController,
            )
            return@ScreenContainer
        }

        val certificate = realmSnapshot.localCertificateFor(realm, localIdentity)
        val role = RealmManagementPolicy.roleFor(realm, certificate, localIdentity)
        val realmInviteEdges = realmSnapshot.inviteEdges.filter { it.realmId == realm.realmId }
        val realmIssuedInvites = issuedInvites.filter { it.realmId == realm.realmId }
        val realmPendingRequests = realmSnapshot.pendingRequests.filter { it.realmId == realm.realmId }
        val realmMembers = realmSnapshot.membershipCertificates.filter { it.realmId == realm.realmId }
        var realmInvitePayload by remember(realm.realmId, certificate?.membershipId) {
            mutableStateOf<OneTimeInvitePayload?>(null)
        }

        KrakenSectionHeader("Обзор")
        KrakenCompactCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(realm.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        realm.description ?: "Локальный реалм по приглашению.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                KrakenStateBadge(realmStateLabel(realm.localState))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPill("Моя роль", roleLabel(role), Modifier.weight(1f))
                MetricPill("Участники", "${realm.capacityState.memberCount}/${realm.capacityState.capacity}", Modifier.weight(1f))
            }
        }
        KrakenSectionHeader("Участники")
        RealmMemberRosterCard(
            realm = realm,
            actorRole = role,
            members = realmMembers,
            inviteEdges = realmInviteEdges,
            pendingRequests = realmPendingRequests,
            onPromoteMember = onPromoteMember,
            onDemoteMember = onDemoteMember,
            onRestrictMember = onRestrictMember,
            onRestoreMember = onRestoreMember,
            onRemoveMember = onRemoveMember,
        )

        KrakenSectionHeader("Приглашения")
        KrakenCompactCard {
            val nowEpochMillis = System.currentTimeMillis()
            val inviteStatusCounts = realmIssuedInvites.groupingBy { inviteStatus(it, nowEpochMillis) }.eachCount()
            Text("Вступление только по QR-приглашению. Каталога и публичного входа нет.")
            Text(
                "Активных: ${inviteStatusCounts["Активно"] ?: 0} · отозванных: ${inviteStatusCounts["Отозвано"] ?: 0} · истёкших: ${inviteStatusCounts["Истекло"] ?: 0} · использованных: ${inviteStatusCounts["Использовано"] ?: 0}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val canCreateRealmInvite =
                localIdentity != null &&
                    certificate != null &&
                    role in setOf(RealmManagementRole.OWNER, RealmManagementRole.ADMIN) &&
                    RealmService.canCreateInvite(realm.capacityState)
            if (canCreateRealmInvite) {
                OutlinedButton(
                    onClick = {
                        realmInvitePayload = onCreateRealmInvite(realm)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Создать QR-приглашение реалма")
                }
            } else {
                Text(
                    "Создание QR-приглашения доступно владельцу или администратору при свободных местах.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (realmIssuedInvites.isEmpty()) {
                Text(
                    "Выданных QR-приглашений пока нет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    realmIssuedInvites
                        .sortedByDescending { it.createdAtEpochMillis }
                        .forEach { invite ->
                            IssuedInviteRow(
                                invite = invite,
                                nowEpochMillis = nowEpochMillis,
                                canRevoke = role in setOf(RealmManagementRole.OWNER, RealmManagementRole.ADMIN),
                                onShowQr = { payload -> realmInvitePayload = payload },
                                onRevoke = onRevokeIssuedInvite,
                            )
                        }
                }
            }
        }
        realmInvitePayload?.let { payload ->
            RealmInviteQrDialog(
                realmName = realm.name,
                payload = payload,
                onDismiss = { realmInvitePayload = null },
            )
        }

        KrakenSectionHeader("Ожидающие заявки")
        KrakenCompactCard {
            val pendingReviewCount = realmPendingRequests.count { it.state == PendingMembershipState.PENDING_REVIEW }
            val approvedWithAccessCount = realmPendingRequests.count { request ->
                request.state == PendingMembershipState.APPROVED &&
                    realmMembers.any { certificate -> certificate.memberPublicKey == request.inviteePublicKey }
            }
            Text("$pendingReviewCount заявок требуют проверки.")
            Text(
                "Решения остаются локальными, пока другое устройство не получит ручной QR подтверждения.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (approvedWithAccessCount > 0) {
                Text(
                    "$approvedWithAccessCount одобренных заявок могут снова показать ручной QR подтверждения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (
                role in setOf(RealmManagementRole.OWNER, RealmManagementRole.ADMIN) &&
                (pendingReviewCount > 0 || approvedWithAccessCount > 0)
            ) {
                OutlinedButton(
                    onClick = { navController.navigate(KrakenRoute.PendingApprovals.route) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pendingReviewCount > 0) "Открыть заявки" else "Открыть ручной QR")
                }
            }
            }

        KrakenSectionHeader("Управление")
        TechnicalDetailsDisclosure("Локальные действия") {
            RealmActionPanel(
                realm = realm,
                role = role,
                onRealmUpdated = onRealmUpdated,
                onCreateDemoPendingRequest = onCreateDemoPendingRequest,
            )
        }

        KrakenSectionHeader("Модерация")
        KrakenCompactCard {
            Text("Политика модерации локальная и зависит от роли.")
            Text(
                "Серверного принудительного применения правил и публичного каталога нет.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (RealmManagementPolicy.canDeleteLocalRecord(realm)) {
            KrakenSectionHeader("Опасная зона")
            WarningCard(
                "Локальная очистка",
                listOf(
                    "Удаляет локальную запись реалма, сертификат, связи приглашений и заявки.",
                    "Не связывается с пирами и ничего не отзывает удалённо.",
                )
            )
            Button(
                onClick = {
                    onDeleteLocalRealmRecord(realm)
                    navController.navigate(KrakenRoute.Realms.route)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Удалить локальную запись")
            }
        }

        if (BuildConfig.DEBUG) {
            KrakenSectionHeader("Технические детали")
            TechnicalDetailsCard(
                realm = realm,
                certificate = certificate,
                inviteEdges = realmInviteEdges,
                pendingRequests = realmPendingRequests,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RealmMemberRosterCard(
    realm: Realm,
    actorRole: RealmManagementRole,
    members: List<MembershipCertificate>,
    inviteEdges: List<InviteEdge>,
    pendingRequests: List<PendingMembershipRequest>,
    onPromoteMember: (MembershipCertificate) -> Unit,
    onDemoteMember: (MembershipCertificate) -> Unit,
    onRestrictMember: (MembershipCertificate) -> Unit,
    onRestoreMember: (MembershipCertificate) -> Unit,
    onRemoveMember: (MembershipCertificate) -> Unit,
) {
    KrakenCompactCard {
        Text("Использовано ${members.size} из ${realm.capacityState.capacity} локальных мест.")
        Text(
            "Ограничения применяются локально. Для другого устройства потребуется отдельное подтверждение.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (members.isEmpty()) {
            Text("Локальных сертификатов участников нет.")
        }
        members.sortedWith(
            compareBy<MembershipCertificate> { RealmManagementPolicy.memberRoleFor(realm, it).ordinal }
                .thenBy { it.memberPublicKey }
        ).forEach { member ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val memberRole = RealmManagementPolicy.memberRoleFor(realm, member)
            val restricted = RealmManagementPolicy.isRestricted(member)
            val inviteEdge = inviteEdges.firstOrNull { it.membershipId == member.membershipId || it.inviteePublicKey == member.memberPublicKey }
            val request = inviteEdge?.let { edge ->
                pendingRequests.firstOrNull { it.inviteId == edge.inviteId && it.inviteePublicKey == member.memberPublicKey }
            } ?: pendingRequests.firstOrNull { it.inviteePublicKey == member.memberPublicKey }
            val displayName = request?.inviteeDisplayName?.takeIf { it.isNotBlank() }
            val inviteHint = inviteEdge?.inviteId?.removePrefix("invite-")?.take(8)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(displayName ?: "Участник ${shortMemberKey(member.memberPublicKey)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOfNotNull(
                                "Запись локальна",
                                inviteHint?.let { "QR $it" },
                                if (displayName != null) shortMemberKey(member.memberPublicKey) else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        KrakenStateBadge(roleLabel(memberRole))
                        if (restricted) KrakenStateBadge("ОГРАНИЧЕН")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (RealmManagementPolicy.canPromoteMember(actorRole, realm, member)) {
                        OutlinedButton(onClick = { onPromoteMember(member) }) { Text("Сделать админом") }
                    }
                    if (RealmManagementPolicy.canDemoteMember(actorRole, realm, member)) {
                        OutlinedButton(onClick = { onDemoteMember(member) }) { Text("Снять админа") }
                    }
                    if (restricted) {
                        if (RealmManagementPolicy.canRestrictMember(actorRole, realm, member)) {
                            OutlinedButton(onClick = { onRestoreMember(member) }) { Text("Вернуть права") }
                        }
                    } else if (RealmManagementPolicy.canRestrictMember(actorRole, realm, member)) {
                        OutlinedButton(onClick = { onRestrictMember(member) }) { Text("Ограничить переписку") }
                    }
                    if (RealmManagementPolicy.canRemoveMember(actorRole, realm, member)) {
                        OutlinedButton(onClick = { onRemoveMember(member) }) { Text("Удалить запись") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RealmActionPanel(
    realm: Realm,
    role: RealmManagementRole,
    onRealmUpdated: (Realm) -> Unit,
    onCreateDemoPendingRequest: (Realm) -> Unit,
) {
    val canPauseOrResume = RealmManagementPolicy.canPauseOrResume(role, realm)
    val canArchive = RealmManagementPolicy.canArchive(role, realm)
    val canLeave = RealmManagementPolicy.canLeave(role, realm)
    val canCreateApprovalRequest = RealmManagementPolicy.canCreateApprovalRequest(role, realm)
    var pendingAction by remember(realm.realmId) { mutableStateOf<RealmManagementAction?>(null) }

    if (!canPauseOrResume && !canArchive && !canLeave && !canCreateApprovalRequest) {
        Text("Для этого состояния реалма нет локальных действий.")
        return
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canPauseOrResume && realm.localState == LocalRealmState.ACTIVE) {
            OutlinedButton(onClick = { pendingAction = RealmManagementAction.PAUSE }) { Text("Пауза") }
        }
        if (canPauseOrResume && realm.localState == LocalRealmState.PAUSED) {
            OutlinedButton(onClick = { pendingAction = RealmManagementAction.RESUME }) { Text("Возобновить") }
        }
        if (canArchive) {
            OutlinedButton(onClick = { pendingAction = RealmManagementAction.ARCHIVE }) { Text("Архив") }
        }
        if (canLeave) {
            OutlinedButton(onClick = { pendingAction = RealmManagementAction.LEAVE }) { Text("Покинуть") }
        }
    }
    if (canCreateApprovalRequest && BuildConfig.DEBUG) {
        TechnicalDetailsDisclosure("Dev tools") {
            WarningCard(
                "Служебная проверка экрана заявок",
                listOf(
                    "Основной поток заявок идёт через QR: приглашение, ответ, одобрение, финальное подтверждение.",
                    "Эта кнопка не является пользовательским сценарием и нужна только для проверки интерфейса.",
                ),
            )
            OutlinedButton(
                onClick = { onCreateDemoPendingRequest(realm) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Добавить служебную заявку")
            }
        }
    }
    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(action.confirmTitle) },
            text = { Text(action.confirmText) },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedRealm = when (action) {
                            RealmManagementAction.PAUSE -> RealmService.pause(realm)
                            RealmManagementAction.RESUME -> RealmService.resume(realm)
                            RealmManagementAction.ARCHIVE -> RealmService.archive(realm)
                            RealmManagementAction.LEAVE -> RealmService.leave(realm)
                        }
                        onRealmUpdated(updatedRealm)
                        pendingAction = null
                    },
                    colors = if (action.destructive) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Text(action.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

private enum class RealmManagementAction(
    val confirmTitle: String,
    val confirmText: String,
    val confirmLabel: String,
    val destructive: Boolean,
) {
    PAUSE(
        confirmTitle = "Поставить реалм на паузу?",
        confirmText = "Локальная запись перестанет считаться активной, пока вы не возобновите реалм.",
        confirmLabel = "Пауза",
        destructive = false,
    ),
    RESUME(
        confirmTitle = "Возобновить реалм?",
        confirmText = "Реалм снова будет считаться активным на этом устройстве.",
        confirmLabel = "Возобновить",
        destructive = false,
    ),
    ARCHIVE(
        confirmTitle = "Архивировать реалм?",
        confirmText = "Реалм уйдёт в архив локально. Перед дальнейшим использованием потребуется отдельное управление записью.",
        confirmLabel = "В архив",
        destructive = true,
    ),
    LEAVE(
        confirmTitle = "Покинуть реалм?",
        confirmText = "Локальная запись будет помечена как покинутая. Для повторного входа понадобится новый QR-flow.",
        confirmLabel = "Покинуть",
        destructive = true,
    ),
}

@Composable
private fun TechnicalDetailsCard(
    realm: Realm,
    certificate: MembershipCertificate?,
    inviteEdges: List<InviteEdge>,
    pendingRequests: List<PendingMembershipRequest>,
) {
    TechnicalDetailsDisclosure {
        LabeledValue("ID реалма", realm.realmId)
        LabeledValue("Сертификат участия", certificate?.membershipId ?: "нет")
        LabeledValue("Выдано ключом", certificate?.issuedByPublicKey ?: "нет данных")
        LabeledValue("Связи приглашений", inviteEdges.size.toString())
        LabeledValue("Ожидающие заявки", pendingRequests.size.toString())
        LabeledValue("Эпоха вместимости", realm.capacityState.epoch.toString())
        LabeledValue("Срок действия", "${realm.policy.maxTtlHours} ч")
        LabeledValue("Лимит копий", realm.policy.maxCopyBudget.toString())
        LabeledValue("Макс. малая группа", realm.policy.smallGroupMaxMembers.toString())
        LabeledValue("Транзит", if (realm.policy.allowTransit) "разрешён" else "выключен")
        LabeledValue("Курьерский скоринг", if (realm.policy.allowCourierScore) "разрешён" else "выключен")
        LabeledValue("Отчёты о прочтении", if (realm.policy.readReceiptsDefault) "включены" else "выключены")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "Технические детали являются локальными данными прототипа. Управление реалмом не расшифровывает сообщения.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IssuedInviteRow(
    invite: IssuedInviteRecord,
    nowEpochMillis: Long,
    canRevoke: Boolean,
    onShowQr: (OneTimeInvitePayload) -> Unit,
    onRevoke: (String) -> Unit,
) {
    val status = inviteStatus(invite, nowEpochMillis)
    val active = status == "Активно"
    var confirmRevoke by remember(invite.inviteId) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Приглашение ${invite.inviteId.removePrefix("invite-").take(8)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        KrakenStateBadge(status)
                    }
                    Text(
                        inviteExpiryLabel(invite, nowEpochMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                invite.payload?.let { payload ->
                    if (active) {
                        OutlinedButton(onClick = { onShowQr(payload) }) {
                            Text("Показать QR")
                        }
                    }
                }
            }
            if (active && canRevoke) {
                TechnicalDetailsDisclosure("Отзыв приглашения") {
                    Text(
                        "Отзыв нельзя отменить. Второй телефон больше не сможет использовать этот QR.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { confirmRevoke = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Отозвать приглашение")
                    }
                }
            }
        }
    }
    if (confirmRevoke) {
        AlertDialog(
            onDismissRequest = { confirmRevoke = false },
            title = { Text("Отозвать приглашение?") },
            text = {
                Text("QR ${invite.inviteId.removePrefix("invite-").take(8)} станет недействительным локально. Действие нельзя отменить.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRevoke = false
                        onRevoke(invite.inviteId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Отозвать")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevoke = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun RealmInviteQrDialog(
    realmName: String,
    payload: OneTimeInvitePayload,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(8.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "QR-приглашение",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Закрыть")
                    }
                }
                PayloadQrCodeCard(
                    title = "QR-приглашение для реалма",
                    payloadJson = InvitePayloadCodec.encode(payload),
                    details = listOf(
                        "Реалм: ${payload.realmName ?: realmName}",
                        "Приглашение: ${payload.inviteId.removePrefix("invite-").take(8)}",
                        "Приглашение учтено локально для проверки ответа.",
                        "После скана появится заявка на проверку.",
                        "После принятия ответного QR приглашение считается использованным.",
                        "Технические данные скрыты в деталях.",
                    ),
                )
            }
        }
    }
}

private fun inviteStatus(invite: IssuedInviteRecord, nowEpochMillis: Long): String =
    when {
        invite.revoked -> "Отозвано"
        invite.consumed -> "Использовано"
        invite.expiresAtEpochMillis != null && nowEpochMillis >= invite.expiresAtEpochMillis -> "Истекло"
        else -> "Активно"
    }

private fun inviteExpiryLabel(invite: IssuedInviteRecord, nowEpochMillis: Long): String =
    when {
        invite.revoked -> "Отозвано. QR больше не действует."
        invite.consumed -> "Использовано. Повторный вход по этому QR закрыт."
        else -> invite.expiresAtEpochMillis?.let { expiresAt ->
            val remainingMillis = expiresAt - nowEpochMillis
            if (remainingMillis <= 0) {
                "Срок действия истёк"
            } else {
                val remainingMinutes = (remainingMillis / 60_000).coerceAtLeast(1)
                val hours = remainingMinutes / 60
                val minutes = remainingMinutes % 60
                if (hours > 0) "Истекает через ${hours}ч ${minutes}м" else "Истекает через ${minutes}м"
            }
        } ?: "Без срока истечения"
    }

private fun realmStateLabel(state: LocalRealmState): String =
    when (state) {
        LocalRealmState.ACTIVE -> "Активен"
        LocalRealmState.ONLY_DIRECT -> "Напрямую"
        LocalRealmState.PAUSED -> "Пауза"
        LocalRealmState.ARCHIVED -> "Архив"
        LocalRealmState.LEFT -> "Покинут"
    }

private fun roleLabel(role: RealmManagementRole): String =
    when (role) {
        RealmManagementRole.OWNER -> "Владелец"
        RealmManagementRole.ADMIN -> "Админ"
        RealmManagementRole.MEMBER -> "Участник"
        RealmManagementRole.OBSERVER -> "Просмотр"
    }

private fun RealmSnapshot.localCertificateFor(
    realm: Realm,
    localIdentity: LocalIdentity?,
): MembershipCertificate? =
    membershipCertificates.firstOrNull {
        it.realmId == realm.realmId && localIdentity != null && it.memberPublicKey == localIdentity.publicKeyEncoded
    }

private fun shortMemberKey(publicKey: String): String {
    val clean = publicKey.removePrefix("placeholder-pub:")
    return if (clean.length <= 12) clean else "${clean.take(6)}...${clean.takeLast(4)}"
}
